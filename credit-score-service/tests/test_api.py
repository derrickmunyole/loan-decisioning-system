from fastapi.testclient import TestClient

from credit_score_service.main import app

client = TestClient(app)


def test_score_endpoint_returns_camel_case_contract():
    response = client.post(
        "/score",
        json={
            "requestedAmountKes": 300000,
            "requestedTermMonths": 24,
            "declaredMonthlyIncomeKes": 80000,
            "declaredEmploymentStatus": "EMPLOYED",
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert set(body.keys()) == {"score", "band", "modelVersion", "reasonContributions"}
    assert isinstance(body["score"], int)
    assert body["band"] in {"POOR", "FAIR", "GOOD", "VERY_GOOD", "EXCELLENT"}
    assert len(body["reasonContributions"]) == 2
    for reason in body["reasonContributions"]:
        assert set(reason.keys()) == {"factor", "value", "impact", "detail"}


def test_score_endpoint_rejects_non_positive_amount():
    response = client.post(
        "/score",
        json={
            "requestedAmountKes": 0,
            "requestedTermMonths": 24,
            "declaredMonthlyIncomeKes": 80000,
            "declaredEmploymentStatus": "EMPLOYED",
        },
    )
    assert response.status_code == 422


def test_score_endpoint_rejects_unknown_employment_status():
    response = client.post(
        "/score",
        json={
            "requestedAmountKes": 300000,
            "requestedTermMonths": 24,
            "declaredMonthlyIncomeKes": 80000,
            "declaredEmploymentStatus": "RETIRED",
        },
    )
    assert response.status_code == 422
