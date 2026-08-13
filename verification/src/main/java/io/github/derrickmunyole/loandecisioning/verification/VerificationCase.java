package io.github.derrickmunyole.loandecisioning.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

/**
 * One record of a single check (blueprint section 5). Append-only — a real provider integration
 * would carry a request/response reference here; the synthetic {@code provider} instead runs a
 * deterministic in-process check, so {@code detail} carries a human-readable reason instead.
 */
@Entity
@Getter
@Table(name = "verification_case")
public class VerificationCase {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "application_version_id", nullable = false)
    private UUID applicationVersionId;

    @Column(nullable = false)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus status;

    @Column(columnDefinition = "text")
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VerificationCase() {}

    public VerificationCase(
            UUID applicationId,
            UUID applicationVersionId,
            String provider,
            VerificationType type,
            VerificationStatus status,
            String detail) {
        this.applicationId = applicationId;
        this.applicationVersionId = applicationVersionId;
        this.provider = provider;
        this.type = type;
        this.status = status;
        this.detail = detail;
        this.createdAt = Instant.now();
    }
}
