package io.github.derrickmunyole.loandecisioning.decisioning;

import java.util.UUID;

/** Shared across {@code PolicyVersion}/{@code ScorecardVersion}/{@code PricingVersion} — all three admin resources fail the same way on a missing id. */
class VersionNotFoundException extends RuntimeException {

    VersionNotFoundException(String resourceType, UUID id) {
        super(resourceType + " " + id + " not found");
    }
}
