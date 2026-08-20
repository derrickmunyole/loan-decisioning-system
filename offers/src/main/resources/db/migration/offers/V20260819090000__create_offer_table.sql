CREATE TABLE offer (
    id UUID PRIMARY KEY,
    decision_id UUID NOT NULL UNIQUE,
    application_id UUID NOT NULL,
    principal_kes NUMERIC(12, 2) NOT NULL,
    apr_basis_points INT NOT NULL,
    term_months INT NOT NULL,
    monthly_payment_kes NUMERIC(12, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_offer_application_id ON offer (application_id);
CREATE INDEX idx_offer_status_expires_at ON offer (status, expires_at);
