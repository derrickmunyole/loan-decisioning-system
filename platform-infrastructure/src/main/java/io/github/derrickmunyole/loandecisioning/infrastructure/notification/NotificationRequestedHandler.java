package io.github.derrickmunyole.loandecisioning.infrastructure.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.derrickmunyole.loandecisioning.infrastructure.api.NotificationRequestedEvent;
import io.github.derrickmunyole.loandecisioning.infrastructure.messaging.ConsumedEvent;
import io.github.derrickmunyole.loandecisioning.infrastructure.messaging.ConsumedEventRepository;
import java.io.IOException;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Split out from {@link NotificationRequestedListener} so the transaction boundary is this
 * method's return, not the listener method's — the listener acks only after this call returns,
 * which is genuinely after commit. Acking from inside this {@code @Transactional} method would
 * ack before Spring's transaction interceptor actually commits.
 */
@Service
class NotificationRequestedHandler {

    static final String CONSUMER_NAME = "notification-requested-listener";

    private final ConsumedEventRepository consumedEventRepository;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    NotificationRequestedHandler(
            ConsumedEventRepository consumedEventRepository,
            NotificationRepository notificationRepository,
            ObjectMapper objectMapper) {
        this.consumedEventRepository = consumedEventRepository;
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    void process(Message message) throws IOException {
        UUID eventId = UUID.fromString((String) message.getMessageProperties().getHeader("eventId"));
        if (consumedEventRepository.existsByConsumerNameAndEventId(CONSUMER_NAME, eventId)) {
            return;
        }
        NotificationRequestedEvent event =
                objectMapper.readValue(message.getBody(), NotificationRequestedEvent.class);
        String correlationId = (String) message.getMessageProperties().getHeader("correlationId");
        notificationRepository.save(
                new Notification(
                        event.notificationId(),
                        event.recipient(),
                        event.channel(),
                        event.messageIntent(),
                        correlationId));
        consumedEventRepository.save(new ConsumedEvent(CONSUMER_NAME, eventId));
    }
}
