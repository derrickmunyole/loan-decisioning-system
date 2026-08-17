package io.github.derrickmunyole.loandecisioning.workflow.api;

import io.github.derrickmunyole.loandecisioning.workflow.workqueue.WorkflowTask;
import io.github.derrickmunyole.loandecisioning.workflow.workqueue.WorkflowTaskRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Write port for modules outside {@code workflow} that need to raise an ops-visible {@code
 * workflow_task} — e.g. {@code decisioning}, when a synchronous third-party call (the
 * credit-score provider) is unavailable. Until Epic 3.4, every {@code workflow_task} came from a
 * DLQ listener living inside {@code workflow} itself; this is the first real caller from another
 * module, so {@code WorkflowTaskType} moved into this package alongside it — the same "extract on
 * the second real caller" pattern ADR 0007 documents.
 */
@Service
public class WorkflowTaskCreationService {

    private final WorkflowTaskRepository workflowTaskRepository;

    public WorkflowTaskCreationService(WorkflowTaskRepository workflowTaskRepository) {
        this.workflowTaskRepository = workflowTaskRepository;
    }

    public UUID createTask(
            WorkflowTaskType taskType,
            UUID applicationId,
            String reason,
            String detail,
            String correlationId) {
        WorkflowTask task =
                workflowTaskRepository.save(
                        new WorkflowTask(taskType, null, applicationId, reason, null, detail, correlationId));
        return task.getId();
    }
}
