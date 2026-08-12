package io.github.derrickmunyole.loandecisioning.infrastructure.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.derrickmunyole.loandecisioning.infrastructure.idempotency.IdempotencyRecord;
import io.github.derrickmunyole.loandecisioning.infrastructure.idempotency.IdempotencyRecordRepository;
import io.github.derrickmunyole.loandecisioning.infrastructure.idempotency.IdempotencyStatus;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generic HTTP Idempotency-Key handling, reused across any endpoint that needs "duplicate request
 * produces exactly one effect, and replays the original response" semantics — distinct from the
 * {@code consumed_event} (AMQP redelivery) and {@code inbox_message} (webhook redelivery)
 * mechanisms, which solve different problems (see roadmap).
 *
 * <p>{@code (scope, idempotencyKey)} has a unique DB constraint. The reserving call's INSERT
 * holds a row lock until its transaction commits or rolls back; a concurrent duplicate blocks on
 * {@link IdempotencyRecordRepository#findForUpdate} until that resolves, then either replays the
 * committed response or — if the first attempt rolled back — retries reservation itself.
 */
@Service
public class IdempotencyService {

    private static final int MAX_RESERVE_ATTEMPTS = 3;

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public <T> T execute(
            String scope, String idempotencyKey, String requestHash, Class<T> responseType, Supplier<T> operation) {
        for (int attempt = 0; attempt < MAX_RESERVE_ATTEMPTS; attempt++) {
            int inserted = repository.tryReserve(UUID.randomUUID(), scope, idempotencyKey, requestHash);
            if (inserted == 1) {
                return runAndComplete(scope, idempotencyKey, operation);
            }

            Optional<IdempotencyRecord> existing = repository.findForUpdate(scope, idempotencyKey);
            if (existing.isEmpty()) {
                continue; // the racing attempt rolled back and its reservation was undone; retry
            }

            IdempotencyRecord record = existing.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyConflictException(scope, idempotencyKey);
            }
            if (record.getStatus() == IdempotencyStatus.COMPLETED) {
                return deserialize(record.getResponseBody(), responseType);
            }
            throw new IdempotencyKeyInProgressException(scope, idempotencyKey);
        }
        throw new IdempotencyKeyInProgressException(scope, idempotencyKey);
    }

    private <T> T runAndComplete(String scope, String idempotencyKey, Supplier<T> operation) {
        T response = operation.get();
        IdempotencyRecord record =
                repository
                        .findForUpdate(scope, idempotencyKey)
                        .orElseThrow(() -> new IllegalStateException("Reserved idempotency record vanished"));
        record.markCompleted(serialize(response));
        repository.save(record);
        return response;
    }

    private String serialize(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotent response", e);
        }
    }

    private <T> T deserialize(String responseBody, Class<T> responseType) {
        try {
            return objectMapper.readValue(responseBody, responseType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize cached idempotent response", e);
        }
    }
}
