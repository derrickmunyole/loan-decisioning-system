package io.github.derrickmunyole.loandecisioning.origination.application;

import io.github.derrickmunyole.loandecisioning.infrastructure.audit.AuditEvent;
import java.time.Instant;

public record TimelineEntry(String actor, String action, Instant occurredAt) {

    static TimelineEntry from(AuditEvent event) {
        return new TimelineEntry(event.getActor(), event.getAction(), event.getOccurredAt());
    }
}
