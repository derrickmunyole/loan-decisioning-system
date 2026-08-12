CREATE TABLE consent (
    id UUID PRIMARY KEY,
    application_version_id UUID NOT NULL REFERENCES application_version (id),
    consent_type VARCHAR(30) NOT NULL,
    consent_version VARCHAR(20) NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_consent_application_version_id ON consent (application_version_id);
