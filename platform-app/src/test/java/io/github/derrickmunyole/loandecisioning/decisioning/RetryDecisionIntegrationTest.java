package io.github.derrickmunyole.loandecisioning.decisioning;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginRequest;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
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
 * Exercises Epic 4.2's {@code POST /cases/{id}/retry-decision} done-criterion: a credit-score
 * provider outage is recoverable by an operations analyst via API. Uses WireMock's scenario/state
 * mechanism (already a dependency from Epic 3.5's {@link CreditScoreClientWireMockTest}) rather
 * than Testcontainers pause/unpause — a stub can fail on the first request and switch to
 * succeeding from the second, which a real container can't do without external orchestration
 * mid-test.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RetryDecisionIntegrationTest {

    private static final String SEED_PASSWORD = "TestPassword123!";
    private static final String OUTAGE_THEN_RECOVERY = "outage-then-recovery";

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

    @RegisterExtension
    static WireMockExtension creditScoreService =
            WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add(
                "app.security.jwt.secret",
                () -> "test-only-jwt-signing-secret-at-least-32-bytes-long");
        registry.add("app.security.seed-users-password", () -> SEED_PASSWORD);
        registry.add("app.outbox.relay-interval", () -> "200");
        registry.add("app.credit-score.base-url", () -> creditScoreService.baseUrl());
    }

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private DecisionRepository decisionRepository;

    @Test
    void retryingAfterTheProviderRecoversResumesTheDecisionAndResolvesTheTask() {
        stubScoreEndpointToFailOnceThenSucceed();
        publishStandardPolicyScorecardPricing();
        UUID applicationId = submitApplication("300000", 24, "80000", "EMPLOYED");

        String applicantToken = login("applicant", SEED_PASSWORD);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(
                        () -> assertThat(getStatus(applicantToken, applicationId)).isEqualTo("REFERRED"));
        assertThat(decisionRepository.findByApplicationId(applicationId)).isEmpty();

        String opsToken = login("operations_analyst", SEED_PASSWORD);
        Map<String, Object> retryResponse = retry(opsToken, applicationId, UUID.randomUUID().toString());
        assertThat(retryResponse.get("status")).isEqualTo("UNDERWRITING");

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(
                        () -> assertThat(decisionRepository.findByApplicationId(applicationId)).hasSize(1));
        Decision decision = decisionRepository.findByApplicationId(applicationId).get(0);
        assertThat(decision.getCreditScoreModelVersion()).isEqualTo("wiremock-recovery-v1");
    }

    @Test
    void retryingAReferredCaseWithNoOpenProviderOutageTaskIsNotFound() {
        // REFERRED via the verification-failure short-circuit -- an automated Decision exists,
        // but no CREDIT_SCORE_PROVIDER_UNAVAILABLE task, since the credit-score call never
        // happens on this path (DecisionEngineHandler returns before reaching it).
        publishStandardPolicyScorecardPricing();
        UUID applicationId = submitIdentityMismatchApplication();

        String applicantToken = login("applicant", SEED_PASSWORD);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(
                        () -> assertThat(getStatus(applicantToken, applicationId)).isEqualTo("REFERRED"));

        String opsToken = login("operations_analyst", SEED_PASSWORD);
        ResponseEntity<String> response = retryRaw(opsToken, applicationId, UUID.randomUUID().toString());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void nonOperationsAnalystIsForbidden() {
        String underwriterToken = login("underwriter", SEED_PASSWORD);
        ResponseEntity<String> response =
                retryRaw(underwriterToken, UUID.randomUUID(), UUID.randomUUID().toString());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void stubScoreEndpointToFailOnceThenSucceed() {
        creditScoreService.stubFor(
                post(urlEqualTo("/score"))
                        .inScenario(OUTAGE_THEN_RECOVERY)
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(aResponse().withStatus(500).withBody("simulated outage"))
                        .willSetStateTo("RECOVERED"));
        creditScoreService.stubFor(
                post(urlEqualTo("/score"))
                        .inScenario(OUTAGE_THEN_RECOVERY)
                        .whenScenarioStateIs("RECOVERED")
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                                {"score": 782, "band": "VERY_GOOD", "modelVersion": "wiremock-recovery-v1", "reasonContributions": []}
                                                """)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> retry(String token, UUID applicationId, String idempotencyKey) {
        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        "/cases/" + applicationId + "/retry-decision",
                        HttpMethod.POST,
                        new HttpEntity<>(headersWithIdempotencyKey(token, idempotencyKey)),
                        new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<String> retryRaw(String token, UUID applicationId, String idempotencyKey) {
        return restTemplate.exchange(
                "/cases/" + applicationId + "/retry-decision",
                HttpMethod.POST,
                new HttpEntity<>(headersWithIdempotencyKey(token, idempotencyKey)),
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

    private UUID submitIdentityMismatchApplication() {
        String token = login("applicant", SEED_PASSWORD);

        var createHeaders = authHeaders(token);
        createHeaders.set("Idempotency-Key", UUID.randomUUID().toString());
        Map<String, String> createBody =
                Map.of(
                        "fullName", "Identity Mismatch Applicant",
                        "email", "mismatch@example.com",
                        "phone", "+254700000002");
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
                        "requestedAmountKes", "100000",
                        "requestedTermMonths", 12,
                        "declaredMonthlyIncomeKes", "50000",
                        "declaredEmploymentStatus", "EMPLOYED",
                        "declaredEmployerName", "SYNTHETIC_IDENTITY_MISMATCH");
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
