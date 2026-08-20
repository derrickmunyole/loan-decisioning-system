package io.github.derrickmunyole.loandecisioning.offers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AmortizationCalculatorTest {

    @Test
    void zeroAprSplitsPrincipalEvenlyAcrossTheTerm() {
        BigDecimal payment = AmortizationCalculator.monthlyPayment(BigDecimal.valueOf(120_000), 0, 24);

        assertThat(payment).isEqualByComparingTo("5000.00");
    }

    /**
     * Cross-checks the BigDecimal implementation against an independently computed {@code double}
     * approximation of the same closed-form formula — a different numeric path, not a literal
     * restatement of the implementation, so a broken exponent/divisor still fails this even though
     * both sides ultimately implement the textbook amortization formula.
     */
    @ParameterizedTest
    @CsvSource({
        "100000, 1499, 36",
        "250000, 1999, 24",
        "50000, 999, 12",
        "500000, 2500, 48"
    })
    void nonZeroAprMatchesTheStandardAmortizationFormula(String principal, int aprBasisPoints, int termMonths) {
        BigDecimal payment =
                AmortizationCalculator.monthlyPayment(new BigDecimal(principal), aprBasisPoints, termMonths);

        double monthlyRate = aprBasisPoints / 120_000.0;
        double factor = Math.pow(1 + monthlyRate, termMonths);
        double expected = Double.parseDouble(principal) * monthlyRate * factor / (factor - 1);

        assertThat(payment.doubleValue()).isCloseTo(expected, within(0.01));
    }

    @Test
    void higherAprProducesAHigherMonthlyPaymentForTheSamePrincipalAndTerm() {
        BigDecimal lowApr = AmortizationCalculator.monthlyPayment(BigDecimal.valueOf(100_000), 999, 36);
        BigDecimal highApr = AmortizationCalculator.monthlyPayment(BigDecimal.valueOf(100_000), 2499, 36);

        assertThat(highApr).isGreaterThan(lowApr);
    }

    @Test
    void resultIsRoundedToTheNearestCent() {
        BigDecimal payment = AmortizationCalculator.monthlyPayment(BigDecimal.valueOf(100_000), 1499, 36);

        assertThat(payment.scale()).isEqualTo(2);
        assertThat(payment.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo(payment);
    }
}