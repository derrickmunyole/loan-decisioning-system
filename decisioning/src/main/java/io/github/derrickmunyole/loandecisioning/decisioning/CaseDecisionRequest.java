package io.github.derrickmunyole.loandecisioning.decisioning;

import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code outcome} is intentionally the same {@link ApplicationStatus} enum the rest of the state
 * machine uses rather than a bespoke "case outcome" type — {@link
 * io.github.derrickmunyole.loandecisioning.workflow.api.WorkflowTransitionService}'s existing
 * {@code REFERRED} table entries ({@code UNDERWRITING}/{@code APPROVED}/{@code DECLINED}) are
 * already the single source of truth for which values are legal here; any other value fails that
 * check with the same 409 an illegal automated transition would.
 */
record CaseDecisionRequest(@NotNull ApplicationStatus outcome, @NotBlank String reason) {}
