package io.github.derrickmunyole.loandecisioning.verification.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only view of a completed {@code VerificationCase}, exposed for modules that need the
 * evidence without reaching into {@code verification}'s internal entities — e.g. {@code
 * decisioning}, which folds this into the immutable {@code UnderwritingSnapshot}. {@code type}
 * and {@code status} are plain strings (not the internal enums) so this type stays fully
 * self-contained across the module boundary, matching {@code origination.api}'s {@code
 * ApplicationVersionView}.
 */
public record VerificationCaseView(
        UUID id,
        UUID applicationId,
        UUID applicationVersionId,
        String provider,
        String type,
        String status,
        String detail,
        Instant createdAt) {}
