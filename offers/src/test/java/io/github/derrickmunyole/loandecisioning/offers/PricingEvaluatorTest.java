package io.github.derrickmunyole.loandecisioning.offers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class PricingEvaluatorTest {

    private static final Map<String, PricingEvaluator.Terms> TIERS =
            Map.of(
                    "APPROVED", new PricingEvaluator.Terms(1499, 36),
                    "CONDITIONAL_APPROVAL", new PricingEvaluator.Terms(1999, 24));

    @Test
    void approvedOutcomeGetsTheStandardTier() {
        PricingEvaluator.Terms terms = PricingEvaluator.evaluate("APPROVED", TIERS);

        assertThat(terms.aprBasisPoints()).isEqualTo(1499);
        assertThat(terms.termMonths()).isEqualTo(36);
    }

    @Test
    void conditionalApprovalGetsAStricterTier() {
        PricingEvaluator.Terms terms = PricingEvaluator.evaluate("CONDITIONAL_APPROVAL", TIERS);

        assertThat(terms.aprBasisPoints()).isEqualTo(1999);
        assertThat(terms.termMonths()).isEqualTo(24);
    }

    @Test
    void missingTierForTheOutcomeThrowsRatherThanGuessing() {
        assertThatThrownBy(() -> PricingEvaluator.evaluate("DECLINED", TIERS))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("DECLINED");
    }

    @Test
    void nullTiersMapThrowsTheSameCleanExceptionRatherThanNpeing() {
        assertThatThrownBy(() -> PricingEvaluator.evaluate("APPROVED", null))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("APPROVED");
    }
}
