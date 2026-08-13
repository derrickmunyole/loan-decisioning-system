package io.github.derrickmunyole.loandecisioning.verification;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A separate bean (never self-invoked) so {@code REQUIRES_NEW} actually starts a new transaction
 * via the Spring-managed proxy. The synthetic transient-failure trigger throws from inside the
 * caller's own transaction to force a redelivery — if the attempt count lived in that same
 * transaction, the rollback would erase it and the trigger would fire forever instead of
 * resolving on the next attempt.
 */
@Service
class VerificationAttemptTracker {

    private final VerificationAttemptMarkerRepository verificationAttemptMarkerRepository;

    VerificationAttemptTracker(VerificationAttemptMarkerRepository verificationAttemptMarkerRepository) {
        this.verificationAttemptMarkerRepository = verificationAttemptMarkerRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    int recordAttemptAndGetCount(UUID applicationId) {
        VerificationAttemptMarker marker =
                verificationAttemptMarkerRepository
                        .findByApplicationId(applicationId)
                        .orElseGet(() -> new VerificationAttemptMarker(applicationId));
        marker.incrementAttempt();
        verificationAttemptMarkerRepository.save(marker);
        return marker.getAttemptCount();
    }
}
