package io.github.derrickmunyole.loandecisioning.workflow.workqueue;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.RabbitQueueNames;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * One more DLQ getting its own listener, per the roadmap's A.4 — same {@link
 * DeadLetterTaskHandler} every other DLQ listener uses, since it's already generic over the
 * dead-lettered message rather than specific to notifications.
 */
@Component
class UnderwritingRequestedDlqListener {

    private static final String CONSUMER_NAME = "underwriting-requested-dlq-listener";

    private final DeadLetterTaskHandler handler;

    UnderwritingRequestedDlqListener(DeadLetterTaskHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(queues = RabbitQueueNames.UNDERWRITING_REQUESTED_DLQ)
    void handle(Message message) {
        handler.process(message, CONSUMER_NAME);
    }
}
