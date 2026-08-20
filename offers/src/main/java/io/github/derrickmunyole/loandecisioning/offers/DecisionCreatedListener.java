package io.github.derrickmunyole.loandecisioning.offers;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.RabbitQueueNames;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
class DecisionCreatedListener {

    private final DecisionCreatedHandler handler;

    DecisionCreatedListener(DecisionCreatedHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(queues = RabbitQueueNames.DECISION_CREATED_QUEUE)
    void handle(Message message) throws IOException {
        handler.process(message);
    }
}
