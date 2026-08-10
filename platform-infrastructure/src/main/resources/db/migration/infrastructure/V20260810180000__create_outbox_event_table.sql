CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    schema_version INT NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    correlation_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_event_pending ON outbox_event (created_at) WHERE status = 'PENDING';
