package io.github.derrickmunyole.loandecisioning.decisioning.api;

import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-only view of a {@code Decision} row, exposed for modules that need it without reaching
 * into {@code decisioning}'s internal entity — the same shape {@code verification.api}'s {@code
 * VerificationCaseView} already establishes. {@code reasons} is parsed out of the entity's raw
 * {@code reason_codes_json} column here, at the port boundary, so callers never need their own
 * {@code ObjectMapper} just to read one field.
 */
public record DecisionView(
        UUID id,
        UUID applicationId,
        UUID underwritingSnapshotId,
        UUID policyVersionId,
        UUID scorecardVersionId,
        UUID pricingVersionId,
        String creditScoreModelVersion,
        ApplicationStatus outcome,
        List<String> reasons,
        String actor,
        Instant decidedAt,
        UUID overridesDecisionId) {}
