package io.github.derrickmunyole.loandecisioning.origination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.derrickmunyole.loandecisioning.infrastructure.audit.AuditEventRepository;
import io.github.derrickmunyole.loandecisioning.infrastructure.outbox.OutboxEvent;
import io.github.derrickmunyole.loandecisioning.infrastructure.outbox.OutboxEventRepository;
import io.github.derrickmunyole.loandecisioning.infrastructure.outbox.OutboxEventStatus;
import io.github.derrickmunyole.loandecisioning.origination.application.ApplicationVersion;
import io.github.derrickmunyole.loandecisioning.origination.application.ApplicationVersionRepository;
import io.github.derrickmunyole.loandecisioning.origination.consent.ConsentRepository;
import io.github.derrickmunyole.loandecisioning.origination.document.DocumentRepository;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginRequest;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises Milestone 1, Epic 1.4's done-criterion end to end: draft → edit → submit produces an
 * immutable version + consent snapshot + {@code application.submitted} outbox event + audit
 * trail, and duplicate submit with the same Idempotency-Key returns the identical response
 * without a double-create.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationIntegrationTest {

    private static final String SEED_PASSWORD = "TestPassword123!";
    private static final String MINIO_USER = "test-minio-user";
    private static final String MINIO_PASSWORD = "test-minio-password";
    private static final String BUCKET = "loan-documents";

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

    @Container
    static MinIOContainer minio =
            new MinIOContainer("minio/minio:RELEASE.2025-09-07T16-13-09Z")
                    .withUserName(MINIO_USER)
                    .withPassword(MINIO_PASSWORD);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add(
                "app.security.jwt.secret",
                () -> "test-only-jwt-signing-secret-at-least-32-bytes-long");
        registry.add("app.security.seed-users-password", () -> SEED_PASSWORD);
        registry.add("app.outbox.relay-interval", () -> "200");
        registry.add("app.storage.minio.endpoint", minio::getS3URL);
        registry.add("app.storage.minio.access-key", () -> MINIO_USER);
        registry.add("app.storage.minio.secret-key", () -> MINIO_PASSWORD);
        registry.add("app.storage.minio.bucket", () -> BUCKET);
    }

    @BeforeAll
    static void createBucket() throws Exception {
        MinioClient client =
                MinioClient.builder()
                        .endpoint(minio.getS3URL())
                        .credentials(MINIO_USER, MINIO_PASSWORD)
                        .build();
        client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
    }

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private ApplicationVersionRepository applicationVersionRepository;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private DocumentRepository documentRepository;

    @Test
    void invalidRequestBodyReturnsBadRequestNotForbidden() {
        // JwtAuthenticationFilter skips ERROR dispatches by default, so without permitAll() on
        // /error, sendError()'s internal forward hits the security chain unauthenticated and gets
        // denied — masking the real 400 as a 403.
        String token = login("applicant", SEED_PASSWORD);
        Map<String, String> invalidBody = Map.of("fullName", "", "email", "not-an-email", "phone", "x");
        var headers = authHeaders(token);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());

        var response =
                restTemplate.exchange(
                        "/applications", HttpMethod.POST, new HttpEntity<>(invalidBody, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void draftToSubmitProducesVersionConsentOutboxAuditAndIsIdempotentUnderDuplicateSubmit() {
        String token = login("applicant", SEED_PASSWORD);

        UUID applicationId = createApplication(token, UUID.randomUUID().toString());
        patchDraft(token, applicationId);

        String submitKey = UUID.randomUUID().toString();
        Map<String, Object> firstSubmit = submit(token, applicationId, submitKey);
        // No verification adapter exists until Epic 2.3, so submit itself drives both hops
        // through WorkflowTransitionService: DRAFT -> SUBMITTED -> VERIFYING.
        assertThat(firstSubmit.get("status")).isEqualTo("VERIFYING");

        Map<String, Object> duplicateSubmit = submit(token, applicationId, submitKey);
        assertThat(duplicateSubmit).isEqualTo(firstSubmit);

        // A real (non-idempotent-replay) second submit attempt now hits
        // WorkflowTransitionService's guard directly, rather than Application's old
        // requireDraft() check — same 409, different guard, worth asserting explicitly.
        var headers = authHeaders(token);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        var secondRealSubmit =
                restTemplate.exchange(
                        "/applications/" + applicationId + "/submit",
                        HttpMethod.POST,
                        new HttpEntity<>(Map.of("consentAccepted", true), headers),
                        String.class);
        assertThat(secondRealSubmit.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        List<ApplicationVersion> versions =
                applicationVersionRepository.findByApplicationIdOrderByVersionNumberAsc(applicationId);
        assertThat(versions).hasSize(1);
        assertThat(consentRepository.findByApplicationVersionId(versions.get(0).getId())).hasSize(2);

        OutboxEvent outboxEvent =
                outboxEventRepository.findAll().stream()
                        .filter(e -> e.getAggregateId().equals(applicationId))
                        .findFirst()
                        .orElseThrow();
        assertThat(outboxEvent.getEventType()).isEqualTo("application.submitted");
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () ->
                                assertThat(
                                                outboxEventRepository
                                                        .findById(outboxEvent.getId())
                                                        .orElseThrow()
                                                        .getStatus())
                                        .isEqualTo(OutboxEventStatus.PUBLISHED));

        List<Map<String, Object>> timeline = getTimeline(token, applicationId);
        assertThat(timeline)
                .extracting(entry -> entry.get("action"))
                .contains("APPLICATION_CREATED", "APPLICATION_DRAFT_UPDATED", "APPLICATION_SUBMITTED");
        assertThat(
                        auditEventRepository.findAll().stream()
                                .anyMatch(
                                        e ->
                                                e.getTargetId().equals(applicationId.toString())
                                                        && e.getAction().equals("APPLICATION_SUBMITTED")))
                .isTrue();
    }

    @Test
    void documentUploadedDuringDraftIsStampedWithVersionAtSubmit() {
        String token = login("applicant", SEED_PASSWORD);
        UUID applicationId = createApplication(token, UUID.randomUUID().toString());
        patchDraft(token, applicationId);

        UUID documentId = uploadDocument(token, applicationId);
        assertThat(documentRepository.findById(documentId).orElseThrow().getApplicationVersionId())
                .isNull();

        submit(token, applicationId, UUID.randomUUID().toString());

        UUID versionId =
                applicationVersionRepository
                        .findByApplicationIdOrderByVersionNumberAsc(applicationId)
                        .get(0)
                        .getId();
        assertThat(documentRepository.findById(documentId).orElseThrow().getApplicationVersionId())
                .isEqualTo(versionId);
    }

    private UUID createApplication(String token, String idempotencyKey) {
        var headers = authHeaders(token);
        headers.set("Idempotency-Key", idempotencyKey);
        Map<String, String> body =
                Map.of(
                        "fullName", "Test Applicant",
                        "email", "applicant@example.com",
                        "phone", "+254700000000");
        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        "/applications",
                        HttpMethod.POST,
                        new HttpEntity<>(body, headers),
                        new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString((String) response.getBody().get("id"));
    }

    private void patchDraft(String token, UUID applicationId) {
        Map<String, Object> body =
                Map.of(
                        "requestedAmountKes", 100000,
                        "requestedTermMonths", 12,
                        "declaredMonthlyIncomeKes", 50000,
                        "declaredEmploymentStatus", "EMPLOYED");
        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        "/applications/" + applicationId,
                        HttpMethod.PATCH,
                        new HttpEntity<>(body, authHeaders(token)),
                        new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private Map<String, Object> submit(String token, UUID applicationId, String idempotencyKey) {
        var headers = authHeaders(token);
        headers.set("Idempotency-Key", idempotencyKey);
        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        "/applications/" + applicationId + "/submit",
                        HttpMethod.POST,
                        new HttpEntity<>(Map.of("consentAccepted", true), headers),
                        new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private List<Map<String, Object>> getTimeline(String token, UUID applicationId) {
        ResponseEntity<List<Map<String, Object>>> response =
                restTemplate.exchange(
                        "/applications/" + applicationId + "/timeline",
                        HttpMethod.GET,
                        new HttpEntity<>(authHeaders(token)),
                        new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private UUID uploadDocument(String token, UUID applicationId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("documentType", "ID_DOCUMENT");
        body.add(
                "file",
                new ByteArrayResource("synthetic id document bytes".getBytes()) {
                    @Override
                    public String getFilename() {
                        return "id.jpg";
                    }
                });
        var headers = authHeaders(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        "/applications/" + applicationId + "/documents",
                        HttpMethod.POST,
                        new HttpEntity<>(body, headers),
                        new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString((String) response.getBody().get("id"));
    }

    private String login(String username, String password) {
        var response =
                restTemplate.postForEntity(
                        "/auth/login", new LoginRequest(username, password), LoginResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().token();
    }

    private HttpHeaders authHeaders(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
