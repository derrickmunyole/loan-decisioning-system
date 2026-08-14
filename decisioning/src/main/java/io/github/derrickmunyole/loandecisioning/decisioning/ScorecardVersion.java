package io.github.derrickmunyole.loandecisioning.decisioning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * The deterministic weighted-band scoring formula (blueprint §5), draft → published, immutable
 * once published. Same lifecycle shape as {@link PolicyVersion}; no update path once created —
 * the only legal mutation is {@link #publish()}.
 */
@Entity
@Getter
@Table(name = "scorecard_version")
public class ScorecardVersion {

    @Id
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VersionStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "formula_config_json", nullable = false, columnDefinition = "jsonb")
    private String formulaConfigJson;

    @Column(nullable = false)
    private String checksum;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected ScorecardVersion() {}

    public ScorecardVersion(String formulaConfigJson, String checksum) {
        this.status = VersionStatus.DRAFT;
        this.formulaConfigJson = formulaConfigJson;
        this.checksum = checksum;
        this.createdAt = Instant.now();
    }

    void publish() {
        if (status != VersionStatus.DRAFT) {
            throw new VersionAlreadyPublishedException("ScorecardVersion", id);
        }
        this.status = VersionStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }
}
