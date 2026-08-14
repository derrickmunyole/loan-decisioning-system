package io.github.derrickmunyole.loandecisioning.decisioning;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PolicyEvaluatorTest {

    private static final Map<String, Integer> BAND_CUTOFFS =
            Map.of(
                    "EXCELLENT", 800,
                    "VERY_GOOD", 740,
                    "GOOD", 670,
                    "FAIR", 580,
                    "POOR", 300);

    private static final Map<String, String> BAND_OUTCOMES =
            Map.of(
                    "EXCELLENT", "APPROVED",
                    "VERY_GOOD", "APPROVED",
                    "GOOD", "CONDITIONAL_APPROVAL",
                    "FAIR", "REFERRED",
                    "POOR", "DECLINED");

    private static final List<String> EXCLUDED_EMPLOYMENT_STATUSES = List.of("UNEMPLOYED");

    @ParameterizedTest
    @CsvSource({
        "850, EXCELLENT, APPROVED",
        "800, EXCELLENT, APPROVED",
        "799, VERY_GOOD, APPROVED",
        "740, VERY_GOOD, APPROVED",
        "739, GOOD, CONDITIONAL_APPROVAL",
        "670, GOOD, CONDITIONAL_APPROVAL",
        "669, FAIR, REFERRED",
        "580, FAIR, REFERRED",
        "579, POOR, DECLINED",
        "300, POOR, DECLINED"
    })
    void scoreMapsToTheExpectedBandAndOutcome(int score, String expectedBand, String expectedOutcome) {
        PolicyEvaluationResult result =
                PolicyEvaluator.evaluate(
                        score, "EMPLOYED", BAND_CUTOFFS, EXCLUDED_EMPLOYMENT_STATUSES, BAND_OUTCOMES);

        assertThat(result.band()).isEqualTo(expectedBand);
        assertThat(result.outcome()).isEqualTo(ApplicationStatus.valueOf(expectedOutcome));
    }

    @Test
    void excludedEmploymentStatusOverridesAnEvenExcellentBand() {
        PolicyEvaluationResult result =
                PolicyEvaluator.evaluate(
                        850, "UNEMPLOYED", BAND_CUTOFFS, EXCLUDED_EMPLOYMENT_STATUSES, BAND_OUTCOMES);

        assertThat(result.outcome()).isEqualTo(ApplicationStatus.DECLINED);
        assertThat(result.band()).isEqualTo("EXCELLENT");
        assertThat(result.reasons()).anySatisfy(r -> assertThat(r).contains("excluded by policy"));
    }

    @Test
    void bandWithNoMappedOutcomeFallsBackToReferred() {
        Map<String, String> incompleteOutcomes = Map.of("EXCELLENT", "APPROVED");

        PolicyEvaluationResult result =
                PolicyEvaluator.evaluate(
                        0, "EMPLOYED", BAND_CUTOFFS, EXCLUDED_EMPLOYMENT_STATUSES, incompleteOutcomes);

        assertThat(result.band()).isEqualTo("POOR");
        assertThat(result.outcome()).isEqualTo(ApplicationStatus.REFERRED);
    }

    @Test
    void unrecognizedOutcomeStringFallsBackToReferredRatherThanThrowing() {
        Map<String, String> malformedOutcomes = Map.of("EXCELLENT", "NOT_A_REAL_STATUS");

        PolicyEvaluationResult result =
                PolicyEvaluator.evaluate(
                        850, "EMPLOYED", BAND_CUTOFFS, EXCLUDED_EMPLOYMENT_STATUSES, malformedOutcomes);

        assertThat(result.outcome()).isEqualTo(ApplicationStatus.REFERRED);
    }

    @Test
    void scoreBelowTheLowestCutoffStillLandsInTheLowestBand() {
        PolicyEvaluationResult result =
                PolicyEvaluator.evaluate(
                        0, "EMPLOYED", BAND_CUTOFFS, EXCLUDED_EMPLOYMENT_STATUSES, BAND_OUTCOMES);

        assertThat(result.band()).isEqualTo("POOR");
        assertThat(result.outcome()).isEqualTo(ApplicationStatus.DECLINED);
    }
}
