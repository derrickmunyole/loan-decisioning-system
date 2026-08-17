ALTER TABLE workflow_task ADD COLUMN application_id UUID;

CREATE INDEX idx_workflow_task_application_id ON workflow_task (application_id);
