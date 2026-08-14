CREATE TABLE decision (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    underwriting_snapshot_id UUID NOT NULL,
    policy_version_id UUID NOT NULL,
    scorecard_version_id UUID NOT NULL,
    pricing_version_id UUID NOT NULL,
    credit_score_model_version VARCHAR(100),
    outcome VARCHAR(30) NOT NULL,
    reason_codes_json JSONB NOT NULL,
    actor VARCHAR(50) NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_decision_application_id ON decision (application_id);
