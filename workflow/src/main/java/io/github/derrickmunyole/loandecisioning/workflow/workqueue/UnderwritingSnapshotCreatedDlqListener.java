package io.github.derrickmunyole.loandecisioning.workflow.workqueue;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.RabbitQueueNames;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * One more DLQ getting its own listener, per the roadmap's A.4 — same {@link
 * DeadLetterTaskHandler} every other DLQ listener uses. This is the generic catch-all for
 * anything the decisioning module's consumer throws that isn't the credit-score-provider-outage
 * path it handles itself — e.g. no published policy/scorecard/pricing configuration.
 */
@Component
class UnderwritingSnapshotCreatedDlqListener {

    private static final String CONSUMER_NAME = "underwriting-snapshot-created-dlq-listener";

    private final DeadLetterTaskHandler handler;

    UnderwritingSnapshotCreatedDlqListener(DeadLetterTaskHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(queues = RabbitQueueNames.UNDERWRITING_SNAPSHOT_CREATED_DLQ)
    void handle(Message message) {
        handler.process(message, CONSUMER_NAME);
    }
}
