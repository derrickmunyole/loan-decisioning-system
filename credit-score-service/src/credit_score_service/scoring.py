"""Deterministic, explainable weighted-formula credit scorer.

Framework-free on purpose (blueprint: "the first scorecard is deterministic and
explainable, not ML") — this module has no FastAPI/Pydantic dependency so the formula
itself is directly table-testable, mirroring the roadmap's "policy/scorecard evaluation
as pure, table-tested functions" testing strategy (A.14).

The installment-to-income ratio factor deliberately reuses the same affordability signal
Java's ``SyntheticVerificationEngine`` already established (its own 40%-of-income
threshold) rather than inventing an unrelated one — a ratio of 40% lands solidly in the
lower half of this scorer's range too, so the two synthetic signals stay thematically
consistent even though they're independent checks (verification vs. credit scoring).
"""

from dataclasses import dataclass

MODEL_VERSION = "credit-score-v1"

MIN_SCORE = 300
MAX_SCORE = 850
_SCORE_RANGE = MAX_SCORE - MIN_SCORE

# Ratio at or above this is treated as maximally bad (goodness 0); 0% ratio is maximally
# good (goodness 1). Linear in between.
_RATIO_GOODNESS_FLOOR_RATIO = 0.5

_INSTALLMENT_RATIO_WEIGHT = 0.70
_EMPLOYMENT_WEIGHT = 0.30

_EMPLOYMENT_GOODNESS = {
    "EMPLOYED": 1.0,
    "SELF_EMPLOYED": 0.6,
    "UNEMPLOYED": 0.0,
}

_BAND_FLOORS = (
    (800, "EXCELLENT"),
    (740, "VERY_GOOD"),
    (670, "GOOD"),
    (580, "FAIR"),
    (MIN_SCORE, "POOR"),
)


@dataclass(frozen=True)
class ReasonContribution:
    factor: str
    value: str
    impact: str
    detail: str


@dataclass(frozen=True)
class ScoreResult:
    score: int
    band: str
    model_version: str
    reason_contributions: list[ReasonContribution]


def compute_score(
    requested_amount_kes: float,
    requested_term_months: int,
    declared_monthly_income_kes: float,
    declared_employment_status: str,
) -> ScoreResult:
    if declared_employment_status not in _EMPLOYMENT_GOODNESS:
        raise ValueError(f"Unknown declared_employment_status: {declared_employment_status}")

    monthly_installment = requested_amount_kes / requested_term_months
    installment_to_income_ratio = monthly_installment / declared_monthly_income_kes

    ratio_goodness = _clamp(1 - (installment_to_income_ratio / _RATIO_GOODNESS_FLOOR_RATIO))
    employment_goodness = _EMPLOYMENT_GOODNESS[declared_employment_status]

    weighted_goodness = (
        _INSTALLMENT_RATIO_WEIGHT * ratio_goodness + _EMPLOYMENT_WEIGHT * employment_goodness
    )
    score = round(MIN_SCORE + _SCORE_RANGE * weighted_goodness)

    return ScoreResult(
        score=score,
        band=_band_for(score),
        model_version=MODEL_VERSION,
        reason_contributions=[
            _installment_ratio_reason(installment_to_income_ratio, ratio_goodness),
            _employment_reason(declared_employment_status, employment_goodness),
        ],
    )


def _installment_ratio_reason(ratio: float, goodness: float) -> ReasonContribution:
    return ReasonContribution(
        factor="installmentToIncomeRatio",
        value=f"{ratio:.4f}",
        impact="POSITIVE" if goodness >= 0.5 else "NEGATIVE",
        detail=f"Estimated monthly installment is {ratio:.0%} of declared monthly income",
    )


def _employment_reason(status: str, goodness: float) -> ReasonContribution:
    return ReasonContribution(
        factor="employmentStatus",
        value=status,
        impact="POSITIVE" if goodness >= 0.5 else "NEGATIVE",
        detail=f"Declared employment status: {status}",
    )


def _band_for(score: int) -> str:
    for floor, band in _BAND_FLOORS:
        if score >= floor:
            return band
    return "POOR"


def _clamp(value: float, lower: float = 0.0, upper: float = 1.0) -> float:
    return max(lower, min(upper, value))
