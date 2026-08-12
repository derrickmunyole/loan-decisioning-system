package io.github.derrickmunyole.loandecisioning.origination.application;

/**
 * Only DRAFT and SUBMITTED exist until Epic 2.1's WorkflowTransitionService takes over; the full
 * state model in docs/blueprint.md's application lifecycle section is not enforced here.
 */
public enum ApplicationStatus {
    DRAFT,
    SUBMITTED
}
