CREATE TABLE underwriting_snapshot (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    application_version_id UUID NOT NULL,
    facts_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_underwriting_snapshot_application_version UNIQUE (application_version_id)
);

CREATE INDEX idx_underwriting_snapshot_application_id ON underwriting_snapshot (application_id);
