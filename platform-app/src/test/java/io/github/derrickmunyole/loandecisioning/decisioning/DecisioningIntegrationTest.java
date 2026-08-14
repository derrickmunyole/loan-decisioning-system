package io.github.derrickmunyole.loandecisioning.decisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.derrickmunyole.loandecisioning.infrastructure.messaging.RabbitTopologyConfig;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginRequest;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginResponse;
import io.github.derrickmunyole.loandecisioning.verification.api.UnderwritingRequestedEvent;
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
 * Exercises Epic 3.1's done-criterion: an immutable {@code UnderwritingSnapshot} is created,
 * exactly once, when an application reaches {@code UNDERWRITING}. The second test forces a
 * duplicate trigger with a fresh {@code eventId} (not a redelivery of the same message), so it's
 * the DB's own unique constraint on {@code application_version_id} being proven, not the {@code
 * consumed_event} AMQP dedupe layer already covered by {@code PlumbingIntegrationTest}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DecisioningIntegrationTest {

    private static final String SEED_PASSWORD = "TestPassword123!";
    private static final int FIRST_VERSION_NUMBER = 1;

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
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UnderwritingSnapshotRepository underwritingSnapshotRepository;

    @Test
    void applicationReachingUnderwritingGetsExactlyOneSnapshotWithTheDeclaredFacts() throws Exception {
        String token = login("applicant", SEED_PASSWORD);
        UUID applicationId = createApplication(token);
        patchDraft(token, applicationId, "Acme Ltd", 200000, 24, 60000);
        submit(token, applicationId);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(getStatus(token, applicationId)).isEqualTo("UNDERWRITING"));

        List<UnderwritingSnapshot> snapshots =
                await().atMost(Duration.ofSeconds(15))
                        .until(
                                () -> underwritingSnapshotRepository.findByApplicationId(applicationId),
                                list -> !list.isEmpty());

        assertThat(snapshots).hasSize(1);
        JsonNode facts = objectMapper.readTree(snapshots.get(0).getFactsJson());
        assertThat(facts.get("requestedAmountKes").decimalValue()).isEqualByComparingTo("200000.00");
        assertThat(facts.get("declaredEmployerName").asText()).isEqualTo("Acme Ltd");
        assertThat(facts.get("evidence")).hasSize(2);
        assertThat(facts.get("evidence"))
                .allSatisfy(evidence -> assertThat(evidence.get("status").asText()).isEqualTo("PASSED"));
    }

    @Test
    void duplicateUnderwritingRequestedDeliveryStillProducesExactlyOneSnapshot() {
        String token = login("applicant", SEED_PASSWORD);
        UUID applicationId = createApplication(token);
        patchDraft(token, applicationId, "Acme Ltd", 150000, 12, 55000);
        submit(token, applicationId);

        await().atMost(Duration.ofSeconds(15))
                .until(
                        () -> underwritingSnapshotRepository.findByApplicationId(applicationId),
                        list -> !list.isEmpty());

        // A second, independent underwriting.requested delivery for the same application version
        // — a different eventId, not a redelivery of the same message — so this can only be
        // stopped by the existence check plus the DB's own unique constraint, not AMQP dedupe.
        publishFreshUnderwritingRequested(applicationId, FIRST_VERSION_NUMBER);

        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).until(() -> true);
        assertThat(underwritingSnapshotRepository.findByApplicationId(applicationId)).hasSize(1);
    }

    private void publishFreshUnderwritingRequested(UUID applicationId, int versionNumber) {
        try {
            byte[] body =
                    objectMapper.writeValueAsBytes(
                            new UnderwritingRequestedEvent(applicationId, versionNumber));
            Message message =
                    MessageBuilder.withBody(body)
                            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                            .setHeader("eventId", UUID.randomUUID().toString())
                            .build();
            rabbitTemplate.send(
                    RabbitTopologyConfig.EVENTS_EXCHANGE,
                    RabbitTopologyConfig.UNDERWRITING_REQUESTED_ROUTING_KEY,
                    message);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
