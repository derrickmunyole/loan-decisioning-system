from fastapi import FastAPI

from credit_score_service.models import ReasonContribution, ScoreRequest, ScoreResponse
from credit_score_service.scoring import compute_score

app = FastAPI(
    title="Credit Score Service",
    description=(
        "Synthetic external credit bureau — a deterministic, explainable weighted-formula "
        "scorer. Not the lending decision itself; that stays in the Java decision engine."
    ),
    version="1.0.0",
)


@app.post("/score", response_model=ScoreResponse)
def score(request: ScoreRequest) -> ScoreResponse:
    result = compute_score(
        requested_amount_kes=request.requested_amount_kes,
        requested_term_months=request.requested_term_months,
        declared_monthly_income_kes=request.declared_monthly_income_kes,
        declared_employment_status=request.declared_employment_status.value,
    )
    return ScoreResponse(
        score=result.score,
        band=result.band,
        model_version=result.model_version,
        reason_contributions=[
            ReasonContribution(factor=r.factor, value=r.value, impact=r.impact, detail=r.detail)
            for r in result.reason_contributions
        ],
    )
