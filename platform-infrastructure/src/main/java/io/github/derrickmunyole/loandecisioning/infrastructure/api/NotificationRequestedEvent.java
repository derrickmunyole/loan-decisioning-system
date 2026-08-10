package io.github.derrickmunyole.loandecisioning.infrastructure.api;

import io.github.derrickmunyole.loandecisioning.common.OutboxPayload;
import java.util.UUID;

/** Payload for the blueprint's {@code notification.requested} event (§7 of the blueprint). */
public record NotificationRequestedEvent(
        UUID notificationId, String recipient, String channel, String messageIntent)
        implements OutboxPayload {

    public NotificationRequestedEvent {
        if (notificationId == null) {
            notificationId = UUID.randomUUID();
        }
    }

    @Override
    public String eventType() {
        return "notification.requested";
    }

    @Override
    public String aggregateType() {
        return "Notification";
    }

    @Override
    public UUID aggregateId() {
        return notificationId;
    }
}
