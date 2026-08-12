package io.github.derrickmunyole.loandecisioning.origination.consent;

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

/** Captured atomically with the {@code ApplicationVersion} it belongs to, at submit. Insert-only. */
@Entity
@Getter
@Table(name = "consent")
public class Consent {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "application_version_id", nullable = false)
    private UUID applicationVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false)
    private ConsentType consentType;

    @Column(name = "consent_version", nullable = false)
    private String consentVersion;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    protected Consent() {}

    public Consent(UUID applicationVersionId, ConsentType consentType, String consentVersion) {
        this.applicationVersionId = applicationVersionId;
        this.consentType = consentType;
        this.consentVersion = consentVersion;
        this.acceptedAt = Instant.now();
    }
}
