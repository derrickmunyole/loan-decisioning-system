"""Generates field values that satisfy the origination API's validation constraints.

Bounds here must track applicant-origination's request DTOs (`CreateApplicationRequest`,
`PatchApplicationRequest`) — see the paired unit tests.
"""

from __future__ import annotations

import random
from dataclasses import dataclass

from faker import Faker

from synthetic_data_generator.api_client import ApplicantProfile, DraftFields

MIN_REQUESTED_AMOUNT_KES = 50_000
MAX_REQUESTED_AMOUNT_KES = 1_250_000
ALLOWED_TERM_MONTHS = (12, 24, 36, 48)
EMPLOYMENT_STATUSES = ("EMPLOYED", "SELF_EMPLOYED", "UNEMPLOYED")
DOCUMENT_TYPES = ("ID_DOCUMENT", "PROOF_OF_INCOME", "PROOF_OF_ADDRESS")

_faker = Faker()


@dataclass
class SyntheticDocument:
    document_type: str
    filename: str
    content: bytes


def fake_applicant_profile() -> ApplicantProfile:
    return ApplicantProfile(
        full_name=_faker.name(),
        email=_faker.unique.email(),
        phone=_faker.phone_number()[:30],
    )


def fake_draft_fields() -> DraftFields:
    employment_status = random.choice(EMPLOYMENT_STATUSES)
    return DraftFields(
        requested_amount_kes=str(
            random.randint(MIN_REQUESTED_AMOUNT_KES, MAX_REQUESTED_AMOUNT_KES)
        ),
        requested_term_months=random.choice(ALLOWED_TERM_MONTHS),
        declared_monthly_income_kes=str(random.randint(0, 500_000)),
        declared_employment_status=employment_status,
        declared_employer_name=(
            "" if employment_status == "UNEMPLOYED" else _faker.company()
        ),
        loan_purpose=_faker.sentence(nb_words=6)[:500],
    )


def fake_document() -> SyntheticDocument:
    document_type = random.choice(DOCUMENT_TYPES)
    content = f"Synthetic {document_type} for demo purposes.\n{_faker.text()}".encode()
    return SyntheticDocument(
        document_type=document_type,
        filename=f"{document_type.lower()}.txt",
        content=content,
    )
