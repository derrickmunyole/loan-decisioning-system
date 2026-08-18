package io.github.derrickmunyole.loandecisioning.decisioning;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.AuditEventView;
import java.time.Instant;

/**
 * Same shape as the {@code TimelineEntry} this replaces in {@code applicant-origination} — moved
 * here in Epic 4.3 along with the endpoint itself, not redesigned. Used both as the applicant's
 * whole response and as the {@code events} section of the staff aggregate.
 */
record TimelineEvent(String actor, String action, Instant occurredAt) {

    static TimelineEvent from(AuditEventView event) {
        return new TimelineEvent(event.actor(), event.action(), event.occurredAt());
    }
}
