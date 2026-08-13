package io.github.derrickmunyole.loandecisioning.verification;

import java.util.UUID;

/**
 * Thrown deliberately by {@link SyntheticVerificationEngine}'s transient-failure trigger to force
 * a redelivery — not a real error. Rolls back the whole handler transaction (including the
 * SUBMITTED -> VERIFYING hop) so the retried delivery re-validates that transition cleanly.
 */
class SimulatedTransientVerificationFailureException extends RuntimeException {

    SimulatedTransientVerificationFailureException(UUID applicationId) {
        super("Simulated transient verification failure for application " + applicationId);
    }
}
