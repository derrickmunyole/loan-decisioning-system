package io.github.derrickmunyole.loandecisioning.workflow.workqueue;

import io.github.derrickmunyole.loandecisioning.workflow.api.WorkflowTaskType;
import java.time.Instant;
import java.util.UUID;

public record WorkflowTaskResponse(
        UUID id,
        WorkflowTaskType taskType,
        String sourceQueue,
        UUID applicationId,
        String reason,
        Integer attempts,
        String detail,
        WorkflowTaskStatus status,
        String correlationId,
        Instant createdAt,
        String resolution,
        String resolvedBy,
        Instant resolvedAt) {

    static WorkflowTaskResponse from(WorkflowTask task) {
        return new WorkflowTaskResponse(
                task.getId(),
                task.getTaskType(),
                task.getSourceQueue(),
                task.getApplicationId(),
                task.getReason(),
                task.getAttempts(),
                task.getDetail(),
                task.getStatus(),
                task.getCorrelationId(),
                task.getCreatedAt(),
                task.getResolution(),
                task.getResolvedBy(),
                task.getResolvedAt());
    }
}
