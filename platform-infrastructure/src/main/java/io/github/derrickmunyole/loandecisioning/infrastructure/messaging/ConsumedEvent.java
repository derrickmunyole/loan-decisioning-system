package io.github.derrickmunyole.loandecisioning.infrastructure.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Dedupes RabbitMQ's at-least-once delivery per consumer. Distinct from {@code
 * app_user}-style entities: this is intentionally append-only and has no domain meaning beyond
 * "consumer X has already handled event Y."
 */
@Entity
@Getter
@Table(
        name = "consumed_event",
        uniqueConstraints = @UniqueConstraint(columnNames = {"consumer_name", "event_id"}))
public class ConsumedEvent {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "consumer_name", nullable = false)
    private String consumerName;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;

    protected ConsumedEvent() {}

    public ConsumedEvent(String consumerName, UUID eventId) {
        this.consumerName = consumerName;
        this.eventId = eventId;
        this.consumedAt = Instant.now();
    }
}
