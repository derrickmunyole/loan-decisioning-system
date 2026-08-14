package io.github.derrickmunyole.loandecisioning.decisioning;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.RabbitQueueNames;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
class UnderwritingRequestedListener {

    private final UnderwritingRequestedHandler handler;

    UnderwritingRequestedListener(UnderwritingRequestedHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(queues = RabbitQueueNames.UNDERWRITING_REQUESTED_QUEUE)
    void handle(Message message) throws IOException {
        handler.process(message);
    }
}
