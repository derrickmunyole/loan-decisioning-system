package io.github.derrickmunyole.loandecisioning.decisioning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Immutable input to every {@code Decision} (blueprint §5) — the declared application-version
 * fields plus verification evidence, frozen at the moment an application enters {@code
 * UNDERWRITING}. Insert-only, no update path. {@code application_version_id} carries the DB
 * unique constraint that is the real "exactly once per application" guarantee (see the {@code
 * decisioning} migration) — the existence check in {@link UnderwritingRequestedHandler} is a fast
 * path, not the source of truth.
 */
@Entity
@Getter
@Table(name = "underwriting_snapshot")
public class UnderwritingSnapshot {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "application_version_id", nullable = false, unique = true)
    private UUID applicationVersionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "facts_json", nullable = false, columnDefinition = "jsonb")
    private String factsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UnderwritingSnapshot() {}

    public UnderwritingSnapshot(UUID applicationId, UUID applicationVersionId, String factsJson) {
        this.applicationId = applicationId;
        this.applicationVersionId = applicationVersionId;
        this.factsJson = factsJson;
        this.createdAt = Instant.now();
    }
}
