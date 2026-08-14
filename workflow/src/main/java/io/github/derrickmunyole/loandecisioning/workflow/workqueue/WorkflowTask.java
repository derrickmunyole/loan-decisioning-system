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

/** Human work queue (blueprint section 5, cross-cutting entities). Append-only for now — nothing
 * updates a row until Epic 4.2's resolve action exists. */
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

    protected WorkflowTask() {}

    public WorkflowTask(
            WorkflowTaskType taskType,
            String sourceQueue,
            String reason,
            Integer attempts,
            String detail,
            String correlationId) {
        this.taskType = taskType;
        this.sourceQueue = sourceQueue;
        this.reason = reason;
        this.attempts = attempts;
        this.detail = detail;
        this.status = WorkflowTaskStatus.OPEN;
        this.correlationId = correlationId;
        this.createdAt = Instant.now();
    }
}
