CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    actor VARCHAR(150) NOT NULL,
    action VARCHAR(150) NOT NULL,
    target_type VARCHAR(100) NOT NULL,
    target_id VARCHAR(150) NOT NULL,
    correlation_id VARCHAR(100),
    occurred_at TIMESTAMPTZ NOT NULL
);
