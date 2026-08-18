package io.github.derrickmunyole.loandecisioning.decisioning;

import java.util.List;
import java.util.UUID;

/**
 * The staff-facing aggregate blueprint §11 calls for: "every screen or API response should link to
 * the decision version, reasons, evidence references, event timeline, and audit history" — four
 * named pieces, kept as four distinct sections rather than one merged, sorted list, since each
 * section draws from a different module's own data (decisions from {@code decisioning} itself,
 * evidence from {@code verification.api}, events from the comprehensive post-Epic-4.3 audit feed).
 * Applicant callers never see this shape — they get the narrower {@link TimelineEvent} list
 * instead, same as before this epic.
 */
record ApplicationTimelineResponse(
        UUID applicationId, List<DecisionSummary> decisions, List<EvidenceSummary> evidence, List<TimelineEvent> events) {}
