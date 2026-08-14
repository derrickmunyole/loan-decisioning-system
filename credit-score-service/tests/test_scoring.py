import pytest

from credit_score_service.scoring import MAX_SCORE, MIN_SCORE, MODEL_VERSION, compute_score

# Each case's score/band was computed from the function itself and pinned here as the
# regression baseline — the formula's constants (weights, ratio floor, band cutoffs) are
# what these are actually protecting; hand-deriving the exact floats independently would
# just re-implement the same arithmetic twice.
BAND_CASES = [
    (12000, 12, 100000, "EMPLOYED", 842, "EXCELLENT"),
    (300000, 24, 100000, "EMPLOYED", 754, "VERY_GOOD"),
    (300000, 24, 80000, "EMPLOYED", 730, "GOOD"),
    (360000, 12, 100000, "EMPLOYED", 619, "FAIR"),
    (300000, 12, 60000, "SELF_EMPLOYED", 463, "POOR"),
    (600000, 12, 100000, "UNEMPLOYED", 300, "POOR"),
]


@pytest.mark.parametrize("amount,term,income,employment,expected_score,expected_band", BAND_CASES)
def test_score_and_band_are_deterministic(
    amount, term, income, employment, expected_score, expected_band
):
    result = compute_score(amount, term, income, employment)
    assert result.score == expected_score
    assert result.band == expected_band


def test_score_is_deterministic_across_repeated_calls():
    args = (300000, 24, 80000, "EMPLOYED")
    assert compute_score(*args) == compute_score(*args)


def test_score_is_always_within_the_documented_range():
    for amount, term, income, employment, *_ in BAND_CASES:
        result = compute_score(amount, term, income, employment)
        assert MIN_SCORE <= result.score <= MAX_SCORE


def test_model_version_is_reported():
    result = compute_score(300000, 24, 80000, "EMPLOYED")
    assert result.model_version == MODEL_VERSION


def test_two_reason_contributions_are_always_returned():
    result = compute_score(300000, 24, 80000, "EMPLOYED")
    factors = {r.factor for r in result.reason_contributions}
    assert factors == {"installmentToIncomeRatio", "employmentStatus"}


def test_worse_affordability_never_produces_a_higher_score_all_else_equal():
    better = compute_score(100000, 24, 100000, "EMPLOYED")
    worse = compute_score(400000, 24, 100000, "EMPLOYED")
    assert worse.score < better.score


def test_unknown_employment_status_is_rejected():
    with pytest.raises(ValueError):
        compute_score(300000, 24, 80000, "RETIRED")
