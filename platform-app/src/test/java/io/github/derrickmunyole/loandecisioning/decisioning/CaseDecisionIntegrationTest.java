package io.github.derrickmunyole.loandecisioning.decisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
 * Exercises Epic 4.1's done-criterion: {@code POST /cases/{id}/decision} overrides a {@code
 * REFERRED} case, with the original automated {@link Decision} staying queryable alongside the
 * override. Uses the real {@code credit-score-service} image (same pattern as {@link
 * DecisionEngineIntegrationTest}) so the seeded {@code REFERRED} cases come from the credit-score
 * band path, not the verification-failure sentinel — {@link
 * DecisionEngineProviderOutageIntegrationTest} separately covers the third path (no automated
 * {@code Decision} to override at all).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CaseDecisionIntegrationTest {

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

    @Test
    void approvingAReferredCaseRecordsAnOverrideReferencingTheAutomatedDecision() {
        publishStandardPolicyScorecardPricing();
        UUID applicationId = submitFairBandApplication();
        awaitStatus(applicationId, "REFERRED");
        Decision automated = onlyDecisionFor(applicationId);

        String underwriterToken = login("underwriter", SEED_PASSWORD);
        Map<String, Object> response =
                decide(underwriterToken, applicationId, "APPROVED", "Manually reviewed and approved");

        assertThat(response.get("outcome")).isEqualTo("APPROVED");
        assertThat(response.get("decisionId")).isNotNull();
        assertThat(response.get("overridesDecisionId")).isEqualTo(automated.getId().toString());
        assertThat(response.get("actor")).isEqualTo("underwriter");

        assertThat(getStatus(login("applicant", SEED_PASSWORD), applicationId)).isEqualTo("APPROVED");

        List<Decision> decisions = decisionRepository.findByApplicationId(applicationId);
        assertThat(decisions).hasSize(2);
        assertThat(decisions)
                .extracting(d -> d.getOutcome().name())
                .containsExactlyInAnyOrder("REFERRED", "APPROVED");
    }

    @Test
    void sendingACaseBackToUnderwritingDoesNotCreateADecisionRow() {
        publishStandardPolicyScorecardPricing();
        UUID applicationId = submitFairBandApplication();
        awaitStatus(applicationId, "REFERRED");

        String underwriterToken = login("underwriter", SEED_PASSWORD);
        Map<String, Object> response =
                decide(underwriterToken, applicationId, "UNDERWRITING", "Need updated payslip");

        assertThat(response.get("outcome")).isEqualTo("UNDERWRITING");
        assertThat(response.get("decisionId")).isNull();
        assertThat(response.get("overridesDecisionId")).isNull();

        assertThat(getStatus(login("applicant", SEED_PASSWORD), applicationId)).isEqualTo("UNDERWRITING");
        assertThat(decisionRepository.findByApplicationId(applicationId)).hasSize(1);
    }

    @Test
    void replayingTheSameIdempotencyKeyDoesNotDuplicateTheOverride() {
        publishStandardPolicyScorecardPricing();
        UUID applicationId = submitFairBandApplication();
        awaitStatus(applicationId, "REFERRED");

        String underwriterToken = login("underwriter", SEED_PASSWORD);
        String idempotencyKey = UUID.randomUUID().toString();
        Map<String, Object> first =
                decideWithKey(
                        underwriterToken, applicationId, "APPROVED", "Approved on review", idempotencyKey);
        Map<String, Object> second =
                decideWithKey(
                        underwriterToken, applicationId, "APPROVED", "Approved on review", idempotencyKey);

        assertThat(second.get("decisionId")).isEqualTo(first.get("decisionId"));
        assertThat(decisionRepository.findByApplicationId(applicationId)).hasSize(2);
    }

    @Test
    void decidingANonReferredCaseIsConflict() {
        String applicantToken = login("applicant", SEED_PASSWORD);
        UUID applicationId = createDraft(applicantToken);

        String underwriterToken = login("underwriter", SEED_PASSWORD);
        ResponseEntity<String> response =
                decideRaw(underwriterToken, applicationId, "APPROVED", "Should be rejected");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void decidingAnUnknownApplicationIsNotFound() {
        String underwriterToken = login("underwriter", SEED_PASSWORD);
        ResponseEntity<String> response =
                decideRaw(underwriterToken, UUID.randomUUID(), "APPROVED", "Should be rejected");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void nonUnderwriterIsForbidden() {
        publishStandardPolicyScorecardPricing();
        UUID applicationId = submitFairBandApplication();
        awaitStatus(applicationId, "REFERRED");

        String applicantToken = login("applicant", SEED_PASSWORD);
        ResponseEntity<String> response =
                decideRaw(applicantToken, applicationId, "APPROVED", "Should be rejected");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private Decision onlyDecisionFor(UUID applicationId) {
        List<Decision> decisions = decisionRepository.findByApplicationId(applicationId);
        assertThat(decisions).hasSize(1);
        return decisions.get(0);
    }

    private void awaitStatus(UUID applicationId, String expectedStatus) {
        String token = login("applicant", SEED_PASSWORD);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(
                        () -> assertThat(getStatus(token, applicationId)).isEqualTo(expectedStatus));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decide(
            String token, UUID applicationId, String outcome, String reason) {
        return decideWithKey(token, applicationId, outcome, reason, UUID.randomUUID().toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decideWithKey(
            String token, UUID applicationId, String outcome, String reason, String idempotencyKey) {
        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        "/cases/" + applicationId + "/decision",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                Map.of("outcome", outcome, "reason", reason),
                                headersWithIdempotencyKey(token, idempotencyKey)),
                        new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<String> decideRaw(
            String token, UUID applicationId, String outcome, String reason) {
        return restTemplate.exchange(
                "/cases/" + applicationId + "/decision",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("outcome", outcome, "reason", reason),
                        headersWithIdempotencyKey(token, UUID.randomUUID().toString())),
                String.class);
    }

    private HttpHeaders headersWithIdempotencyKey(String token, String idempotencyKey) {
        HttpHeaders headers = authHeaders(token);
        headers.set("Idempotency-Key", idempotencyKey);
        return headers;
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
                Map.of("effectiveDate", "2026-01-01", "rules", Map.of("bandOutcomes", bandOutcomes)));

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
                Map.of("aprTermRules", Map.of("baseAprPercent", 18.5, "termMonths", 24)));
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

    /** Same fixture as {@code DecisionEngineIntegrationTest.fairBandIsReferred}. */
    private UUID submitFairBandApplication() {
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
                        "requestedAmountKes", "360000",
                        "requestedTermMonths", 12,
                        "declaredMonthlyIncomeKes", "100000",
                        "declaredEmploymentStatus", "EMPLOYED",
                        "declaredEmployerName", "Acme Ltd");
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

    private UUID createDraft(String token) {
        var headers = authHeaders(token);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        Map<String, String> body =
                Map.of(
                        "fullName", "Draft Applicant",
                        "email", "draft@example.com",
                        "phone", "+254700000001");
        ResponseEntity<Map<String, Object>> created =
                restTemplate.exchange(
                        "/applications",
                        HttpMethod.POST,
                        new HttpEntity<>(body, headers),
                        new ParameterizedTypeReference<>() {});
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString((String) created.getBody().get("id"));
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
