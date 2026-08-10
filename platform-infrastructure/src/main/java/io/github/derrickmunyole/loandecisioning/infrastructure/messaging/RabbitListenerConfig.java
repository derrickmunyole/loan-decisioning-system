package io.github.derrickmunyole.loandecisioning.infrastructure.messaging;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Overrides Spring Boot's default listener container factory to require manual acks: the
 * business transaction must commit before a message is acked, or a crash mid-processing loses
 * the effect silently. {@code spring.rabbitmq.listener.simple.retry.*} still applies via the
 * injected {@link SimpleRabbitListenerContainerFactoryConfigurer} — retries happen in-process
 * before a message is finally rejected and dead-lettered.
 */
@Configuration
class RabbitListenerConfig {

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
}
