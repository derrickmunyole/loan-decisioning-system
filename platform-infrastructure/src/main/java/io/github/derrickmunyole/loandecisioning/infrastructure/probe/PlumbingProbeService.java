package io.github.derrickmunyole.loandecisioning.infrastructure.probe;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.NotificationRequestedEvent;
import io.github.derrickmunyole.loandecisioning.infrastructure.api.OutboxEventPublisher;
import io.github.derrickmunyole.loandecisioning.infrastructure.api.Audited;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PlumbingProbeService {

    private final OutboxEventPublisher outboxEventPublisher;

    PlumbingProbeService(OutboxEventPublisher outboxEventPublisher) {
        this.outboxEventPublisher = outboxEventPublisher;
    }

    @Transactional
    @Audited(
            action = "PLUMBING_PROBE_DEMO_EVENT",
            targetType = "Notification",
            targetId = "#recipient")
    NotificationRequestedEvent createDemoEvent(String recipient) {
        NotificationRequestedEvent event =
                new NotificationRequestedEvent(null, recipient, "LOG", "plumbing-probe-demo");
        outboxEventPublisher.enqueue(event);
        return event;
    }
}
