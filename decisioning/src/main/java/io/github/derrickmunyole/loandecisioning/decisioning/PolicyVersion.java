package io.github.derrickmunyole.loandecisioning.decisioning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Declarative decision-eligibility rules (blueprint §5), draft → published, immutable once
 * published. No update path once created at all — even in {@code DRAFT}, the only legal mutation
 * is {@link #publish()}; a wrong draft is abandoned and recreated, not edited in place.
 */
@Entity
@Getter
@Table(name = "policy_version")
public class PolicyVersion {

    @Id
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VersionStatus status;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules_json", nullable = false, columnDefinition = "jsonb")
    private String rulesJson;

    @Column(nullable = false)
    private String checksum;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected PolicyVersion() {}

    public PolicyVersion(LocalDate effectiveDate, String rulesJson, String checksum) {
        this.status = VersionStatus.DRAFT;
        this.effectiveDate = effectiveDate;
        this.rulesJson = rulesJson;
        this.checksum = checksum;
        this.createdAt = Instant.now();
    }

    void publish() {
        if (status != VersionStatus.DRAFT) {
            throw new VersionAlreadyPublishedException("PolicyVersion", id);
        }
        this.status = VersionStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }
}
