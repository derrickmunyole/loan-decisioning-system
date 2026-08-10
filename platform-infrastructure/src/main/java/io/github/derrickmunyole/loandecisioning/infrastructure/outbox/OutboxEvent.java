package io.github.derrickmunyole.loandecisioning.infrastructure.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxEventStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {}

    public OutboxEvent(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            int schemaVersion,
            String payload,
            String correlationId) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.payload = payload;
        this.status = OutboxEventStatus.PENDING;
        this.attempts = 0;
        this.correlationId = correlationId;
        this.createdAt = Instant.now();
    }

    public void markPublished() {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void incrementAttempts() {
        this.attempts++;
    }
}
