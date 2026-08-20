package io.github.derrickmunyole.loandecisioning.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.derrickmunyole.loandecisioning.infrastructure.messaging.RabbitTopologyConfig;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginRequest;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginResponse;
import io.github.derrickmunyole.loandecisioning.workflow.workqueue.WorkflowTask;
import io.github.derrickmunyole.loandecisioning.workflow.workqueue.WorkflowTaskRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
 * Exercises Epic 2.2's done-criterion end to end: a forced consumer failure surfaces as a
 * work-queue item after retries genuinely exhaust — no test-only shortcut in production code, the
 * malformed message is published straight onto the real queue and left to Spring's real
 * retry+backoff and RabbitMQ's real dead-lettering.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkQueueIntegrationTest {

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
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private WorkflowTaskRepository workflowTaskRepository;

    @Test
    void exhaustedRetriesOnAMalformedMessageSurfaceAsAWorkQueueItem() {
        var task = raiseAMessageProcessingFailureTask();

        assertThat(task.getTaskType().name()).isEqualTo("MESSAGE_PROCESSING_FAILURE");
        assertThat(task.getSourceQueue())
                .isEqualTo(RabbitTopologyConfig.NOTIFICATION_REQUESTED_QUEUE);
        assertThat(task.getStatus().name()).isEqualTo("OPEN");
        assertThat(task.getCorrelationId()).isNotNull();
        assertThat(task.getAttempts()).isNotNull();

        // Epic 4.2 role scoping: MESSAGE_PROCESSING_FAILURE is ops's queue, not the
        // underwriter's (UNDERWRITE_CASE is) -- see WorkQueueController.
        String underwriterToken = login("underwriter", SEED_PASSWORD);
        assertThat(getWorkQueue(underwriterToken))
                .extracting(entry -> entry.get("id"))
                .doesNotContain(task.getId().toString());

        String opsToken = login("operations_analyst", SEED_PASSWORD);
        assertThat(getWorkQueue(opsToken))
                .extracting(entry -> entry.get("id"))
                .contains(task.getId().toString());

        String applicantToken = login("applicant", SEED_PASSWORD);
        var forbidden =
                restTemplate.exchange(
                        "/work-queue",
                        HttpMethod.GET,
                        new HttpEntity<>(authHeaders(applicantToken)),
                        String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void resolvingATaskMarksItResolvedAndIsIdempotent() {
        var task = raiseAMessageProcessingFailureTask();

        String opsToken = login("operations_analyst", SEED_PASSWORD);
        Map<String, Object> resolved = resolve(opsToken, task.getId(), "Inspected manually");
        assertThat(resolved.get("status")).isEqualTo("RESOLVED");
        assertThat(resolved.get("resolvedBy")).isEqualTo("operations_analyst");
        assertThat(resolved.get("resolution")).isEqualTo("Inspected manually");

        Map<String, Object> secondAttempt =
                resolve(opsToken, task.getId(), "Different note -- should be ignored");
        assertThat(secondAttempt.get("resolution")).isEqualTo("Inspected manually");
    }

    @Test
    void underwriterCannotResolveAnOpsTask() {
        var task = raiseAMessageProcessingFailureTask();

        String underwriterToken = login("underwriter", SEED_PASSWORD);
        ResponseEntity<String> response = resolveRaw(underwriterToken, task.getId(), "Should be rejected");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void resolvingAnUnknownTaskIsNotFound() {
        String opsToken = login("operations_analyst", SEED_PASSWORD);
        ResponseEntity<String> response = resolveRaw(opsToken, UUID.randomUUID(), "Should be rejected");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void opsCannotResolveAnUnderwriteCaseTaskEvenByNamingItDirectly() {
        publishStandardPolicyScorecardPricing();
        UUID applicationId = submitIdentityMismatchApplication();

        String applicantToken = login("applicant", SEED_PASSWORD);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(
                        () -> assertThat(getStatus(applicantToken, applicationId)).isEqualTo("REFERRED"));

        var underwriteCaseTask =
                workflowTaskRepository.findAllByOrderByCreatedAtDesc().stream()
                        .filter(t -> applicationId.equals(t.getApplicationId()))
                        .findFirst()
                        .orElseThrow();

        String opsToken = login("operations_analyst", SEED_PASSWORD);
        ResponseEntity<String> response =
                resolveRaw(opsToken, underwriteCaseTask.getId(), "Should be rejected");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private WorkflowTask raiseAMessageProcessingFailureTask() {
        UUID eventId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        publishMalformedNotificationRequested(eventId, correlationId);

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(
                        () ->
                                assertThat(
                                                workflowTaskRepository.findAllByOrderByCreatedAtDesc().stream()
                                                        .anyMatch(t -> correlationId.equals(t.getCorrelationId())))
                                        .isTrue());
        return workflowTaskRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(t -> correlationId.equals(t.getCorrelationId()))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolve(String token, UUID taskId, String resolution) {
        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        "/work-queue/" + taskId + "/resolve",
                        HttpMethod.POST,
                        new HttpEntity<>(Map.of("resolution", resolution), authHeaders(token)),
                        new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<String> resolveRaw(String token, UUID taskId, String resolution) {
        return restTemplate.exchange(
                "/work-queue/" + taskId + "/resolve",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("resolution", resolution), authHeaders(token)),
                String.class);
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

    /** Reaches REFERRED via the verification-failure short-circuit, so it never needs a working
     * credit-score service -- this test class deliberately has no such container. */
    private UUID submitIdentityMismatchApplication() {
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

    private void publishMalformedNotificationRequested(UUID eventId, String correlationId) {
        Message message =
                MessageBuilder.withBody("{ not valid json".getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setHeader("eventId", eventId.toString())
                        .setHeader("correlationId", correlationId)
                        .build();
        rabbitTemplate.send(
                RabbitTopologyConfig.EVENTS_EXCHANGE,
                RabbitTopologyConfig.NOTIFICATION_REQUESTED_ROUTING_KEY,
                message);
    }

    private List<Map<String, Object>> getWorkQueue(String token) {
        ResponseEntity<List<Map<String, Object>>> response =
                restTemplate.exchange(
                        "/work-queue",
                        HttpMethod.GET,
                        new HttpEntity<>(authHeaders(token)),
                        new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
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
