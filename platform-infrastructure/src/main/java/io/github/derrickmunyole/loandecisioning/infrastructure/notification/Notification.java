package io.github.derrickmunyole.loandecisioning.infrastructure.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Log-only stub for now — no real delivery channel exists yet, so status is always LOGGED. */
@Entity
@Getter
@Table(name = "notification")
public class Notification {

    private static final String STUB_STATUS = "LOGGED";

    @Id
    private UUID id;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private String channel;

    @Column(name = "message_intent", nullable = false)
    private String messageIntent;

    @Column(nullable = false)
    private String status;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {}

    /**
     * {@code id} must be the {@code notificationId} the caller was already given (it's the
     * event's aggregate ID) — never auto-generated here, or the caller's reference stops
     * pointing at the row that actually gets created.
     */
    public Notification(UUID id, String recipient, String channel, String messageIntent, String correlationId) {
        this.id = id;
        this.recipient = recipient;
        this.channel = channel;
        this.messageIntent = messageIntent;
        this.status = STUB_STATUS;
        this.correlationId = correlationId;
        this.createdAt = Instant.now();
    }
}
