CREATE TABLE verification_case (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    application_version_id UUID NOT NULL,
    provider VARCHAR(40) NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    detail TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_verification_case_application_id ON verification_case (application_id);

CREATE TABLE verification_attempt_marker (
    application_id UUID PRIMARY KEY,
    attempt_count INT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
