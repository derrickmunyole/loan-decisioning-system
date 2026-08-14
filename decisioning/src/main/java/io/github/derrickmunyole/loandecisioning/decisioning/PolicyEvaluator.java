package io.github.derrickmunyole.loandecisioning.decisioning;

import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure, deterministic band/outcome evaluation — framework-free like {@code
 * credit-score-service}'s own {@code scoring.py}, so the roadmap's "policy/scorecard evaluation
 * as pure, table-tested functions" testing strategy applies directly.
 *
 * <p>Two separately publishable configs, kept genuinely non-redundant: {@code
 * ScorecardVersion.formulaConfig}'s {@code bandCutoffs} turns the credit-score-service's raw
 * numeric score into a band (the org's own risk sensitivity, retunable without a Python deploy);
 * {@code PolicyVersion.rules}'s {@code bandOutcomes} turns that band into one of the four decision
 * outcomes, plus a hard {@code excludedEmploymentStatuses} eligibility gate that overrides the
 * band mapping entirely. {@code PricingVersion} is deliberately not an input here — captured on
 * {@code Decision} for traceability only, per the blueprint's "separate risk eligibility from
 * pricing."
 */
final class PolicyEvaluator {

    private PolicyEvaluator() {}

    static PolicyEvaluationResult evaluate(
            int score,
            String declaredEmploymentStatus,
            Map<String, Integer> bandCutoffs,
            List<String> excludedEmploymentStatuses,
            Map<String, String> bandOutcomes) {
        List<String> reasons = new ArrayList<>();

        String band = bandFor(score, bandCutoffs);
        reasons.add("Credit score " + score + " maps to band " + band);

        if (excludedEmploymentStatuses != null && excludedEmploymentStatuses.contains(declaredEmploymentStatus)) {
            reasons.add(
                    "Declared employment status " + declaredEmploymentStatus + " is excluded by policy");
            return new PolicyEvaluationResult(ApplicationStatus.DECLINED, band, reasons);
        }

        String outcomeName = bandOutcomes.get(band);
        if (outcomeName == null) {
            reasons.add("Policy has no outcome mapped for band " + band + "; referring for manual review");
            return new PolicyEvaluationResult(ApplicationStatus.REFERRED, band, reasons);
        }

        ApplicationStatus outcome;
        try {
            outcome = ApplicationStatus.valueOf(outcomeName);
        } catch (IllegalArgumentException e) {
            reasons.add(
                    "Policy's outcome for band "
                            + band
                            + " ('"
                            + outcomeName
                            + "') is not a recognized status; referring for manual review");
            return new PolicyEvaluationResult(ApplicationStatus.REFERRED, band, reasons);
        }

        reasons.add("Policy maps band " + band + " to " + outcome);
        return new PolicyEvaluationResult(outcome, band, reasons);
    }

    /** Highest band whose cutoff the score meets or exceeds; falls back to the lowest cutoff's band. */
    private static String bandFor(int score, Map<String, Integer> bandCutoffs) {
        return bandCutoffs.entrySet().stream()
                .filter(entry -> score >= entry.getValue())
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseGet(
                        () ->
                                bandCutoffs.entrySet().stream()
                                        .min(Map.Entry.comparingByValue())
                                        .map(Map.Entry::getKey)
                                        .orElseThrow(
                                                () ->
                                                        new IllegalStateException(
                                                                "ScorecardVersion has no bandCutoffs configured")));
    }
}
