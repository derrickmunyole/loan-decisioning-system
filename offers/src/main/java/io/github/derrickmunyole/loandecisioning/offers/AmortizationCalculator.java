package io.github.derrickmunyole.loandecisioning.offers;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Pure, deterministic fixed-rate monthly payment calculation — the standard amortization formula
 * {@code M = P * r * (1+r)^n / ((1+r)^n - 1)}, {@code r} being the monthly rate. Framework-free
 * like {@link PricingEvaluator}, table-tested the same way.
 */
final class AmortizationCalculator {

    private static final MathContext PRECISION = MathContext.DECIMAL64;
    private static final BigDecimal BASIS_POINTS_PER_MONTHLY_RATE = BigDecimal.valueOf(120_000);

    private AmortizationCalculator() {}

    static BigDecimal monthlyPayment(BigDecimal principal, int aprBasisPoints, int termMonths) {
        if (aprBasisPoints == 0) {
            return principal.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);
        }

        BigDecimal monthlyRate =
                BigDecimal.valueOf(aprBasisPoints).divide(BASIS_POINTS_PER_MONTHLY_RATE, PRECISION);
        BigDecimal onePlusRToTheN = BigDecimal.ONE.add(monthlyRate).pow(termMonths, PRECISION);
        BigDecimal numerator = principal.multiply(monthlyRate, PRECISION).multiply(onePlusRToTheN, PRECISION);
        BigDecimal denominator = onePlusRToTheN.subtract(BigDecimal.ONE);
        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }
}
