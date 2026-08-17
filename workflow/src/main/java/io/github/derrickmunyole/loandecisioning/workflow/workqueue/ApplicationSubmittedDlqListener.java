package io.github.derrickmunyole.loandecisioning.workflow.workqueue;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.RabbitQueueNames;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * The one DLQ that had no listener at all until Epic 4.2 — {@code
 * verification.application-submitted.dlq} was declared at the topology level since Epic 2.3, but
 * nothing consumed it, so an exhausted-retry {@code application.submitted} message (e.g. the
 * {@code SYNTHETIC_TRANSIENT_FAILURE} sentinel exhausting Spring AMQP's own retry interceptor)
 * dead-lettered and then sat there permanently — the exact "silently lost application" the
 * blueprint's resilience requirements exist to prevent, and the concrete reason a verification
 * exception had nothing to populate ops's scoped work-queue subset with. Same {@link
 * DeadLetterTaskHandler} every other DLQ listener uses.
 */
@Component
class ApplicationSubmittedDlqListener {

    private static final String CONSUMER_NAME = "application-submitted-dlq-listener";

    private final DeadLetterTaskHandler handler;

    ApplicationSubmittedDlqListener(DeadLetterTaskHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(queues = RabbitQueueNames.APPLICATION_SUBMITTED_DLQ)
    void handle(Message message) {
        handler.process(message, CONSUMER_NAME);
    }
}
