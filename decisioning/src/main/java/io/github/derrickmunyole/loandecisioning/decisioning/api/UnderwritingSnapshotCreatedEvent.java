package io.github.derrickmunyole.loandecisioning.decisioning.api;

import io.github.derrickmunyole.loandecisioning.common.OutboxPayload;
import java.util.UUID;

/**
 * The blueprint's own §7 event catalog names this one — {@code underwriting.snapshot.created} —
 * but nothing published it until Epic 3.4, since nothing consumed it until the decision engine
 * did. Published by {@code UnderwritingRequestedHandler} in the same transaction as the snapshot
 * save, mirroring how {@code verification} publishes {@code underwriting.requested} in its own
 * transaction (Epic 3.1).
 */
public record UnderwritingSnapshotCreatedEvent(UUID applicationId, UUID applicationVersionId)
        implements OutboxPayload {

    @Override
    public String eventType() {
        return "underwriting.snapshot.created";
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
