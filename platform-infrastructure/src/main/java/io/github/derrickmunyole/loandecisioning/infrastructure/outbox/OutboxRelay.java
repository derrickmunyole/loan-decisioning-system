package io.github.derrickmunyole.loandecisioning.infrastructure.outbox;

import io.github.derrickmunyole.loandecisioning.infrastructure.messaging.RabbitTopologyConfig;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls pending outbox rows and publishes them with publisher confirms enabled. A row is marked
 * {@code PUBLISHED} only inside the confirm callback (see {@link
 * OutboxPublishConfirmationService}) — never right after {@code convertAndSend} — so a crash
 * between send and broker ack can't silently lose an event.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxPublishConfirmationService confirmationService;

    public OutboxRelay(
            OutboxEventRepository outboxEventRepository,
            RabbitTemplate rabbitTemplate,
            OutboxPublishConfirmationService confirmationService) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.confirmationService = confirmationService;
    }

    @PostConstruct
    void registerConfirmCallback() {
        rabbitTemplate.setConfirmCallback(
                (correlationData, ack, cause) -> {
                    if (correlationData == null) {
                        return;
                    }
                    UUID eventId = UUID.fromString(correlationData.getId());
                    if (ack) {
                        confirmationService.markPublished(eventId);
                    } else {
                        log.warn("Outbox event {} was nacked by the broker: {}", eventId, cause);
                    }
                });
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay-interval:2000}")
    @Transactional
    public void relay() {
        List<OutboxEvent> batch = outboxEventRepository.lockNextPendingBatch(BATCH_SIZE);
        for (OutboxEvent event : batch) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        Message message =
                MessageBuilder.withBody(event.getPayload().getBytes())
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setHeader("eventId", event.getId().toString())
                        .setHeader("correlationId", event.getCorrelationId())
                        .build();
        rabbitTemplate.convertAndSend(
                RabbitTopologyConfig.EVENTS_EXCHANGE,
                event.getEventType(),
                message,
                new CorrelationData(event.getId().toString()));
        event.incrementAttempts();
    }
}
