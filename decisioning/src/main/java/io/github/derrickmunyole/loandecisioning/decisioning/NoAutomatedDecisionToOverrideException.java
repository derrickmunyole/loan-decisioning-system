package io.github.derrickmunyole.loandecisioning.decisioning;

import java.util.UUID;

/**
 * A {@code REFERRED} application can reach that status two ways (see {@link
 * DecisionEngineHandler}): a failed verification check, which always records an automated {@link
 * Decision} row, or a credit-score provider outage, which deliberately records none since there's
 * no valid score/model version to attach one to. An {@code APPROVED}/{@code DECLINED} override
 * needs a prior {@code Decision} to inherit its snapshot/policy/scorecard/pricing versions from
 * and to set {@code overridesDecisionId} — thrown when there isn't one to inherit from, i.e. the
 * provider-outage path. That case is resolved via its own {@code CREDIT_SCORE_PROVIDER_UNAVAILABLE}
 * {@code workflow_task} (Epic 4.2), not this endpoint.
 */
class NoAutomatedDecisionToOverrideException extends RuntimeException {

    NoAutomatedDecisionToOverrideException(UUID applicationId) {
        super(
                "Application "
                        + applicationId
                        + " is REFERRED but has no automated Decision to override — likely a"
                        + " credit-score provider outage case, resolved via its own workflow_task"
                        + " rather than this endpoint");
    }
}
