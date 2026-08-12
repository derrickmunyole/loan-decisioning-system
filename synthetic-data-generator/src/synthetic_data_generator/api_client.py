"""Thin wrapper over the platform's REST API — no business logic, just HTTP calls."""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from typing import Any

import requests


class ApiError(RuntimeError):
    def __init__(self, method: str, url: str, response: requests.Response):
        super().__init__(f"{method} {url} -> {response.status_code}: {response.text}")
        self.status_code = response.status_code


@dataclass
class ApplicantProfile:
    full_name: str
    email: str
    phone: str


@dataclass
class DraftFields:
    requested_amount_kes: str
    requested_term_months: int
    declared_monthly_income_kes: str
    declared_employment_status: str
    declared_employer_name: str
    loan_purpose: str


class ApiClient:
    def __init__(self, base_url: str, timeout_seconds: float = 10.0):
        self._base_url = base_url.rstrip("/")
        self._timeout = timeout_seconds
        self._session = requests.Session()

    def login(self, username: str, password: str) -> str:
        response = self._session.post(
            f"{self._base_url}/auth/login",
            json={"username": username, "password": password},
            timeout=self._timeout,
        )
        self._raise_for_status("POST", "/auth/login", response)
        token = response.json()["token"]
        self._session.headers["Authorization"] = f"Bearer {token}"
        return token

    def create_application(self, profile: ApplicantProfile) -> dict[str, Any]:
        response = self._session.post(
            f"{self._base_url}/applications",
            headers={"Idempotency-Key": str(uuid.uuid4())},
            json={
                "fullName": profile.full_name,
                "email": profile.email,
                "phone": profile.phone,
            },
            timeout=self._timeout,
        )
        self._raise_for_status("POST", "/applications", response)
        return response.json()

    def update_draft(self, application_id: str, draft: DraftFields) -> dict[str, Any]:
        response = self._session.patch(
            f"{self._base_url}/applications/{application_id}",
            json={
                "requestedAmountKes": draft.requested_amount_kes,
                "requestedTermMonths": draft.requested_term_months,
                "declaredMonthlyIncomeKes": draft.declared_monthly_income_kes,
                "declaredEmploymentStatus": draft.declared_employment_status,
                "declaredEmployerName": draft.declared_employer_name,
                "loanPurpose": draft.loan_purpose,
            },
            timeout=self._timeout,
        )
        self._raise_for_status("PATCH", f"/applications/{application_id}", response)
        return response.json()

    def upload_document(
        self, application_id: str, document_type: str, filename: str, content: bytes
    ) -> dict[str, Any]:
        response = self._session.post(
            f"{self._base_url}/applications/{application_id}/documents",
            params={"documentType": document_type},
            files={"file": (filename, content, "application/octet-stream")},
            timeout=self._timeout,
        )
        self._raise_for_status("POST", f"/applications/{application_id}/documents", response)
        return response.json()

    def submit_application(self, application_id: str) -> dict[str, Any]:
        response = self._session.post(
            f"{self._base_url}/applications/{application_id}/submit",
            headers={"Idempotency-Key": str(uuid.uuid4())},
            json={"consentAccepted": True},
            timeout=self._timeout,
        )
        self._raise_for_status("POST", f"/applications/{application_id}/submit", response)
        return response.json()

    @staticmethod
    def _raise_for_status(method: str, path: str, response: requests.Response) -> None:
        if not response.ok:
            raise ApiError(method, path, response)
