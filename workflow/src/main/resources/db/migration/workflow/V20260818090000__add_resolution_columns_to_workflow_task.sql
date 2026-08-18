ALTER TABLE workflow_task ADD COLUMN resolution TEXT;
ALTER TABLE workflow_task ADD COLUMN resolved_by VARCHAR(50);
ALTER TABLE workflow_task ADD COLUMN resolved_at TIMESTAMPTZ;
