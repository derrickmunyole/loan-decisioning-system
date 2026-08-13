package io.github.derrickmunyole.loandecisioning.workflow.workqueue;

import com.rabbitmq.client.Channel;
import io.github.derrickmunyole.loandecisioning.infrastructure.api.RabbitQueueNames;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * The first {@code @RabbitListener} to live outside {@code platform-infrastructure} — see {@link
 * io.github.derrickmunyole.loandecisioning.infrastructure.api.AmqpDedupeService} for why that
 * needed a new shared API rather than reaching into {@code infrastructure.messaging} directly.
 * Each DLQ gets its own listener like this one, per the roadmap's A.4.
 */
@Component
class NotificationRequestedDlqListener {

    private final DeadLetterTaskHandler handler;

    NotificationRequestedDlqListener(DeadLetterTaskHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(queues = RabbitQueueNames.NOTIFICATION_REQUESTED_DLQ)
    void handle(Message message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        handler.process(message);
        channel.basicAck(deliveryTag, false);
    }
}
