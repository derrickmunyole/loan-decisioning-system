package io.github.derrickmunyole.loandecisioning.workflow.workqueue;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.RabbitQueueNames;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Same {@link DeadLetterTaskHandler} every other DLQ listener uses — the generic catch-all for
 * anything the {@code offers} module's {@code decision.created} consumer throws (Epic 5.1).
 */
@Component
class DecisionCreatedDlqListener {

    private static final String CONSUMER_NAME = "decision-created-dlq-listener";

    private final DeadLetterTaskHandler handler;

    DecisionCreatedDlqListener(DeadLetterTaskHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(queues = RabbitQueueNames.DECISION_CREATED_DLQ)
    void handle(Message message) {
        handler.process(message, CONSUMER_NAME);
    }
}