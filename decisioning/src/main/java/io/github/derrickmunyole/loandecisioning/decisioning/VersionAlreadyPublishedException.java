package io.github.derrickmunyole.loandecisioning.decisioning;

import java.util.UUID;

/** Shared across {@code PolicyVersion}/{@code ScorecardVersion}/{@code PricingVersion} — publishing an already-published row is the same illegal move for all three. */
class VersionAlreadyPublishedException extends RuntimeException {

    VersionAlreadyPublishedException(String resourceType, UUID id) {
        super(resourceType + " " + id + " is already published");
    }
}
