package io.github.derrickmunyole.loandecisioning.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.derrickmunyole.loandecisioning.common.OutboxPayload;
import io.github.derrickmunyole.loandecisioning.infrastructure.api.OutboxEventPublisher;
import io.github.derrickmunyole.loandecisioning.infrastructure.correlation.CorrelationIdFilter;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
class OutboxEventPublisherImpl implements OutboxEventPublisher {

    private static final int CURRENT_SCHEMA_VERSION = 1;

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    OutboxEventPublisherImpl(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public UUID enqueue(OutboxPayload payload) {
        String json = writeJson(payload);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        OutboxEvent event =
                new OutboxEvent(
                        payload.aggregateType(),
                        payload.aggregateId(),
                        payload.eventType(),
                        CURRENT_SCHEMA_VERSION,
                        json,
                        correlationId);
        return outboxEventRepository.save(event).getId();
    }

    private String writeJson(OutboxPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload: " + payload, e);
        }
    }
}
