CREATE TABLE idempotency_key (
    id UUID PRIMARY KEY,
    scope VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_body TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_idempotency_key_scope_key ON idempotency_key (scope, idempotency_key);
