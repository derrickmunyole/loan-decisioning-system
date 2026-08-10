package io.github.derrickmunyole.loandecisioning.infrastructure.outbox;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marks an outbox row published. Runs in its own transaction because the broker's publisher
 * confirm arrives asynchronously, on a different thread, after the relay's own transaction that
 * sent the message has already committed.
 */
@Service
class OutboxPublishConfirmationService {

    private final OutboxEventRepository outboxEventRepository;

    OutboxPublishConfirmationService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public void markPublished(UUID eventId) {
        outboxEventRepository
                .findById(eventId)
                .ifPresent(
                        event -> {
                            event.markPublished();
                            outboxEventRepository.save(event);
                        });
    }
}
