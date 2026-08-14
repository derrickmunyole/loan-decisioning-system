package io.github.derrickmunyole.loandecisioning.workflow.api;

/**
 * The blueprint's {@code workflow_task} purposes (review document, underwrite case, resolve
 * funding failure) each get their own value when the epic that actually produces them lands,
 * rather than being guessed at ahead of time. {@code CREDIT_SCORE_PROVIDER_UNAVAILABLE} (Epic
 * 3.4) is the first purpose not literally named in the blueprint's list — a synchronous
 * third-party provider outage during decisioning, distinct from a message-processing failure.
 */
public enum WorkflowTaskType {
    MESSAGE_PROCESSING_FAILURE,
    CREDIT_SCORE_PROVIDER_UNAVAILABLE
}
