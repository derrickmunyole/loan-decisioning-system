package io.github.derrickmunyole.loandecisioning.origination.application;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.AuditEventView;
import java.time.Instant;

public record TimelineEntry(String actor, String action, Instant occurredAt) {

    static TimelineEntry from(AuditEventView event) {
        return new TimelineEntry(event.actor(), event.action(), event.occurredAt());
    }
}
