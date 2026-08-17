package io.github.derrickmunyole.loandecisioning.workflow.api;

import io.github.derrickmunyole.loandecisioning.workflow.workqueue.WorkflowTask;
import io.github.derrickmunyole.loandecisioning.workflow.workqueue.WorkflowTaskRepository;
import io.github.derrickmunyole.loandecisioning.workflow.workqueue.WorkflowTaskStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read/write port for modules outside {@code workflow} that need to find or resolve a {@code
 * WorkflowTask} — e.g. {@code decisioning}'s retry flow (Epic 4.2), which needs to confirm an
 * open {@code CREDIT_SCORE_PROVIDER_UNAVAILABLE} task exists for an application before retrying,
 * then mark it resolved once the retry succeeds. {@code workflow}'s own {@code
 * POST /work-queue/{id}/resolve} action doesn't go through this port — it's in the same module,
 * so it uses {@code WorkflowTaskRepository} and {@link WorkflowTask#markResolved} directly.
 */
@Service
public class WorkflowTaskResolutionService {

    private final WorkflowTaskRepository workflowTaskRepository;

    public WorkflowTaskResolutionService(WorkflowTaskRepository workflowTaskRepository) {
        this.workflowTaskRepository = workflowTaskRepository;
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowTaskView> findOpenTask(UUID applicationId, WorkflowTaskType taskType) {
        return workflowTaskRepository
                .findFirstByApplicationIdAndTaskTypeAndStatusOrderByCreatedAtDesc(
                        applicationId, taskType, WorkflowTaskStatus.OPEN)
                .map(task -> new WorkflowTaskView(task.getId(), task.getTaskType(), task.getApplicationId()));
    }

    @Transactional
    public void markResolved(UUID taskId, String resolution, String resolvedBy) {
        WorkflowTask task =
                workflowTaskRepository
                        .findById(taskId)
                        .orElseThrow(() -> new WorkflowTaskNotFoundException(taskId));
        task.markResolved(resolution, resolvedBy);
    }
}
