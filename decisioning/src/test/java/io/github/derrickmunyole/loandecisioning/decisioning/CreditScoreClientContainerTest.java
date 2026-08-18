package io.github.derrickmunyole.loandecisioning.decisioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Paths;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The Testcontainers leg of Epic 3.5's done-criterion — {@link CreditScoreClient} against the
 * real {@code credit-score-service} image, built fresh from its own Dockerfile, with nothing else
 * in the loop: no Postgres, no RabbitMQ, no Spring context, no {@link DecisionEngineHandler} or
 * {@link PolicyEvaluator}. Deliberately narrower than {@code DecisionEngineIntegrationTest}
 * (Epic 3.4, in {@code platform-app}), which already proves the full decision pipeline works
 * against this same image — this test exists so a client-contract regression (a wire-format
 * mismatch with the real service) fails here, on its own, rather than only showing up entangled
 * with a policy-evaluation failure in the slower, full-pipeline suite.
 */
@Testcontainers
class CreditScoreClientContainerTest {

    @Container
    static GenericContainer<?> creditScoreService =
            new GenericContainer<>(
                            new ImageFromDockerfile()
                                    // Explicit per-file context, not the whole directory: the
                                    // latter also picks up .venv/.pytest_cache/.ruff_cache if a
                                    // developer has ever run this component's own tests locally,
                                    // and ImageFromDockerfile doesn't honor .dockerignore the way
                                    // `docker build` does -- a stray .venv symlink pointing at an
                                    // absolute host path breaks the build-context tar entirely.
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

    private CreditScoreClient client() {
        String baseUrl =
                "http://" + creditScoreService.getHost() + ":" + creditScoreService.getMappedPort(8000);
        WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();
        return new CreditScoreClient(webClient, Duration.ofSeconds(5));
    }

    @Test
    void scoresAWellQualifiedApplicantInTheExcellentRange() {
        CreditScoreResponse response =
                client()
                        .score(new BigDecimal("50000"), 24, new BigDecimal("200000"), "EMPLOYED");

        assertThat(response.score()).isBetween(300, 850);
        assertThat(response.modelVersion()).isNotBlank();
        assertThat(response.reasonContributions()).isNotEmpty();
    }

    @Test
    void scoresAPoorlyQualifiedApplicantInTheLowRange() {
        CreditScoreResponse response =
                client()
                        .score(
                                new BigDecimal("360000"),
                                12,
                                new BigDecimal("100000"),
                                "SELF_EMPLOYED");

        assertThat(response.score()).isBetween(300, 850);
        assertThat(response.modelVersion()).isNotBlank();
        assertThat(response.reasonContributions()).isNotEmpty();
    }

    @Test
    void repeatedIdenticalRequestsAreDeterministic() {
        CreditScoreResponse first =
                client()
                        .score(new BigDecimal("50000"), 24, new BigDecimal("200000"), "EMPLOYED");
        CreditScoreResponse second =
                client()
                        .score(new BigDecimal("50000"), 24, new BigDecimal("200000"), "EMPLOYED");

        assertThat(second.score()).isEqualTo(first.score());
        assertThat(second.modelVersion()).isEqualTo(first.modelVersion());
    }
}
