CREATE TABLE consumed_event (
    id UUID PRIMARY KEY,
    consumer_name VARCHAR(150) NOT NULL,
    event_id UUID NOT NULL,
    consumed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_consumed_event_consumer_event UNIQUE (consumer_name, event_id)
);
