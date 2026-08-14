package io.github.derrickmunyole.loandecisioning.verification.api;

import io.github.derrickmunyole.loandecisioning.common.OutboxPayload;
import java.util.UUID;

/**
 * Published once {@code verification}'s {@code application.submitted} handler drives an
 * application into {@code UNDERWRITING} — not one of the blueprint's original §7 events, but a
 * new one introduced in Epic 3.1 for the same reason {@code application.submitted} already
 * exists: it now has a real consumer ({@code decisioning}, building the immutable {@code
 * UnderwritingSnapshot}), where earlier epics (2.1, 2.3) deliberately skipped publishing
 * intermediate events with no consumer yet.
 */
public record UnderwritingRequestedEvent(UUID applicationId, int versionNumber) implements OutboxPayload {

    @Override
    public String eventType() {
        return "underwriting.requested";
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
