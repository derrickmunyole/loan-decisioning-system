package io.github.derrickmunyole.loandecisioning.infrastructure.notification;

import io.github.derrickmunyole.loandecisioning.infrastructure.messaging.RabbitTopologyConfig;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationRequestedListener {

    private final NotificationRequestedHandler handler;

    NotificationRequestedListener(NotificationRequestedHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(queues = RabbitTopologyConfig.NOTIFICATION_REQUESTED_QUEUE)
    public void handle(Message message) throws IOException {
        handler.process(message);
    }
}
