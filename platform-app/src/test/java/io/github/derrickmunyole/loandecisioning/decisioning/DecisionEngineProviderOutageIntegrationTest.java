package io.github.derrickmunyole.loandecisioning.decisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.derrickmunyole.loandecisioning.security.auth.LoginRequest;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginResponse;
import io.github.derrickmunyole.loandecisioning.workflow.api.WorkflowTaskType;
import io.github.derrickmunyole.loandecisioning.workflow.workqueue.WorkflowTaskRepository;
import java.time.Duration;
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
 * The other half of Epic 3.4's done-criterion: a forced credit-score outage never produces a
 * silent or wrong decision. No real {@code credit-score-service} container here — {@code
 * app.credit-score.base-url} points at the RFC 2606 {@code .invalid} TLD, guaranteeing an
 * immediate, deterministic DNS failure rather than depending on which local ports happen to be
 * free.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DecisionEngineProviderOutageIntegrationTest {

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
        registry.add("app.credit-score.base-url", () -> "http://credit-score-service.invalid:8000");
        registry.add("app.credit-score.timeout", () -> "PT1S");
    }

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private DecisionRepository decisionRepository;
    @Autowired private WorkflowTaskRepository workflowTaskRepository;

    @Test
    void unreachableProviderReferesTheApplicationAndRaisesAnOpsTaskWithoutARecordedDecision() {
        publishStandardPolicyScorecardPricing();
        UUID applicationId = submitApplication("300000", 24, "80000", "EMPLOYED");

        String applicantToken = login("applicant", SEED_PASSWORD);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(
                        () ->
                                assertThat(getStatus(applicantToken, applicationId))
                                        .isEqualTo("REFERRED"));

        assertThat(decisionRepository.findByApplicationId(applicationId)).isEmpty();

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () ->
                                assertThat(
                                                workflowTaskRepository.findAllByOrderByCreatedAtDesc().stream()
                                                        .anyMatch(
                                                                task ->
                                                                        task.getTaskType()
                                                                                == WorkflowTaskType
                                                                                        .CREDIT_SCORE_PROVIDER_UNAVAILABLE))
                                        .isTrue());
    }

    /**
     * The third of the three "how a case reaches REFERRED" paths {@link DecisionEngineHandler}'s
     * javadoc names — Epic 4.1's {@code POST /cases/{id}/decision} can't override this one, since
     * there's no automated {@link Decision} to source snapshot/policy/scorecard/pricing versions
     * from. It's resolved via this case's own {@code CREDIT_SCORE_PROVIDER_UNAVAILABLE} {@code
     * workflow_task} instead (Epic 4.2).
     */
    @Test
    void overridingAProviderOutageReferredCaseWithNoAutomatedDecisionIsConflict() {
        publishStandardPolicyScorecardPricing();
        UUID applicationId = submitApplication("300000", 24, "80000", "EMPLOYED");

        String applicantToken = login("applicant", SEED_PASSWORD);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(
                        () ->
                                assertThat(getStatus(applicantToken, applicationId))
                                        .isEqualTo("REFERRED"));
        assertThat(decisionRepository.findByApplicationId(applicationId)).isEmpty();

        String underwriterToken = login("underwriter", SEED_PASSWORD);
        var headers = authHeaders(underwriterToken);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<String> response =
                restTemplate.exchange(
                        "/cases/" + applicationId + "/decision",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                Map.of("outcome", "APPROVED", "reason", "Should be rejected"), headers),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
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

    private UUID submitApplication(
            String requestedAmountKes,
            int requestedTermMonths,
            String declaredMonthlyIncomeKes,
            String declaredEmploymentStatus) {
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
