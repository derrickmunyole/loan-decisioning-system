CREATE TABLE notification (
    id UUID PRIMARY KEY,
    recipient VARCHAR(255) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    message_intent VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL,
    correlation_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL
);
