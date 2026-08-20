package io.github.derrickmunyole.loandecisioning.decisioning;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.derrickmunyole.loandecisioning.security.auth.LoginRequest;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginResponse;
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
 * Exercises Epic 3.2's done-criterion: publishing a new policy/scorecard/pricing version never
 * mutates an already-published one — draft creation and publish are the only two writes each
 * version ever gets, and publishing twice is a 409, not a silent no-op.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PolicyAdminIntegrationTest {

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
    }

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void policyVersionCanBeCreatedAsDraftThenPublishedExactlyOnce() {
        String token = login("policy_admin", SEED_PASSWORD);

        ResponseEntity<Map<String, Object>> created =
                restTemplate.exchange(
                        "/policies",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                Map.of(
                                        "effectiveDate", "2026-09-01",
                                        "rules", Map.of("minIncomeToDebtRatio", 0.4)),
                                authHeaders(token)),
                        new ParameterizedTypeReference<>() {});
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("status")).isEqualTo("DRAFT");
        assertThat(created.getBody().get("publishedAt")).isNull();
        String id = (String) created.getBody().get("id");

        ResponseEntity<Map<String, Object>> published =
                restTemplate.exchange(
                        "/policies/" + id + "/publish",
                        HttpMethod.POST,
                        new HttpEntity<>(authHeaders(token)),
                        new ParameterizedTypeReference<>() {});
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(published.getBody().get("status")).isEqualTo("PUBLISHED");
        assertThat(published.getBody().get("publishedAt")).isNotNull();

        ResponseEntity<String> secondPublish =
                restTemplate.exchange(
                        "/policies/" + id + "/publish",
                        HttpMethod.POST,
                        new HttpEntity<>(authHeaders(token)),
                        String.class);
        assertThat(secondPublish.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void scorecardVersionCanBeCreatedAsDraftThenPublishedExactlyOnce() {
        String token = login("policy_admin", SEED_PASSWORD);

        ResponseEntity<Map<String, Object>> created =
                restTemplate.exchange(
                        "/scorecards",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                Map.of("formulaConfig", Map.of("incomeWeight", 0.6, "identityWeight", 0.4)),
                                authHeaders(token)),
                        new ParameterizedTypeReference<>() {});
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("status")).isEqualTo("DRAFT");
        String id = (String) created.getBody().get("id");

        ResponseEntity<Map<String, Object>> published =
                restTemplate.exchange(
                        "/scorecards/" + id + "/publish",
                        HttpMethod.POST,
                        new HttpEntity<>(authHeaders(token)),
                        new ParameterizedTypeReference<>() {});
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(published.getBody().get("status")).isEqualTo("PUBLISHED");

        ResponseEntity<String> secondPublish =
                restTemplate.exchange(
                        "/scorecards/" + id + "/publish",
                        HttpMethod.POST,
                        new HttpEntity<>(authHeaders(token)),
                        String.class);
        assertThat(secondPublish.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void pricingVersionCanBeCreatedAsDraftThenPublishedExactlyOnce() {
        String token = login("policy_admin", SEED_PASSWORD);

        ResponseEntity<Map<String, Object>> created =
                restTemplate.exchange(
                        "/pricing",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                Map.of(
                                        "aprTermRules",
                                        Map.of(
                                                "tiers",
                                                Map.of(
                                                        "APPROVED", Map.of("aprBasisPoints", 1499, "termMonths", 36),
                                                        "CONDITIONAL_APPROVAL",
                                                                Map.of("aprBasisPoints", 1999, "termMonths", 24)))),
                                authHeaders(token)),
                        new ParameterizedTypeReference<>() {});
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("status")).isEqualTo("DRAFT");
        String id = (String) created.getBody().get("id");

        ResponseEntity<Map<String, Object>> published =
                restTemplate.exchange(
                        "/pricing/" + id + "/publish",
                        HttpMethod.POST,
                        new HttpEntity<>(authHeaders(token)),
                        new ParameterizedTypeReference<>() {});
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(published.getBody().get("status")).isEqualTo("PUBLISHED");

        ResponseEntity<String> secondPublish =
                restTemplate.exchange(
                        "/pricing/" + id + "/publish",
                        HttpMethod.POST,
                        new HttpEntity<>(authHeaders(token)),
                        String.class);
        assertThat(secondPublish.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void publishingAnUnknownIdIs404() {
        String token = login("policy_admin", SEED_PASSWORD);

        ResponseEntity<String> response =
                restTemplate.exchange(
                        "/policies/" + UUID.randomUUID() + "/publish",
                        HttpMethod.POST,
                        new HttpEntity<>(authHeaders(token)),
                        String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void nonPolicyAdminRoleIsForbiddenFromCreatingAPolicy() {
        String token = login("underwriter", SEED_PASSWORD);

        ResponseEntity<String> response =
                restTemplate.exchange(
                        "/policies",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                Map.of("effectiveDate", "2026-09-01", "rules", Map.of("x", 1)),
                                authHeaders(token)),
                        String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
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
