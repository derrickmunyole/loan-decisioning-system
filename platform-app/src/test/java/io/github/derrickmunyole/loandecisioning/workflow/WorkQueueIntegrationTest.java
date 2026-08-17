package io.github.derrickmunyole.loandecisioning.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.derrickmunyole.loandecisioning.infrastructure.messaging.RabbitTopologyConfig;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginRequest;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginResponse;
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
        UUID eventId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        publishMalformedNotificationRequested(eventId, correlationId);

        // 3 attempts, 1s/2s backoff (application.yml) before the broker dead-letters it.
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(workflowTaskRepository.count()).isEqualTo(1));

        var task = workflowTaskRepository.findAll().get(0);
        assertThat(task.getTaskType().name()).isEqualTo("MESSAGE_PROCESSING_FAILURE");
        assertThat(task.getSourceQueue())
                .isEqualTo(RabbitTopologyConfig.NOTIFICATION_REQUESTED_QUEUE);
        assertThat(task.getStatus().name()).isEqualTo("OPEN");
        assertThat(task.getCorrelationId()).isEqualTo(correlationId);
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
