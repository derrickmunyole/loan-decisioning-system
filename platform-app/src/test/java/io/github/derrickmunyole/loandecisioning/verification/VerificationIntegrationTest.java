package io.github.derrickmunyole.loandecisioning.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.derrickmunyole.loandecisioning.infrastructure.audit.AuditEvent;
import io.github.derrickmunyole.loandecisioning.infrastructure.audit.AuditEventRepository;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginRequest;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginResponse;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises Epic 2.3's done-criterion: submitted applications auto-progress to UNDERWRITING via
 * the real {@code application.submitted} consumer, plus the two synthetic input-shape signals —
 * an identity mismatch (business-outcome FAILED, application still proceeds) and a simulated
 * transient failure (technical retry, then success) — against the real RabbitMQ retry mechanics,
 * not a test-only shortcut.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VerificationIntegrationTest {

    private static final String SEED_PASSWORD = "TestPassword123!";

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add(
                "app.security.jwt.secret",
                () -> "test-only-jwt-signing-secret-at-least-32-bytes-long");
        registry.add("app.security.seed-users-password", () -> SEED_PASSWORD);
        registry.add("app.outbox.relay-interval", () -> "200");
    }

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private VerificationCaseRepository verificationCaseRepository;
    @Autowired private AuditEventRepository auditEventRepository;

    @Test
    void normalApplicationAutoProgressesToUnderwritingWithPassedChecks() {
        String token = login("applicant", SEED_PASSWORD);
        UUID applicationId = createApplication(token);
        patchDraft(token, applicationId, "Acme Ltd", 100000, 12, 50000);
        submit(token, applicationId);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(getStatus(token, applicationId)).isEqualTo("UNDERWRITING"));

        List<VerificationCase> cases = verificationCaseRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId);
        assertThat(cases).hasSize(2);
        assertThat(cases).allSatisfy(c -> assertThat(c.getStatus()).isEqualTo(VerificationStatus.PASSED));

        List<AuditEvent> auditEvents = applicationAuditEvents(applicationId);
        assertThat(auditEvents)
                .extracting(AuditEvent::getAction)
                .contains("APPLICATION_VERIFICATION_STARTED", "APPLICATION_VERIFICATION_COMPLETED");
        assertThat(auditEvents)
                .filteredOn(e -> e.getAction().startsWith("APPLICATION_VERIFICATION"))
                .allSatisfy(e -> assertThat(e.getActor()).isEqualTo("system_service"));
    }

    @Test
    void identityMismatchTriggerFailsIdentityCaseButStillReachesUnderwriting() {
        String token = login("applicant", SEED_PASSWORD);
        UUID applicationId = createApplication(token);
        patchDraft(token, applicationId, "SYNTHETIC_IDENTITY_MISMATCH", 100000, 12, 50000);
        submit(token, applicationId);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(getStatus(token, applicationId)).isEqualTo("UNDERWRITING"));

        List<VerificationCase> cases = verificationCaseRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId);
        assertThat(cases)
                .filteredOn(c -> c.getType() == VerificationType.IDENTITY)
                .allSatisfy(c -> assertThat(c.getStatus()).isEqualTo(VerificationStatus.FAILED));
        assertThat(cases)
                .filteredOn(c -> c.getType() == VerificationType.INCOME)
                .allSatisfy(c -> assertThat(c.getStatus()).isEqualTo(VerificationStatus.PASSED));
    }

    @Test
    void transientFailureTriggerRetriesThenSucceeds() {
        String token = login("applicant", SEED_PASSWORD);
        UUID applicationId = createApplication(token);
        patchDraft(token, applicationId, "SYNTHETIC_TRANSIENT_FAILURE", 100000, 12, 50000);
        submit(token, applicationId);

        // 1s backoff (application.yml) before the redelivered attempt succeeds.
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(getStatus(token, applicationId)).isEqualTo("UNDERWRITING"));

        List<VerificationCase> cases = verificationCaseRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId);
        assertThat(cases).hasSize(2);
        assertThat(cases).allSatisfy(c -> assertThat(c.getStatus()).isEqualTo(VerificationStatus.PASSED));

        // The first delivery throws SimulatedTransientVerificationFailureException after the
        // SUBMITTED->VERIFYING hop (and its audit write) already happened in that attempt's
        // transaction -- proving the audit row rolled back with the rest of the attempt is the
        // whole point of this assertion, not just that the retry eventually succeeds.
        List<AuditEvent> auditEvents = applicationAuditEvents(applicationId);
        assertThat(auditEvents)
                .filteredOn(e -> e.getAction().equals("APPLICATION_VERIFICATION_STARTED"))
                .hasSize(1);
        assertThat(auditEvents)
                .filteredOn(e -> e.getAction().equals("APPLICATION_VERIFICATION_COMPLETED"))
                .hasSize(1);
    }

    private List<AuditEvent> applicationAuditEvents(UUID applicationId) {
        return auditEventRepository.findByTargetTypeAndTargetIdOrderByOccurredAtAsc(
                "Application", applicationId.toString());
    }

    private UUID createApplication(String token) {
        var headers = authHeaders(token);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
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

    private void patchDraft(
            String token,
            UUID applicationId,
            String declaredEmployerName,
            int requestedAmountKes,
            int requestedTermMonths,
            int declaredMonthlyIncomeKes) {
        Map<String, Object> body =
                Map.of(
                        "requestedAmountKes", requestedAmountKes,
                        "requestedTermMonths", requestedTermMonths,
                        "declaredMonthlyIncomeKes", declaredMonthlyIncomeKes,
                        "declaredEmploymentStatus", "EMPLOYED",
                        "declaredEmployerName", declaredEmployerName);
        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        "/applications/" + applicationId,
                        HttpMethod.PATCH,
                        new HttpEntity<>(body, authHeaders(token)),
                        new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void submit(String token, UUID applicationId) {
        var headers = authHeaders(token);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        "/applications/" + applicationId + "/submit",
                        HttpMethod.POST,
                        new HttpEntity<>(Map.of("consentAccepted", true), headers),
                        new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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
