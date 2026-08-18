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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * Exercises Epic 4.3's done-criterion: {@code GET /applications/{id}/timeline} satisfies blueprint
 * §11's "link to decision version, reasons, evidence references, event timeline, and audit
 * history" for all four staff roles, while the applicant keeps the pre-4.3 narrow response. Uses
 * the real {@code credit-score-service} image (same pattern as {@link CaseDecisionIntegrationTest})
 * so there's a real {@link Decision}, real verification evidence, and a real automated-transition
 * audit trail to assert against.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationTimelineIntegrationTest {

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
        registry.add(
                "app.credit-score.base-url",
                () ->
                        "http://"
                                + creditScoreService.getHost()
                                + ":"
                                + creditScoreService.getMappedPort(8000));
    }

    @Autowired private TestRestTemplate restTemplate;

    @ParameterizedTest
    @ValueSource(strings = {"underwriter", "operations_analyst", "policy_admin", "auditor"})
    void staffRoleSeesTheAggregateTimelineWithDecisionEvidenceAndEvents(String username) {
        publishStandardPolicyScorecardPricing();
        UUID applicationId = submitFairBandApplication();
        awaitStatus(applicationId, "REFERRED");

        String token = login(username, SEED_PASSWORD);
        Map<String, Object> body = timeline(token, applicationId);

        assertThat(body).containsKey("decisions");
        assertThat(body).containsKey("evidence");
        assertThat(body).containsKey("events");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> decisions = (List<Map<String, Object>>) body.get("decisions");
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).get("outcome")).isEqualTo("REFERRED");
        assertThat(decisions.get(0).get("underwritingSnapshotId")).isNotNull();
        assertThat(decisions.get(0).get("policyVersionId")).isNotNull();
        assertThat(decisions.get(0).get("scorecardVersionId")).isNotNull();
        assertThat(decisions.get(0).get("pricingVersionId")).isNotNull();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evidence = (List<Map<String, Object>>) body.get("evidence");
        assertThat(evidence)
                .extracting(e -> e.get("type"))
                .containsExactlyInAnyOrder("IDENTITY", "INCOME");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) body.get("events");
        assertThat(events)
                .extracting(e -> e.get("action"))
                .contains(
                        "APPLICATION_VERIFICATION_STARTED",
                        "APPLICATION_VERIFICATION_COMPLETED",
                        "AUTOMATED_DECISION_RECORDED");
    }

    @Test
    void owningApplicantStillGetsTheNarrowEventOnlyView() {
        publishStandardPolicyScorecardPricing();
        UUID applicationId = submitFairBandApplication();
        awaitStatus(applicationId, "REFERRED");

        String token = login("applicant", SEED_PASSWORD);
        ResponseEntity<List<Map<String, Object>>> response =
                restTemplate.exchange(
                        "/applications/" + applicationId + "/timeline",
                        HttpMethod.GET,
                        new HttpEntity<>(authHeaders(token)),
                        new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> events = response.getBody();
        assertThat(events).isNotEmpty();
        assertThat(events.get(0)).containsKeys("actor", "action", "occurredAt");
        assertThat(events.get(0)).doesNotContainKey("decisions");
    }

    @ParameterizedTest
    @ValueSource(strings = {"underwriter", "operations_analyst", "policy_admin", "auditor"})
    void unknownApplicationTimelineIsNotFoundForStaff(String username) {
        String token = login(username, SEED_PASSWORD);
        ResponseEntity<String> response =
                restTemplate.exchange(
                        "/applications/" + UUID.randomUUID() + "/timeline",
                        HttpMethod.GET,
                        new HttpEntity<>(authHeaders(token)),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private Map<String, Object> timeline(String token, UUID applicationId) {
        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        "/applications/" + applicationId + "/timeline",
                        HttpMethod.GET,
                        new HttpEntity<>(authHeaders(token)),
                        new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private void awaitStatus(UUID applicationId, String expectedStatus) {
        String token = login("applicant", SEED_PASSWORD);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(
                        () -> assertThat(getStatus(token, applicationId)).isEqualTo(expectedStatus));
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
