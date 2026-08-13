package io.github.derrickmunyole.loandecisioning.infrastructure.messaging;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.RabbitQueueNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One topic exchange for every domain event, routing key = event type. Each consumer owns a
 * durable queue bound to the event types it cares about, dead-lettering into its own DLQ after
 * retries are exhausted (see {@code spring.rabbitmq.listener.simple.retry.*}).
 */
@Configuration
public class RabbitTopologyConfig {

    public static final String EVENTS_EXCHANGE = "loan.events";
    public static final String DEAD_LETTER_EXCHANGE = "loan.events.dlx";

    public static final String NOTIFICATION_REQUESTED_QUEUE = "notifications.notification-requested.queue";
    public static final String NOTIFICATION_REQUESTED_DLQ = RabbitQueueNames.NOTIFICATION_REQUESTED_DLQ;
    public static final String NOTIFICATION_REQUESTED_ROUTING_KEY = "notification.requested";

    public static final String APPLICATION_SUBMITTED_QUEUE = RabbitQueueNames.APPLICATION_SUBMITTED_QUEUE;
    public static final String APPLICATION_SUBMITTED_DLQ = "verification.application-submitted.dlq";
    public static final String APPLICATION_SUBMITTED_ROUTING_KEY = "application.submitted";

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue notificationRequestedQueue() {
        return QueueBuilder.durable(NOTIFICATION_REQUESTED_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", NOTIFICATION_REQUESTED_QUEUE)
                .build();
    }

    @Bean
    Queue notificationRequestedDlq() {
        return QueueBuilder.durable(NOTIFICATION_REQUESTED_DLQ).build();
    }

    @Bean
    Binding notificationRequestedBinding() {
        return BindingBuilder.bind(notificationRequestedQueue())
                .to(eventsExchange())
                .with(NOTIFICATION_REQUESTED_ROUTING_KEY);
    }

    @Bean
    Binding notificationRequestedDlqBinding() {
        return BindingBuilder.bind(notificationRequestedDlq())
                .to(deadLetterExchange())
                .with(NOTIFICATION_REQUESTED_QUEUE);
    }

    @Bean
    Queue applicationSubmittedQueue() {
        return QueueBuilder.durable(APPLICATION_SUBMITTED_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", APPLICATION_SUBMITTED_QUEUE)
                .build();
    }

    @Bean
    Queue applicationSubmittedDlq() {
        return QueueBuilder.durable(APPLICATION_SUBMITTED_DLQ).build();
    }

    @Bean
    Binding applicationSubmittedBinding() {
        return BindingBuilder.bind(applicationSubmittedQueue())
                .to(eventsExchange())
                .with(APPLICATION_SUBMITTED_ROUTING_KEY);
    }

    @Bean
    Binding applicationSubmittedDlqBinding() {
        return BindingBuilder.bind(applicationSubmittedDlq())
                .to(deadLetterExchange())
                .with(APPLICATION_SUBMITTED_QUEUE);
    }
}
