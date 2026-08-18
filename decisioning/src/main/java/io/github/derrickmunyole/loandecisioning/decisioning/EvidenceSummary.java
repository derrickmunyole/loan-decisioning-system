package io.github.derrickmunyole.loandecisioning.decisioning;

import io.github.derrickmunyole.loandecisioning.verification.api.VerificationCaseView;
import java.time.Instant;
import java.util.UUID;

/** The "evidence references" piece of blueprint §11's timeline sentence. */
record EvidenceSummary(UUID id, String provider, String type, String status, String detail, Instant createdAt) {

    static EvidenceSummary from(VerificationCaseView view) {
        return new EvidenceSummary(
                view.id(), view.provider(), view.type(), view.status(), view.detail(), view.createdAt());
    }
}
