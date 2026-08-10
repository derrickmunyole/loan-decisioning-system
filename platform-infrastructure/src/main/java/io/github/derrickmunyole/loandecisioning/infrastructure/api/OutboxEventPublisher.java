package io.github.derrickmunyole.loandecisioning.infrastructure.api;

import io.github.derrickmunyole.loandecisioning.common.OutboxPayload;
import java.util.UUID;

/**
 * The contract other modules depend on to participate in the transactional outbox. Callers must
 * invoke this from within their own {@code @Transactional} method so the outbox row commits
 * atomically with the business change it describes.
 */
public interface OutboxEventPublisher {

    UUID enqueue(OutboxPayload payload);
}
