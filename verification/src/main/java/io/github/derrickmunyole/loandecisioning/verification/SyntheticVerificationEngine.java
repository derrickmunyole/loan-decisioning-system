package io.github.derrickmunyole.loandecisioning.verification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Deterministic, in-process stand-in for a real identity/income provider (blueprint §5's
 * {@code verification_case.provider}). There's no real external data source to check declared
 * fields against, so two sentinel values on {@code declaredEmployerName} act as this epic's
 * synthetic input-shape signals — the same convention the roadmap earmarks for the generator's
 * future {@code --scenario} flags (A.10), just hand-triggered until that lands:
 *
 * <ul>
 *   <li>{@link #TRANSIENT_FAILURE_TRIGGER} — simulates a flaky provider: the first processing
 *       attempt fails, the redelivered attempt succeeds normally.
 *   <li>{@link #IDENTITY_MISMATCH_TRIGGER} — simulates a genuine identity mismatch: the {@code
 *       IDENTITY} check's business outcome is {@code FAILED} (not a technical failure — the
 *       application still proceeds to {@code UNDERWRITING} for a human to review).
 * </ul>
 */
@Component
class SyntheticVerificationEngine {

    static final String PROVIDER = "SYNTHETIC_MOCK";
    static final String TRANSIENT_FAILURE_TRIGGER = "SYNTHETIC_TRANSIENT_FAILURE";
    static final String IDENTITY_MISMATCH_TRIGGER = "SYNTHETIC_IDENTITY_MISMATCH";

    private static final BigDecimal MAX_INSTALLMENT_TO_INCOME_RATIO = new BigDecimal("0.40");

    boolean isTransientFailureTrigger(String declaredEmployerName) {
        return TRANSIENT_FAILURE_TRIGGER.equals(declaredEmployerName);
    }

    VerificationOutcome checkIdentity(String declaredEmployerName) {
        if (IDENTITY_MISMATCH_TRIGGER.equals(declaredEmployerName)) {
            return new VerificationOutcome(
                    VerificationStatus.FAILED,
                    "Declared employer name matched the synthetic identity-mismatch trigger");
        }
        return new VerificationOutcome(VerificationStatus.PASSED, "Identity check passed");
    }

    VerificationOutcome checkIncome(
            BigDecimal requestedAmountKes, int requestedTermMonths, BigDecimal declaredMonthlyIncomeKes) {
        BigDecimal monthlyInstallment =
                requestedAmountKes.divide(BigDecimal.valueOf(requestedTermMonths), 2, RoundingMode.HALF_UP);
        BigDecimal maxAffordableInstallment =
                declaredMonthlyIncomeKes.multiply(MAX_INSTALLMENT_TO_INCOME_RATIO);
        if (monthlyInstallment.compareTo(maxAffordableInstallment) > 0) {
            return new VerificationOutcome(
                    VerificationStatus.FAILED,
                    "Estimated monthly installment "
                            + monthlyInstallment
                            + " exceeds 40% of declared monthly income ("
                            + maxAffordableInstallment
                            + ")");
        }
        return new VerificationOutcome(VerificationStatus.PASSED, "Income check passed");
    }
}
