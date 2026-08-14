package io.github.derrickmunyole.loandecisioning.decisioning;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * First outbound HTTP call in this codebase — see ADR 0008. {@code @CircuitBreaker} covers
 * repeated failures; {@code .block(timeout)} covers a single call hanging past the configured
 * budget (Reactor's own blocking-read timeout, not resilience4j's {@code TimeLimiter} — that
 * annotation is for reactive/async return types, and this call is deliberately synchronous per
 * the roadmap's "Synchronous WebClient call"). Any exception from either — timeout, non-2xx,
 * connection failure, or the breaker itself being open — is the caller's job to interpret; this
 * class doesn't catch anything; it only decorates.
 */
@Component
class CreditScoreClient {

    private final WebClient webClient;
    private final Duration timeout;

    CreditScoreClient(
            WebClient creditScoreWebClient,
            @Value("${app.credit-score.timeout:PT3S}") Duration timeout) {
        this.webClient = creditScoreWebClient;
        this.timeout = timeout;
    }

    @CircuitBreaker(name = "creditScore")
    CreditScoreResponse score(
            BigDecimal requestedAmountKes,
            int requestedTermMonths,
            BigDecimal declaredMonthlyIncomeKes,
            String declaredEmploymentStatus) {
        Map<String, Object> request =
                Map.of(
                        "requestedAmountKes", requestedAmountKes,
                        "requestedTermMonths", requestedTermMonths,
                        "declaredMonthlyIncomeKes", declaredMonthlyIncomeKes,
                        "declaredEmploymentStatus", declaredEmploymentStatus);
        return webClient
                .post()
                .uri("/score")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(CreditScoreResponse.class)
                .block(timeout);
    }
}
