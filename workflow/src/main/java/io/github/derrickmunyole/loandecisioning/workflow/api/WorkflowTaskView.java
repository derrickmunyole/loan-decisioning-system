package io.github.derrickmunyole.loandecisioning.workflow.api;

import java.util.UUID;

/**
 * Read-only projection of a {@code WorkflowTask} for callers outside {@code workflow} — e.g.
 * {@code decisioning}'s retry flow, which needs to confirm an open {@code
 * CREDIT_SCORE_PROVIDER_UNAVAILABLE} task exists for an application before acting on it, without
 * reaching into the internal {@code WorkflowTask} entity/{@code WorkflowTaskRepository} directly.
 * No {@code status} field — {@code WorkflowTaskStatus} itself is still internal to {@code
 * workflow.workqueue}, and every method that returns this view already filters to {@code OPEN} by
 * construction, so an external caller never needs to inspect it.
 */
public record WorkflowTaskView(UUID id, WorkflowTaskType taskType, UUID applicationId) {}
