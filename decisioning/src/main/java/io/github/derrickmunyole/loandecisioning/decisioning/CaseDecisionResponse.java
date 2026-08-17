package io.github.derrickmunyole.loandecisioning.decisioning;

import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code decisionId}/{@code overridesDecisionId} are {@code null} for an {@code UNDERWRITING}
 * outcome ("send back for more evidence") — that action doesn't create a {@link Decision} row,
 * since {@code UNDERWRITING} isn't one of blueprint §1's four decision outcomes.
 */
record CaseDecisionResponse(
        UUID applicationId,
        ApplicationStatus outcome,
        String reason,
        String actor,
        UUID decisionId,
        UUID overridesDecisionId,
        Instant decidedAt) {

    static CaseDecisionResponse fromOverride(Decision decision, String reason) {
        return new CaseDecisionResponse(
                decision.getApplicationId(),
                decision.getOutcome(),
                reason,
                decision.getActor(),
                decision.getId(),
                decision.getOverridesDecisionId(),
                decision.getDecidedAt());
    }

    static CaseDecisionResponse evidenceRequested(UUID applicationId, String reason, String actor) {
        return new CaseDecisionResponse(
                applicationId, ApplicationStatus.UNDERWRITING, reason, actor, null, null, Instant.now());
    }
}
