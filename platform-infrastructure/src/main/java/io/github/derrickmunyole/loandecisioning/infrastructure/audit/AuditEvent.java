package io.github.derrickmunyole.loandecisioning.infrastructure.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

/** Append-only. See {@link Audited} for how rows get created. */
@Entity
@Getter
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private String action;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private String targetId;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEvent() {}

    public AuditEvent(
            String actor, String action, String targetType, String targetId, String correlationId) {
        this.actor = actor;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.correlationId = correlationId;
        this.occurredAt = Instant.now();
    }
}
