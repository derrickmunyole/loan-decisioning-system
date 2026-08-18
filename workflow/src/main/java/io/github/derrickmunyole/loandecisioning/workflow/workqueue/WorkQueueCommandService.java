package io.github.derrickmunyole.loandecisioning.workflow.workqueue;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.Audited;
import io.github.derrickmunyole.loandecisioning.workflow.api.WorkflowTaskNotFoundException;
import io.github.derrickmunyole.loandecisioning.workflow.api.WorkflowTaskType;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In the same module as {@link WorkflowTask}/{@link WorkflowTaskRepository}, so — unlike {@code
 * decisioning}'s retry flow — this goes straight to the entity rather than through {@code
 * workflow.api.WorkflowTaskResolutionService}; that port exists for external callers, not for
 * code already inside {@code workflow}.
 */
@Service
class WorkQueueCommandService {

    private final WorkflowTaskRepository workflowTaskRepository;

    WorkQueueCommandService(WorkflowTaskRepository workflowTaskRepository) {
        this.workflowTaskRepository = workflowTaskRepository;
    }

    @Transactional
    @Audited(action = "WORK_QUEUE_TASK_RESOLVED", targetType = "WorkflowTask", targetId = "#taskId")
    WorkflowTaskResponse resolve(String actor, UUID taskId, ResolveWorkQueueTaskRequest request) {
        WorkflowTask task =
                workflowTaskRepository
                        .findById(taskId)
                        .orElseThrow(() -> new WorkflowTaskNotFoundException(taskId));
        if (task.getTaskType() == WorkflowTaskType.UNDERWRITE_CASE) {
            throw new WorkQueueTaskOutOfScopeException(taskId);
        }
        task.markResolved(request.resolution(), actor);
        return WorkflowTaskResponse.from(task);
    }
}
