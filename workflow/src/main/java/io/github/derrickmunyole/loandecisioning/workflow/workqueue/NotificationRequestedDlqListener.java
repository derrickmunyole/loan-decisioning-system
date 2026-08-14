package io.github.derrickmunyole.loandecisioning.workflow.workqueue;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.RabbitQueueNames;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * The first {@code @RabbitListener} to live outside {@code platform-infrastructure} — see {@link
 * io.github.derrickmunyole.loandecisioning.infrastructure.api.AmqpDedupeService} for why that
 * needed a new shared API rather than reaching into {@code infrastructure.messaging} directly.
 * Each DLQ gets its own listener like this one, per the roadmap's A.4.
 */
@Component
class NotificationRequestedDlqListener {

    private static final String CONSUMER_NAME = "notification-requested-dlq-listener";

    private final DeadLetterTaskHandler handler;

    NotificationRequestedDlqListener(DeadLetterTaskHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(queues = RabbitQueueNames.NOTIFICATION_REQUESTED_DLQ)
    void handle(Message message) {
        handler.process(message, CONSUMER_NAME);
    }
}
