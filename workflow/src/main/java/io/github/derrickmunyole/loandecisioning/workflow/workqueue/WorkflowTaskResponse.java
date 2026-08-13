package io.github.derrickmunyole.loandecisioning.workflow.workqueue;

import java.time.Instant;
import java.util.UUID;

public record WorkflowTaskResponse(
        UUID id,
        WorkflowTaskType taskType,
        String sourceQueue,
        String reason,
        Integer attempts,
        String detail,
        WorkflowTaskStatus status,
        String correlationId,
        Instant createdAt) {

    static WorkflowTaskResponse from(WorkflowTask task) {
        return new WorkflowTaskResponse(
                task.getId(),
                task.getTaskType(),
                task.getSourceQueue(),
                task.getReason(),
                task.getAttempts(),
                task.getDetail(),
                task.getStatus(),
                task.getCorrelationId(),
                task.getCreatedAt());
    }
}
