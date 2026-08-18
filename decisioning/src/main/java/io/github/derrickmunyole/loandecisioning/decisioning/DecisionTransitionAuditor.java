package io.github.derrickmunyole.loandecisioning.decisioning;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.Audited;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Closes the same NFR gap {@code verification}'s {@code VerificationTransitionAuditor} closes,
 * for {@link DecisionEngineHandler}'s two automated {@code UNDERWRITING} exits: a normal decision
 * (any of the four outcomes, via {@code recordDecision}) and a credit-score-provider-outage
 * referral (no {@link Decision} row at all). Split into its own bean for the same reason —
 * {@code @Audited} is proxy-woven AOP, and {@code DecisionEngineHandler} would silently never
 * audit if it called an annotated method on itself.
 */
@Service
class DecisionTransitionAuditor {

    @Audited(
            action = "AUTOMATED_DECISION_RECORDED",
            targetType = "Application",
            targetId = "#applicationId")
    void recordDecisionOutcome(UUID applicationId) {}

    @Audited(
            action = "CREDIT_SCORE_PROVIDER_OUTAGE_REFERRED",
            targetType = "Application",
            targetId = "#applicationId")
    void recordProviderOutageReferral(UUID applicationId) {}
}
