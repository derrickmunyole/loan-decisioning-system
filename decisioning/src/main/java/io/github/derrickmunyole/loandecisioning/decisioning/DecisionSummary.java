package io.github.derrickmunyole.loandecisioning.decisioning;

import io.github.derrickmunyole.loandecisioning.decisioning.api.DecisionView;
import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The "decision version" and "reasons" pieces of blueprint §11's timeline sentence — full
 * traceability to the exact snapshot/policy/scorecard/pricing/credit-score-model version a
 * decision was computed from, per blueprint §5.
 */
record DecisionSummary(
        UUID decisionId,
        ApplicationStatus outcome,
        List<String> reasons,
        String actor,
        Instant decidedAt,
        UUID underwritingSnapshotId,
        UUID policyVersionId,
        UUID scorecardVersionId,
        UUID pricingVersionId,
        String creditScoreModelVersion,
        UUID overridesDecisionId) {

    static DecisionSummary from(DecisionView view) {
        return new DecisionSummary(
                view.id(),
                view.outcome(),
                view.reasons(),
                view.actor(),
                view.decidedAt(),
                view.underwritingSnapshotId(),
                view.policyVersionId(),
                view.scorecardVersionId(),
                view.pricingVersionId(),
                view.creditScoreModelVersion(),
                view.overridesDecisionId());
    }
}
