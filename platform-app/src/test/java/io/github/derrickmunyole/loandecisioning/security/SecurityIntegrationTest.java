package io.github.derrickmunyole.loandecisioning.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.derrickmunyole.loandecisioning.security.auth.LoginRequest;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityIntegrationTest {

    private static final String SEED_PASSWORD = "TestPassword123!";

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "app.security.jwt.secret",
                () -> "test-only-jwt-signing-secret-at-least-32-bytes-long");
        registry.add("app.security.seed-users-password", () -> SEED_PASSWORD);
    }

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void underwriterCanLoginAndAccessRoleGatedEndpoint() {
        String token = login("underwriter", SEED_PASSWORD);

        var response =
                restTemplate.exchange(
                        "/internal/security-probe/underwriter-only",
                        HttpMethod.GET,
                        authorizedRequest(token),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void applicantIsForbiddenFromUnderwriterOnlyEndpoint() {
        String token = login("applicant", SEED_PASSWORD);

        var response =
                restTemplate.exchange(
                        "/internal/security-probe/underwriter-only",
                        HttpMethod.GET,
                        authorizedRequest(token),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void selfOwnershipCheckAllowsMatchingUsernameAndRejectsOthers() {
        String token = login("applicant", SEED_PASSWORD);

        var ownResponse =
                restTemplate.exchange(
                        "/internal/security-probe/self/applicant",
                        HttpMethod.GET,
                        authorizedRequest(token),
                        String.class);
        var otherResponse =
                restTemplate.exchange(
                        "/internal/security-probe/self/underwriter",
                        HttpMethod.GET,
                        authorizedRequest(token),
                        String.class);

        assertThat(ownResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(otherResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() {
        var response =
                restTemplate.postForEntity(
                        "/auth/login",
                        new LoginRequest("applicant", "wrong-password"),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unauthenticatedRequestToProtectedEndpointIsRejected() {
        var response =
                restTemplate.getForEntity(
                        "/internal/security-probe/self/applicant", String.class);

        assertThat(response.getStatusCode())
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    private String login(String username, String password) {
        var response =
                restTemplate.postForEntity(
                        "/auth/login", new LoginRequest(username, password), LoginResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().token();
    }

    private HttpEntity<Void> authorizedRequest(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }
}
