from synthetic_data_generator.fake_data import (
    ALLOWED_TERM_MONTHS,
    EMPLOYMENT_STATUSES,
    MAX_REQUESTED_AMOUNT_KES,
    MIN_REQUESTED_AMOUNT_KES,
    fake_applicant_profile,
    fake_document,
    fake_draft_fields,
)


def test_draft_fields_stay_within_api_bounds():
    for _ in range(200):
        draft = fake_draft_fields()

        amount = int(draft.requested_amount_kes)
        assert MIN_REQUESTED_AMOUNT_KES <= amount <= MAX_REQUESTED_AMOUNT_KES
        assert draft.requested_term_months in ALLOWED_TERM_MONTHS
        assert int(draft.declared_monthly_income_kes) >= 0
        assert draft.declared_employment_status in EMPLOYMENT_STATUSES
        assert len(draft.loan_purpose) <= 500
        assert len(draft.declared_employer_name) <= 200


def test_unemployed_applicants_have_no_employer_name():
    for _ in range(200):
        draft = fake_draft_fields()
        if draft.declared_employment_status == "UNEMPLOYED":
            assert draft.declared_employer_name == ""


def test_applicant_profile_stays_within_api_bounds():
    profile = fake_applicant_profile()
    assert len(profile.full_name) <= 200
    assert len(profile.email) <= 200
    assert len(profile.phone) <= 30


def test_document_has_a_recognized_type():
    document = fake_document()
    assert document.document_type in ("ID_DOCUMENT", "PROOF_OF_INCOME", "PROOF_OF_ADDRESS")
    assert document.content
