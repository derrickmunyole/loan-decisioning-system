package io.github.derrickmunyole.loandecisioning.offers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.derrickmunyole.loandecisioning.infrastructure.audit.AuditEvent;
import io.github.derrickmunyole.loandecisioning.infrastructure.audit.AuditEventRepository;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginRequest;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginResponse;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises Epic 5.1's roadmap done-criterion directly: duplicate accept calls produce one
 * acceptance, and the expiry sweep correctly ages out an untouched offer. Same real-infra pattern
 * as {@code DecisionEngineIntegrationTest} — a real {@code credit-score-service} container, not a
 * mock, since an automated APPROVED decision is the only way to reach an {@code Offer} through the
 * actual pipeline rather than inserting one directly.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OfferIntegrationTest {

    private static final String SEED_PASSWORD = "TestPassword123!";

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

    @Container
    static GenericContainer<?> creditScoreService =
            new GenericContainer<>(
                            new ImageFromDockerfile()
                                    .withFileFromPath(
                                            "Dockerfile", Paths.get("..", "credit-score-service", "Dockerfile"))
                                    .withFileFromPath(
                                            "pyproject.toml",
                                            Paths.get("..", "credit-score-service", "pyproject.toml"))
                                    .withFileFromPath(
                                            "uv.lock", Paths.get("..", "credit-score-service", "uv.lock"))
                                    .withFileFromPath("src", Paths.get("..", "credit-score-service", "src")))
                    .withExposedPorts(8000)
                    .waitingFor(Wait.forHttp("/docs").forStatusCode(200));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add(
                "app.security.jwt.secret",
                () -> "test-only-jwt-signing-secret-at-least-32-bytes-long");
        registry.add("app.security.seed-users-password", () -> SEED_PASSWORD);
        registry.add("app.outbox.relay-interval", () -> "200");
        // Shrunk from the real 14-day/60s defaults so the expiry test observes a real sweep in
        // real (test) time, rather than needing a fake/mutable Clock bean.
        registry.add("app.offers.expiry-period", () -> "PT2S");
        registry.add("app.offers.expiry-sweep-interval", () -> "500");
        registry.add(
                "app.credit-score.base-url",
                () ->
                        "http://"
                                + creditScoreService.getHost()
                                + ":"
                                + creditScoreService.getMappedPort(8000));
    }

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private OfferRepository offerRepository;
    @Autowired private AuditEventRepository auditEventRepository;

    @Test
    void approvedDecisionAutoCreatesAnOfferTheApplicantCanSee() {
        publishStandardPolicyScorecardPricing();
        UUID applicationId = submitApplication("50000", 24, "200000", "EMPLOYED", "Acme Ltd");

        String token = login("applicant", SEED_PASSWORD);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(getStatus(token, applicationId)).isEqualTo("OFFERED"));

        Map<String, Object> offer = getOffer(token, applicationId);
        assertThat(offer.get("status")).isEqualTo("OFFERED");
        assertThat(offer.get("applicationId")).isEqualTo(applicationId.toString());
        assertThat(new BigDecimal(offer.get("principalKes").toString())).isEqualByComparingTo("50000");
        assertThat(offer.get("aprBasisPoints")).isEqualTo(1499);
        assertThat(offer.get("termMonths")).isEqualTo(36);
    }

    @Test
    void duplicateAcceptCallsProduceExactlyOneAcceptance() {
        publishStandardPolicyScorecardPricing();
        UUID applicationId = submitApplication("50000", 24, "200000", "EMPLOYED", "Acme Ltd");
        String token = login("applicant", SEED_PASSWORD);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(getStatus(token, applicationId)).isEqualTo("OFFERED"));
        UUID offerId = UUID.fromString((String) getOffer(token, applicationId).get("id"));

        String idempotencyKey = UUID.randomUUID().toString();
        Map<String, Object> first = accept(token, offerId, idempotencyKey);
        Map<String, Object> second = accept(token, offerId, idempotencyKey);

        assertThat(first.get("status")).isEqualTo("ACCEPTED");
        assertThat(second).isEqualTo(first);
        assertThat(getStatus(token, applicationId)).isEqualTo("ACCEPTED");

        List<AuditEvent> acceptEvents =
                auditEventRepository.findByTargetTypeAndTargetIdOrderByOccurredAtAsc(
                        "Offer", offerId.toString());
        assertThat(acceptEvents).filteredOn(e -> e.getAction().equals("OFFER_ACCEPTED")).hasSize(1);
    }

    @Test
    void acceptingAfterAnAlreadyAcceptedOfferWithADifferentKeyIsRejected() {
        publishStandardPolicyScorecardPricing();
        UUID applicationId = submitApplication("50000", 24, "200000", "EMPLOYED", "Acme Ltd");
        String token = login("applicant", SEED_PASSWORD);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(getStatus(token, applicationId)).isEqualTo("OFFERED"));
        UUID offerId = UUID.fromString((String) getOffer(token, applicationId).get("id"));

        accept(token, offerId, UUID.randomUUID().toString());

        var headers = authHeaders(token);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<String> secondAttempt =
                restTemplate.exchange(
                        "/offers/" + offerId + "/accept", HttpMethod.POST, new HttpEntity<>(headers), String.class);
        assertThat(secondAttempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void theExpirySweepAgesOutAnUntouchedOfferAndBlocksALateAccept() {
        publishStandardPolicyScorecardPricing();
        UUID applicationId = submitApplication("50000", 24, "200000", "EMPLOYED", "Acme Ltd");
        String token = login("applicant", SEED_PASSWORD);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(getStatus(token, applicationId)).isEqualTo("OFFERED"));
        UUID offerId = UUID.fromString((String) getOffer(token, applicationId).get("id"));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () ->
                                assertThat(offerRepository.findById(offerId).orElseThrow().getStatus().name())
                                        .isEqualTo("OFFER_EXPIRED"));
        assertThat(getStatus(token, applicationId)).isEqualTo("OFFER_EXPIRED");

        List<AuditEvent> expiryEvents =
                auditEventRepository.findByTargetTypeAndTargetIdOrderByOccurredAtAsc(
                        "Offer", offerId.toString());
        assertThat(expiryEvents)
                .filteredOn(e -> e.getAction().equals("OFFER_EXPIRED"))
                .hasSize(1)
                .allSatisfy(e -> assertThat(e.getActor()).isEqualTo("system_service"));

        var headers = authHeaders(token);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<String> lateAccept =
                restTemplate.exchange(
                        "/offers/" + offerId + "/accept", HttpMethod.POST, new HttpEntity<>(headers), String.class);
        assertThat(lateAccept.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private Map<String, Object> accept(String token, UUID offerId, String idempotencyKey) {
        var headers = authHeaders(token);
        headers.set("Idempotency-Key", idempotencyKey);
        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        "/offers/" + offerId + "/accept",
                        HttpMethod.POST,
                        new HttpEntity<>(headers),
                        new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private Map<String, Object> getOffer(String token, UUID applicationId) {
        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        "/applications/" + applicationId + "/offer",
                        HttpMethod.GET,
                        new HttpEntity<>(authHeaders(token)),
                        new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private void publishStandardPolicyScorecardPricing() {
        String token = login("policy_admin", SEED_PASSWORD);

        Map<String, Object> bandOutcomes =
                Map.of(
                        "EXCELLENT", "APPROVED",
                        "VERY_GOOD", "APPROVED",
                        "GOOD", "CONDITIONAL_APPROVAL",
                        "FAIR", "REFERRED",
                        "POOR", "DECLINED");
        createAndPublish(
                token,
                "/policies",
                Map.of(
                        "effectiveDate", "2026-01-01",
                        "rules", Map.of("bandOutcomes", bandOutcomes)));

        Map<String, Object> bandCutoffs =
                Map.of(
                        "EXCELLENT", 800,
                        "VERY_GOOD", 740,
                        "GOOD", 670,
                        "FAIR", 580,
                        "POOR", 300);
        createAndPublish(token, "/scorecards", Map.of("formulaConfig", Map.of("bandCutoffs", bandCutoffs)));

        createAndPublish(
                token,
                "/pricing",
                Map.of(
                        "aprTermRules",
                        Map.of(
                                "tiers",
                                Map.of(
                                        "APPROVED", Map.of("aprBasisPoints", 1499, "termMonths", 36),
                                        "CONDITIONAL_APPROVAL", Map.of("aprBasisPoints", 1999, "termMonths", 24)))));
    }

    private void createAndPublish(String token, String path, Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> created =
                restTemplate.exchange(
                        path,
                        HttpMethod.POST,
                        new HttpEntity<>(body, authHeaders(token)),
                        new ParameterizedTypeReference<>() {});
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) created.getBody().get("id");

        ResponseEntity<Map<String, Object>> published =
                restTemplate.exchange(
                        path + "/" + id + "/publish",
                        HttpMethod.POST,
                        new HttpEntity<>(authHeaders(token)),
                        new ParameterizedTypeReference<>() {});
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private UUID submitApplication(
            String requestedAmountKes,
            int requestedTermMonths,
            String declaredMonthlyIncomeKes,
            String declaredEmploymentStatus,
            String declaredEmployerName) {
        String token = login("applicant", SEED_PASSWORD);

        var createHeaders = authHeaders(token);
        createHeaders.set("Idempotency-Key", UUID.randomUUID().toString());
        Map<String, String> createBody =
                Map.of(
                        "fullName", "Test Applicant",
                        "email", "applicant@example.com",
                        "phone", "+254700000000");
        ResponseEntity<Map<String, Object>> created =
                restTemplate.exchange(
                        "/applications",
                        HttpMethod.POST,
                        new HttpEntity<>(createBody, createHeaders),
                        new ParameterizedTypeReference<>() {});
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID applicationId = UUID.fromString((String) created.getBody().get("id"));

        Map<String, Object> patchBody =
                Map.of(
                        "requestedAmountKes", requestedAmountKes,
                        "requestedTermMonths", requestedTermMonths,
                        "declaredMonthlyIncomeKes", declaredMonthlyIncomeKes,
                        "declaredEmploymentStatus", declaredEmploymentStatus,
                        "declaredEmployerName", declaredEmployerName);
        ResponseEntity<Map<String, Object>> patched =
                restTemplate.exchange(
                        "/applications/" + applicationId,
                        HttpMethod.PATCH,
                        new HttpEntity<>(patchBody, authHeaders(token)),
                        new ParameterizedTypeReference<>() {});
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);

        var submitHeaders = authHeaders(token);
        submitHeaders.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<Map<String, Object>> submitted =
                restTemplate.exchange(
                        "/applications/" + applicationId + "/submit",
                        HttpMethod.POST,
                        new HttpEntity<>(Map.of("consentAccepted", true), submitHeaders),
                        new ParameterizedTypeReference<>() {});
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.OK);

        return applicationId;
    }

    private String getStatus(String token, UUID applicationId) {
        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        "/applications/" + applicationId,
                        HttpMethod.GET,
                        new HttpEntity<>(authHeaders(token)),
                        new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("status");
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
