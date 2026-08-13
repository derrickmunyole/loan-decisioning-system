package io.github.derrickmunyole.loandecisioning.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Tracks how many times the synthetic transient-failure trigger has fired for a given
 * application. Written by {@link VerificationAttemptTracker} in its own {@code REQUIRES_NEW}
 * transaction so the count survives even when the caller's own transaction (which threw to
 * simulate the failure) rolls back.
 */
@Entity
@Getter
@Table(name = "verification_attempt_marker")
public class VerificationAttemptMarker {

    @Id
    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VerificationAttemptMarker() {}

    public VerificationAttemptMarker(UUID applicationId) {
        this.applicationId = applicationId;
        this.attemptCount = 0;
        this.updatedAt = Instant.now();
    }

    public void incrementAttempt() {
        this.attemptCount++;
        this.updatedAt = Instant.now();
    }
}
