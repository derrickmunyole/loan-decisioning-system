package io.github.derrickmunyole.loandecisioning.infrastructure.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Row-level lock on {@code (scope, idempotency_key)} is the mutex: the reserving transaction's
 * insert holds the lock until it commits or rolls back, so a concurrent duplicate blocks on
 * {@link IdempotencyRecordRepository#findForUpdate} until the first attempt resolves. See
 * {@link IdempotencyService} for the full protocol.
 */
@Entity
@Getter
@Table(name = "idempotency_key")
public class IdempotencyRecord {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String scope;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdempotencyStatus status;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected IdempotencyRecord() {}

    public void markCompleted(String responseBody) {
        this.status = IdempotencyStatus.COMPLETED;
        this.responseBody = responseBody;
        this.completedAt = Instant.now();
    }
}
