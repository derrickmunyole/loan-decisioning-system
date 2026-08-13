package io.github.derrickmunyole.loandecisioning.infrastructure.api;

/**
 * Queue names a listener living outside this module needs to reference (e.g. {@code
 * @RabbitListener(queues = ...)}). {@code RabbitTopologyConfig} (internal, owns the actual
 * exchange/queue/binding beans) references these too, rather than each side defining its own copy.
 */
public final class RabbitQueueNames {

    public static final String NOTIFICATION_REQUESTED_DLQ = "notifications.notification-requested.dlq";
    public static final String APPLICATION_SUBMITTED_QUEUE = "verification.application-submitted.queue";

    private RabbitQueueNames() {}
}
