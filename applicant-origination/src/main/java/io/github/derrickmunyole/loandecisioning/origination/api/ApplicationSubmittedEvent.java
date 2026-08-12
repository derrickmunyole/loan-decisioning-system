package io.github.derrickmunyole.loandecisioning.origination.api;

import io.github.derrickmunyole.loandecisioning.common.OutboxPayload;
import java.util.UUID;

/** Payload for the blueprint's {@code application.submitted} event (§7). */
public record ApplicationSubmittedEvent(UUID applicationId, UUID applicantId, int versionNumber)
        implements OutboxPayload {

    @Override
    public String eventType() {
        return "application.submitted";
    }

    @Override
    public String aggregateType() {
        return "Application";
    }

    @Override
    public UUID aggregateId() {
        return applicationId;
    }
}
