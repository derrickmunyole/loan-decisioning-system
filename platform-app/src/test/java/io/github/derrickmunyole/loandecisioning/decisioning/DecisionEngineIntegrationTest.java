package io.github.derrickmunyole.loandecisioning.decisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.derrickmunyole.loandecisioning.infrastructure.audit.AuditEvent;
import io.github.derrickmunyole.loandecisioning.infrastructure.audit.AuditEventRepository;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginRequest;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginResponse;
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
 * Exercises Epic 3.4's done-criterion: all four decision outcomes reachable by tuning synthetic
 * input, against the real {@code credit-score-service} image (built fresh from its Dockerfile via
 * Testcontainers, not mocked) — this project's established "test against real infra" pattern,
 * same as every other integration test here, extended to the one dependency that happens to live
 * outside the JVM. Provider-outage handling gets its own test class ({@link
 * DecisionEngineProviderOutageIntegrationTest}) since it needs a deliberately unreachable base
 * URL instead of this real container.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DecisionEngineIntegrationTest {

    private static final String SEED_PASSWORD = "TestPassword123!";

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

    @Container
    static GenericContainer<?> creditScoreService =
            new GenericContainer<>(
                            new ImageFromDockerfile()
                                    // Explicit per-file context, not the whole directory: the
                                    // latter also picks up .venv/.pytest_cache/.ruff_cache if a
                                    // developer has ever run this component's own tests locally,
                                    // and ImageFromDockerfile doesn't honor .dockerignore the way
                                    // `docker build` does -- a stray .venv symlink pointing at an
                                    // absolute host path breaks the build-context tar entirely.
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
        registry.add(
                "app.credit-score.base-url",
                () ->
                        "http://"
                                + creditScoreService.getHost()
                                + ":"
                                + creditScoreService.getMappedPort(8000));
    }

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private DecisionRepository decisionRepository;
    @Autowired private AuditEventRepository auditEventRepository;

    @Test
    void excellentBandIsApproved() {
        publishStandardPolicyScorecardPricing();
        UUID applicationId =
                submitApplication("50000", 24, "200000", "EMPLOYED", "Acme Ltd");

        assertOutcome(applicationId, "APPROVED");
    }

    @Test
    void goodBandIsConditionalApproval() {
        publishStandardPolicyScorecardPricing();
        UUID applicationId =
                submitApplication("300000", 24, "80000", "EMPLOYED", "Acme Ltd");

        assertOutcome(applicationId, "CONDITIONAL_APPROVAL");
    }

    @Test
    void fairBandIsReferred() {
        publishStandardPolicyScorecardPricing();
        UUID applicationId =
                submitApplication("360000", 12, "100000", "EMPLOYED", "Acme Ltd");

        assertOutcome(applicationId, "REFERRED");
    }

    @Test
    void poorBandIsDeclined() {
        // Ratio 30% — comfortably below verification's own 40% affordability cutoff (so the
        // failed-verification gate doesn't fire first), but SELF_EMPLOYED's lower weight is
        // still enough to land this in the POOR band.
        publishStandardPolicyScorecardPricing();
        UUID applicationId =
                submitApplication("360000", 12, "100000", "SELF_EMPLOYED", "Acme Ltd");

        assertOutcome(applicationId, "DECLINED");
    }

    @Test
    void failedVerificationEvidenceIsReferredWithoutCallingTheScorer() {
        publishStandardPolicyScorecardPricing();
        UUID applicationId =
                submitApplication(
                        "100000", 12, "50000", "EMPLOYED", "SYNTHETIC_IDENTITY_MISMATCH");

        assertOutcome(applicationId, "REFERRED");

        Decision decision = decisionRepository.findByApplicationId(applicationId).get(0);
        assertThat(decision.getCreditScoreModelVersion()).isNull();
        assertThat(decision.getReasonCodesJson()).contains("failed check");
    }

    private void assertOutcome(UUID applicationId, String expectedOutcome) {
        String token = login("applicant", SEED_PASSWORD);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(
                        () -> assertThat(getStatus(token, applicationId)).isEqualTo(expectedOutcome));

        List<Decision> decisions = decisionRepository.findByApplicationId(applicationId);
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).getOutcome().name()).isEqualTo(expectedOutcome);

        List<AuditEvent> auditEvents =
                auditEventRepository.findByTargetTypeAndTargetIdOrderByOccurredAtAsc(
                        "Application", applicationId.toString());
        assertThat(auditEvents)
                .filteredOn(e -> e.getAction().equals("AUTOMATED_DECISION_RECORDED"))
                .hasSize(1)
                .allSatisfy(e -> assertThat(e.getActor()).isEqualTo("system_service"));
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
