"""Request/response schemas. camelCase on the wire (Pydantic alias generator) so this
matches Java's Jackson defaults with no translation layer on the Java side — internal
Python attribute names stay snake_case.
"""

from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class _CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class EmploymentStatus(StrEnum):
    EMPLOYED = "EMPLOYED"
    SELF_EMPLOYED = "SELF_EMPLOYED"
    UNEMPLOYED = "UNEMPLOYED"


class ScoreRequest(_CamelModel):
    requested_amount_kes: float = Field(gt=0)
    requested_term_months: int = Field(gt=0)
    declared_monthly_income_kes: float = Field(gt=0)
    declared_employment_status: EmploymentStatus


class ReasonContribution(_CamelModel):
    factor: str
    value: str
    impact: str
    detail: str


class ScoreResponse(_CamelModel):
    score: int
    band: str
    model_version: str
    reason_contributions: list[ReasonContribution]
