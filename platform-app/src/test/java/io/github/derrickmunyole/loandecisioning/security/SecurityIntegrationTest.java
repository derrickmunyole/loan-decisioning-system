package io.github.derrickmunyole.loandecisioning.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.derrickmunyole.loandecisioning.security.Role;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginRequest;
import io.github.derrickmunyole.loandecisioning.security.auth.LoginResponse;
import io.github.derrickmunyole.loandecisioning.security.user.AppUser;
import io.github.derrickmunyole.loandecisioning.security.user.AppUserRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises the endpoint-level role gate and method-level {@code @PreAuthorize} ownership check
 * against the real {@code /applications} endpoints (Milestone 1, Epic 1.4) — these used to run
 * against a wiring-only probe controller, removed once real role-gated endpoints existed.
 */
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
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void applicantCanCreateApplication() {
        String token = login("applicant", SEED_PASSWORD);

        var response = restTemplate.exchange("/applications", HttpMethod.POST, createRequest(token), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void underwriterIsForbiddenFromApplicantEndpoint() {
        String token = login("underwriter", SEED_PASSWORD);

        var response = restTemplate.exchange("/applications", HttpMethod.POST, createRequest(token), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void ownerCanUpdateOwnDraftButAnotherApplicantCannot() {
        String ownerToken = login("applicant", SEED_PASSWORD);
        UUID applicationId = createApplication(ownerToken);

        ensureApplicantUser("applicant2");
        String otherToken = login("applicant2", SEED_PASSWORD);

        var ownResponse =
                restTemplate.exchange(
                        "/applications/" + applicationId,
                        HttpMethod.PATCH,
                        patchRequest(ownerToken),
                        String.class);
        var otherResponse =
                restTemplate.exchange(
                        "/applications/" + applicationId,
                        HttpMethod.PATCH,
                        patchRequest(otherToken),
                        String.class);

        assertThat(ownResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(otherResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anotherApplicantIsForbiddenFromGetTimelineSubmitAndDocuments() {
        String ownerToken = login("applicant", SEED_PASSWORD);
        UUID applicationId = createApplication(ownerToken);

        ensureApplicantUser("applicant2");
        String otherToken = login("applicant2", SEED_PASSWORD);

        assertThat(get(applicationId, otherToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(timeline(applicationId, otherToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(uploadDocument(applicationId, otherToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(submit(applicationId, otherToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // sanity: the guard isn't blocking the legitimate owner too
        assertThat(get(applicationId, ownerToken).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(timeline(applicationId, ownerToken).getStatusCode()).isEqualTo(HttpStatus.OK);
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
                restTemplate.postForEntity(
                        "/applications", createBody(), String.class);

        assertThat(response.getStatusCode())
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    private UUID createApplication(String token) {
        var response = restTemplate.exchange("/applications", HttpMethod.POST, createRequest(token), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString((String) response.getBody().get("id"));
    }

    private void ensureApplicantUser(String username) {
        if (appUserRepository.findByUsername(username).isEmpty()) {
            appUserRepository.save(
                    new AppUser(username, passwordEncoder.encode(SEED_PASSWORD), Role.APPLICANT));
        }
    }

    private Map<String, String> createBody() {
        return Map.of(
                "fullName", "Test Applicant",
                "email", "applicant@example.com",
                "phone", "+254700000000");
    }

    private HttpEntity<Map<String, String>> createRequest(String token) {
        var headers = authHeaders(token);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        return new HttpEntity<>(createBody(), headers);
    }

    private HttpEntity<Map<String, Object>> patchRequest(String token) {
        var body =
                Map.<String, Object>of(
                        "requestedAmountKes", 100000,
                        "requestedTermMonths", 12,
                        "declaredMonthlyIncomeKes", 50000,
                        "declaredEmploymentStatus", "EMPLOYED");
        return new HttpEntity<>(body, authHeaders(token));
    }

    private ResponseEntity<String> get(UUID applicationId, String token) {
        return restTemplate.exchange(
                "/applications/" + applicationId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                String.class);
    }

    private ResponseEntity<String> timeline(UUID applicationId, String token) {
        return restTemplate.exchange(
                "/applications/" + applicationId + "/timeline",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                String.class);
    }

    private ResponseEntity<String> submit(UUID applicationId, String token) {
        var headers = authHeaders(token);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        return restTemplate.exchange(
                "/applications/" + applicationId + "/submit",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("consentAccepted", true), headers),
                String.class);
    }

    private ResponseEntity<String> uploadDocument(UUID applicationId, String token) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("documentType", "ID_DOCUMENT");
        body.add(
                "file",
                new ByteArrayResource("synthetic id document".getBytes()) {
                    @Override
                    public String getFilename() {
                        return "id.txt";
                    }
                });
        var headers = authHeaders(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.exchange(
                "/applications/" + applicationId + "/documents",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);
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
