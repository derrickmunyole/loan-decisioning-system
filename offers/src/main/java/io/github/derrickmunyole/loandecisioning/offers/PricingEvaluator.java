package io.github.derrickmunyole.loandecisioning.offers;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Pure, deterministic tier lookup — framework-free like {@code decisioning}'s {@code
 * PolicyEvaluator}, so the roadmap's "policy/scorecard evaluation as pure, table-tested functions"
 * testing strategy applies here too.
 *
 * <p>Tiered by the {@code Decision}'s own outcome ({@code APPROVED} vs {@code
 * CONDITIONAL_APPROVAL}) rather than by credit-score band: {@code offers}'s listener already has
 * the outcome in the {@code decision.created} payload, so keying off it needs no new plumbing to
 * carry a score/band across the module boundary. A missing tier for an outcome the listener is
 * actually pricing is treated as a platform-configuration gap — same category as "no published
 * PolicyVersion" in {@code DecisionEngineHandler} — and left to throw into the generic
 * retry/DLQ/{@code workflow_task} path rather than inventing a fallback rate.
 */
final class PricingEvaluator {

    private PricingEvaluator() {}

    record Terms(int aprBasisPoints, int termMonths) {}

    static Terms evaluate(String outcome, Map<String, Terms> tiers) {
        Terms terms = tiers.get(outcome);
        if (terms == null) {
            throw new NoSuchElementException("PricingVersion has no tier configured for outcome " + outcome);
        }
        return terms;
    }
}
