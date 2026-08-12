CREATE TABLE application (
    id UUID PRIMARY KEY,
    applicant_id UUID NOT NULL REFERENCES applicant (id),
    status VARCHAR(20) NOT NULL,
    current_version_number INT,
    requested_amount_kes NUMERIC(12, 2),
    requested_term_months INT,
    declared_monthly_income_kes NUMERIC(12, 2),
    declared_employment_status VARCHAR(20),
    declared_employer_name VARCHAR(200),
    loan_purpose VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_application_applicant_id ON application (applicant_id);
