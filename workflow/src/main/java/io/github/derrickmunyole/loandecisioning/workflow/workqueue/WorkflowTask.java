package io.github.derrickmunyole.loandecisioning.workflow.workqueue;

import io.github.derrickmunyole.loandecisioning.workflow.api.WorkflowTaskType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Underwriter/operations work queue (blueprint section 5, cross-cutting entities: {@code
 * workflow_task}). Insert-only until Epic 4.2, whose {@link #markResolved} is this entity's only
 * update path — every other field stays fixed at creation.
 */
@Entity
@Getter
@Table(name = "workflow_task")
public class WorkflowTask {

    @Id
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false)
    private WorkflowTaskType taskType;

    @Column(name = "source_queue")
    private String sourceQueue;

    @Column(name = "application_id")
    private UUID applicationId;

    private String reason;

    private Integer attempts;

    @Column(columnDefinition = "text")
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkflowTaskStatus status;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(columnDefinition = "text")
    private String resolution;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected WorkflowTask() {}

    public WorkflowTask(
            WorkflowTaskType taskType,
            String sourceQueue,
            UUID applicationId,
            String reason,
            Integer attempts,
            String detail,
            String correlationId) {
        this.taskType = taskType;
        this.sourceQueue = sourceQueue;
        this.applicationId = applicationId;
        this.reason = reason;
        this.attempts = attempts;
        this.detail = detail;
        this.status = WorkflowTaskStatus.OPEN;
        this.correlationId = correlationId;
        this.createdAt = Instant.now();
    }

    /**
     * Self-idempotent — resolving an already-{@code RESOLVED} task is a no-op rather than
     * overwriting who actually resolved it first, so callers (both the ops-facing {@code
     * POST /work-queue/{id}/resolve} and {@code decisioning}'s retry flow via {@code
     * WorkflowTaskResolutionService}) don't each need their own guard against double-resolution.
     */
    public void markResolved(String resolution, String resolvedBy) {
        if (this.status == WorkflowTaskStatus.RESOLVED) {
            return;
        }
        this.status = WorkflowTaskStatus.RESOLVED;
        this.resolution = resolution;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = Instant.now();
    }
}
