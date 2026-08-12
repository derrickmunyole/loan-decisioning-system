package io.github.derrickmunyole.loandecisioning.infrastructure.api;

import java.util.List;

/** The contract other modules depend on to read audit history — see {@code OutboxEventPublisher}. */
public interface AuditQueryService {

    List<AuditEventView> findByTarget(String targetType, String targetId);
}
