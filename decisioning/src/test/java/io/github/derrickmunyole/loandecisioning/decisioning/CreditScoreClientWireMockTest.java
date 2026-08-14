package io.github.derrickmunyole.loandecisioning.decisioning;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Fast, no-live-service contract test for {@link CreditScoreClient} — the WireMock leg of Epic
 * 3.5's done-criterion (roadmap A.9: "a WireMock stub test (fast, no live service needed) plus a
 * genuine Testcontainers integration test against the real FastAPI image"; the Testcontainers leg
 * is {@link CreditScoreClientContainerTest}). Exercises request/response shape and every failure
 * mode a real provider could exhibit that's awkward to force against the real service on demand:
 * a non-2xx status, a malformed response body, and a response slower than the configured timeout.
 */
class CreditScoreClientWireMockTest {

    private static final Duration TIMEOUT = Duration.ofMillis(500);

    @RegisterExtension
    static WireMockExtension wireMock =
            WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    private CreditScoreClient client() {
        WebClient webClient = WebClient.builder().baseUrl(wireMock.baseUrl()).build();
        return new CreditScoreClient(webClient, TIMEOUT);
    }

    @Test
    void parsesAValidResponseAndSendsTheDocumentedRequestShape() {
        wireMock.stubFor(
                post(urlEqualTo("/score"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                                {
                                                  "score": 782,
                                                  "band": "VERY_GOOD",
                                                  "modelVersion": "deterministic-v1",
                                                  "reasonContributions": [
                                                    {"factor": "installmentToIncomeRatio", "value": "0.18", "impact": "POSITIVE", "detail": "Low affordability burden"}
                                                  ]
                                                }
                                                """)));

        CreditScoreResponse response =
                client()
                        .score(
                                new BigDecimal("200000"),
                                24,
                                new BigDecimal("150000"),
                                "EMPLOYED");

        assertThat(response.score()).isEqualTo(782);
        assertThat(response.modelVersion()).isEqualTo("deterministic-v1");
        assertThat(response.reasonContributions()).hasSize(1);
        assertThat(response.reasonContributions().get(0).factor())
                .isEqualTo("installmentToIncomeRatio");

        wireMock.verify(
                postRequestedFor(urlEqualTo("/score"))
                        .withRequestBody(
                                equalToJson(
                                        """
                                        {
                                          "requestedAmountKes": 200000,
                                          "requestedTermMonths": 24,
                                          "declaredMonthlyIncomeKes": 150000,
                                          "declaredEmploymentStatus": "EMPLOYED"
                                        }
                                        """,
                                        true,
                                        true)));
    }

    @Test
    void ignoresTheUnmappedBandField() {
        wireMock.stubFor(
                post(urlEqualTo("/score"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                                {"score": 300, "band": "POOR", "modelVersion": "v1", "reasonContributions": []}
                                                """)));

        CreditScoreResponse response =
                client().score(new BigDecimal("10000"), 6, new BigDecimal("20000"), "UNEMPLOYED");

        assertThat(response.score()).isEqualTo(300);
    }

    @Test
    void aNonSuccessStatusPropagatesAsAnException() {
        wireMock.stubFor(
                post(urlEqualTo("/score"))
                        .willReturn(aResponse().withStatus(500).withBody("internal error")));

        assertThatThrownBy(
                        () ->
                                client()
                                        .score(
                                                new BigDecimal("200000"),
                                                24,
                                                new BigDecimal("150000"),
                                                "EMPLOYED"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void aMalformedResponseBodyPropagatesAsAnException() {
        wireMock.stubFor(
                post(urlEqualTo("/score"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{ not valid json")));

        assertThatThrownBy(
                        () ->
                                client()
                                        .score(
                                                new BigDecimal("200000"),
                                                24,
                                                new BigDecimal("150000"),
                                                "EMPLOYED"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void aResponseSlowerThanTheConfiguredTimeoutPropagatesAsAnException() {
        wireMock.stubFor(
                post(urlEqualTo("/score"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withFixedDelay((int) TIMEOUT.toMillis() * 3)
                                        .withBody(
                                                """
                                                {"score": 700, "band": "GOOD", "modelVersion": "v1", "reasonContributions": []}
                                                """)));

        assertThatThrownBy(
                        () ->
                                client()
                                        .score(
                                                new BigDecimal("200000"),
                                                24,
                                                new BigDecimal("150000"),
                                                "EMPLOYED"))
                .isInstanceOf(Exception.class);
    }
}
