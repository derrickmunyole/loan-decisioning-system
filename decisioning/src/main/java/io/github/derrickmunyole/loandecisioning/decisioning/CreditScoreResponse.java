package io.github.derrickmunyole.loandecisioning.decisioning;

import java.util.List;

/**
 * The subset of {@code credit-score-service}'s {@code POST /score} response this client cares
 * about. Deliberately doesn't map {@code band} — this side derives its own band from {@code
 * score} via the active {@link ScorecardVersion}'s cutoffs, not the provider's embedded one, so
 * risk sensitivity can be retuned by publishing a new {@code ScorecardVersion} without a Python
 * deploy. Spring Boot's default Jackson config ignores the unmapped field.
 */
record CreditScoreResponse(int score, String modelVersion, List<ReasonContribution> reasonContributions) {

    record ReasonContribution(String factor, String value, String impact, String detail) {}
}
