package io.github.derrickmunyole.loandecisioning.infrastructure.api;

import java.time.Instant;

/** Read-only projection of an audit event — never the {@code AuditEvent} JPA entity itself. */
public record AuditEventView(
        String actor, String action, String targetType, String targetId, Instant occurredAt) {}
