package io.github.derrickmunyole.loandecisioning.workflow.workqueue;

/**
 * Only {@code MESSAGE_PROCESSING_FAILURE} exists as of Epic 2.2. The blueprint's {@code
 * workflow_task} purposes (review document, underwrite case, resolve funding failure) each get
 * their own value when the epic that actually produces them lands, rather than being guessed at
 * ahead of time.
 */
public enum WorkflowTaskType {
    MESSAGE_PROCESSING_FAILURE
}
