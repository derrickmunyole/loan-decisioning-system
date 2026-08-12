package io.github.derrickmunyole.loandecisioning.infrastructure.idempotency;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    /**
     * Returns 1 if this call reserved the key, 0 if a row for (scope, key) already exists.
     * {@code ON CONFLICT DO NOTHING} never throws, so it can't poison the surrounding
     * transaction the way a caught unique-constraint violation would.
     */
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO idempotency_key (id, scope, idempotency_key, request_hash, status, created_at)
                    VALUES (:id, :scope, :key, :requestHash, 'IN_PROGRESS', now())
                    ON CONFLICT (scope, idempotency_key) DO NOTHING
                    """,
            nativeQuery = true)
    int tryReserve(
            @Param("id") UUID id,
            @Param("scope") String scope,
            @Param("key") String key,
            @Param("requestHash") String requestHash);

    /** Blocks until any in-flight reservation for this key commits or rolls back. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from IdempotencyRecord r where r.scope = :scope and r.idempotencyKey = :key")
    Optional<IdempotencyRecord> findForUpdate(@Param("scope") String scope, @Param("key") String key);
}
