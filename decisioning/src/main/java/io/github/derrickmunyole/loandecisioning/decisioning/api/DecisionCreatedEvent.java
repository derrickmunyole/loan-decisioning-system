package io.github.derrickmunyole.loandecisioning.decisioning.api;

import io.github.derrickmunyole.loandecisioning.common.OutboxPayload;
import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import java.util.UUID;

/**
 * The blueprint's own §7 event catalog names this one — {@code decision.created} — but nothing
 * published it until Epic 5.1, since nothing consumed it until {@code offers} did. Published from
 * both places a {@code Decision} row is saved: {@code DecisionEngineHandler.recordDecision} (the
 * automated path) and {@code CaseDecisionCommandService.decide}'s override branch (Epic 4.1). Not
 * scoped to approval outcomes — carries whatever {@code outcome} the saved {@code Decision} has,
 * the same "publish the fact, let the consumer decide what matters" shape {@code
 * application.submitted} already established; {@code offers}'s own listener is the one that
 * filters to {@code APPROVED}/{@code CONDITIONAL_APPROVAL}.
 */
public record DecisionCreatedEvent(UUID decisionId, UUID applicationId, ApplicationStatus outcome)
        implements OutboxPayload {

    @Override
    public String eventType() {
        return "decision.created";
    }

    @Override
    public String aggregateType() {
        return "Application";
    }

    @Override
    public UUID aggregateId() {
        return applicationId;
    }
}
