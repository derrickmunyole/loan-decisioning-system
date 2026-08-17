package io.github.derrickmunyole.loandecisioning.workflow.workqueue;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.AmqpDedupeService;
import io.github.derrickmunyole.loandecisioning.workflow.api.WorkflowTaskType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Split out from {@link NotificationRequestedDlqListener} so the transaction boundary is this
 * method's return, not the listener method's — same ack-after-commit reasoning as ADR 0004's
 * {@code NotificationRequestedHandler} split.
 *
 * <p>A message reaching this handler already exhausted retries on the real queue — this is the
 * last line before an application's failure goes silently unnoticed, so unlike {@code
 * NotificationRequestedHandler}, this handler must not itself throw on missing/malformed headers.
 * Losing the {@code eventId} header degrades to "dedupe skipped for this one row," not a crash
 * that would let the broker's own retry-then-drop behavior (this queue has no further DLX) erase
 * the failure record entirely.
 *
 * <p>Generic over which DLQ it's handling — {@code consumerName} identifies the caller for the
 * {@code consumed_event} dedupe row, so two different DLQ listeners sharing this one handler don't
 * collide under the same dedupe identity (harmless for correctness, since {@code eventId} alone is
 * already globally unique, but confusing for anyone reading {@code consumed_event} rows later).
 */
@Service
class DeadLetterTaskHandler {

    private static final int MAX_DETAIL_LENGTH = 2000;

    private final AmqpDedupeService amqpDedupeService;
    private final WorkflowTaskRepository workflowTaskRepository;

    DeadLetterTaskHandler(
            AmqpDedupeService amqpDedupeService, WorkflowTaskRepository workflowTaskRepository) {
        this.amqpDedupeService = amqpDedupeService;
        this.workflowTaskRepository = workflowTaskRepository;
    }

    @Transactional
    void process(Message message, String consumerName) {
        UUID eventId = extractEventId(message);
        if (eventId != null && amqpDedupeService.alreadyConsumed(consumerName, eventId)) {
            return;
        }

        Map<String, Object> death = mostRecentDeath(message);
        workflowTaskRepository.save(
                new WorkflowTask(
                        WorkflowTaskType.MESSAGE_PROCESSING_FAILURE,
                        asString(death.get("queue")),
                        null,
                        asString(death.get("reason")),
                        asInteger(death.get("count")),
                        truncate(new String(message.getBody(), StandardCharsets.UTF_8)),
                        asString(message.getMessageProperties().getHeader("correlationId"))));

        if (eventId != null) {
            amqpDedupeService.markConsumed(consumerName, eventId);
        }
    }

    private UUID extractEventId(Message message) {
        Object header = message.getMessageProperties().getHeader("eventId");
        if (header == null) {
            return null;
        }
        try {
            return UUID.fromString(header.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * RabbitMQ stamps every dead-lettered message with an {@code x-death} header — a list because
     * a message can be dead-lettered through more than one queue over its lifetime. The most
     * recent event is the first element, not the last. A message shoveled directly into the DLQ
     * (as opposed to broker-dead-lettered) won't have this header at all.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mostRecentDeath(Message message) {
        Object header = message.getMessageProperties().getHeader("x-death");
        if (!(header instanceof List<?> deaths) || deaths.isEmpty()) {
            return Map.of();
        }
        Object first = deaths.get(0);
        return first instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Integer asInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private String truncate(String value) {
        return value.length() <= MAX_DETAIL_LENGTH ? value : value.substring(0, MAX_DETAIL_LENGTH);
    }
}
