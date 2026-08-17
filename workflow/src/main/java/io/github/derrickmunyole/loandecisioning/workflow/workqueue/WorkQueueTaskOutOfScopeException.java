package io.github.derrickmunyole.loandecisioning.workflow.workqueue;

import java.util.UUID;

/**
 * The {@code OPERATIONS_ANALYST} role gate on {@code POST /work-queue/{id}/resolve} restricts who
 * can call the endpoint at all, but not which task a given caller reaches for by id — an
 * operations analyst could still name an {@code UNDERWRITE_CASE} task's id directly, bypassing
 * the same scoping {@code GET /work-queue} already applies to what they're shown. This is the
 * service-level guard that closes that gap.
 */
class WorkQueueTaskOutOfScopeException extends RuntimeException {

    WorkQueueTaskOutOfScopeException(UUID taskId) {
        super("WorkflowTask " + taskId + " is not resolvable via the ops work queue");
    }
}
