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
 * APR/term pricing rules (blueprint §5), kept separate from {@link PolicyVersion} on purpose —
 * risk eligibility and pricing can be republished independently of each other. Draft → published,
 * immutable once published; same lifecycle shape as {@link PolicyVersion}/{@link
 * ScorecardVersion}.
 */
@Entity
@Getter
@Table(name = "pricing_version")
public class PricingVersion {

    @Id
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VersionStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "apr_term_rules_json", nullable = false, columnDefinition = "jsonb")
    private String aprTermRulesJson;

    @Column(nullable = false)
    private String checksum;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected PricingVersion() {}

    public PricingVersion(String aprTermRulesJson, String checksum) {
        this.status = VersionStatus.DRAFT;
        this.aprTermRulesJson = aprTermRulesJson;
        this.checksum = checksum;
        this.createdAt = Instant.now();
    }

    void publish() {
        if (status != VersionStatus.DRAFT) {
            throw new VersionAlreadyPublishedException("PricingVersion", id);
        }
        this.status = VersionStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }
}
