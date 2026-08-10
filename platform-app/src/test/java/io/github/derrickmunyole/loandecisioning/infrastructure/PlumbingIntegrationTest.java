package io.github.derrickmunyole.loandecisioning.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.derrickmunyole.loandecisioning.infrastructure.audit.AuditEventRepository;
import io.github.derrickmunyole.loandecisioning.infrastructure.messaging.ConsumedEventRepository;
import io.github.derrickmunyole.loandecisioning.infrastructure.messaging.RabbitTopologyConfig;
import io.github.derrickmunyole.loandecisioning.infrastructure.notification.NotificationRepository;
import io.github.derrickmunyole.loandecisioning.infrastructure.outbox.OutboxEvent;
import io.github.derrickmunyole.loandecisioning.infrastructure.outbox.OutboxEventRepository;
import io.github.derrickmunyole.loandecisioning.infrastructure.outbox.OutboxEventStatus;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginRequest;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
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

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlumbingIntegrationTest {

    private static final String SEED_PASSWORD = "TestPassword123!";
    private static final String NOTIFICATION_CONSUMER_NAME = "notification-requested-listener";

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
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ConsumedEventRepository consumedEventRepository;

    @Test
    void demoEventProducesAuditRowOutboxPublishAndExactlyOneNotificationEvenUnderDuplicateDelivery() {
        long auditCountBefore = auditEventRepository.count();
        String recipient = "someone@example.com";

        UUID notificationId = callProbe(recipient);

        OutboxEvent outboxEvent =
                await().atMost(Duration.ofSeconds(10))
                        .until(() -> findOutboxEvent(notificationId), Optional::isPresent)
                        .orElseThrow();
        assertThat(outboxEvent.getEventType()).isEqualTo("notification.requested");

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () ->
                                assertThat(
                                                outboxEventRepository
                                                        .findById(outboxEvent.getId())
                                                        .orElseThrow()
                                                        .getStatus())
                                        .isEqualTo(OutboxEventStatus.PUBLISHED));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(notificationRepository.count()).isEqualTo(1));
        var notification = notificationRepository.findById(notificationId).orElseThrow();
        assertThat(notification.getRecipient()).isEqualTo(recipient);

        assertThat(auditEventRepository.count()).isEqualTo(auditCountBefore + 1);
        var auditEvent =
                auditEventRepository.findAll().stream()
                        .filter(e -> e.getTargetId().equals(recipient))
                        .findFirst()
                        .orElseThrow();
        assertThat(auditEvent.getAction()).isEqualTo("PLUMBING_PROBE_DEMO_EVENT");
        assertThat(auditEvent.getTargetType()).isEqualTo("Notification");

        assertThat(
                        consumedEventRepository.existsByConsumerNameAndEventId(
                                NOTIFICATION_CONSUMER_NAME, outboxEvent.getId()))
                .isTrue();

        // Force a duplicate delivery of the exact same event directly onto the queue, bypassing
        // the relay, with the same eventId header the relay already published successfully.
        publishDuplicate(outboxEvent);

        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).until(() -> true);
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    private UUID callProbe(String recipient) {
        String token = login("applicant", SEED_PASSWORD);
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        "/internal/plumbing-probe/demo-event",
                        HttpMethod.POST,
                        new HttpEntity<>(Map.of("recipient", recipient), headers),
                        new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return UUID.fromString((String) response.getBody().get("notificationId"));
    }

    private Optional<OutboxEvent> findOutboxEvent(UUID aggregateId) {
        return outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(aggregateId))
                .findFirst();
    }

    private void publishDuplicate(OutboxEvent outboxEvent) {
        Message message =
                MessageBuilder.withBody(outboxEvent.getPayload().getBytes())
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setHeader("eventId", outboxEvent.getId().toString())
                        .setHeader("correlationId", outboxEvent.getCorrelationId())
                        .build();
        rabbitTemplate.send(
                RabbitTopologyConfig.EVENTS_EXCHANGE,
                RabbitTopologyConfig.NOTIFICATION_REQUESTED_ROUTING_KEY,
                message);
    }

    private String login(String username, String password) {
        var response =
                restTemplate.postForEntity(
                        "/auth/login", new LoginRequest(username, password), LoginResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().token();
    }
}
