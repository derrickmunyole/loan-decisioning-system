package io.github.derrickmunyole.loandecisioning.decisioning;

import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import java.time.Instant;
import java.util.UUID;

record RetryDecisionResponse(UUID applicationId, ApplicationStatus status, Instant retriedAt) {}
