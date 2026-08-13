package io.github.derrickmunyole.loandecisioning.verification;

import io.github.derrickmunyole.loandecisioning.infrastructure.messaging.RabbitTopologyConfig;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationSubmittedListener {

    private final ApplicationSubmittedHandler handler;

    ApplicationSubmittedListener(ApplicationSubmittedHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(queues = RabbitTopologyConfig.APPLICATION_SUBMITTED_QUEUE)
    public void handle(Message message) throws IOException {
        handler.process(message);
    }
}
