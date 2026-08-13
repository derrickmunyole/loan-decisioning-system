CREATE TABLE workflow_task (
    id UUID PRIMARY KEY,
    task_type VARCHAR(40) NOT NULL,
    source_queue VARCHAR(200),
    reason VARCHAR(40),
    attempts INT,
    detail TEXT,
    status VARCHAR(20) NOT NULL,
    correlation_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_workflow_task_status ON workflow_task (status);
