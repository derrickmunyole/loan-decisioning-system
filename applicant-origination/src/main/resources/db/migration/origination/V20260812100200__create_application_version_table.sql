CREATE TABLE application_version (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES application (id),
    version_number INT NOT NULL,
    requested_amount_kes NUMERIC(12, 2) NOT NULL,
    requested_term_months INT NOT NULL,
    declared_monthly_income_kes NUMERIC(12, 2) NOT NULL,
    declared_employment_status VARCHAR(20) NOT NULL,
    declared_employer_name VARCHAR(200),
    loan_purpose VARCHAR(500),
    submitted_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX idx_application_version_app_number ON application_version (application_id, version_number);
