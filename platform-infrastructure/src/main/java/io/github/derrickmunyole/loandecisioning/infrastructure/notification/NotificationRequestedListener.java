package io.github.derrickmunyole.loandecisioning.infrastructure.notification;

import com.rabbitmq.client.Channel;
import io.github.derrickmunyole.loandecisioning.infrastructure.messaging.RabbitTopologyConfig;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class NotificationRequestedListener {

    private final NotificationRequestedHandler handler;

    NotificationRequestedListener(NotificationRequestedHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(queues = RabbitTopologyConfig.NOTIFICATION_REQUESTED_QUEUE)
    public void handle(Message message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        handler.process(message);
        channel.basicAck(deliveryTag, false);
    }
}
