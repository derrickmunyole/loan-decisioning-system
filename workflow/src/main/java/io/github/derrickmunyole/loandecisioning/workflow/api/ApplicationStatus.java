package io.github.derrickmunyole.loandecisioning.workflow.api;

/**
 * The full {@code Application} lifecycle from {@code docs/blueprint.md} §4. Legal transitions
 * between these are enforced by {@link WorkflowTransitionService}, not by this enum itself.
 */
public enum ApplicationStatus {
    DRAFT,
    SUBMITTED,
    VERIFYING,
    UNDERWRITING,
    APPROVED,
    DECLINED,
    REFERRED,
    CONDITIONAL_APPROVAL,
    OFFERED,
    ACCEPTED,
    FUNDING_PENDING,
    FUNDED,
    ACTIVE,
    FUNDING_FAILED,
    OFFER_EXPIRED,
    WITHDRAWN
}
