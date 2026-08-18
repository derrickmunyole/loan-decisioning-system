package io.github.derrickmunyole.loandecisioning.decisioning;

import io.github.derrickmunyole.loandecisioning.decisioning.api.UnderwritingSnapshotCreatedEvent;
import io.github.derrickmunyole.loandecisioning.infrastructure.api.Audited;
import io.github.derrickmunyole.loandecisioning.infrastructure.api.OutboxEventPublisher;
import io.github.derrickmunyole.loandecisioning.origination.api.ApplicationTransitionService;
import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import io.github.derrickmunyole.loandecisioning.workflow.api.IllegalApplicationTransitionException;
import io.github.derrickmunyole.loandecisioning.workflow.api.WorkflowTaskResolutionService;
import io.github.derrickmunyole.loandecisioning.workflow.api.WorkflowTaskType;
import io.github.derrickmunyole.loandecisioning.workflow.api.WorkflowTaskView;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retries only the credit-score-provider-outage path {@link DecisionEngineHandler} raises — the
 * other way a case reaches {@code REFERRED} (a failed verification check) always records an
 * automated {@link Decision}, so it's the underwriter's {@code POST /cases/{id}/decision} to act
 * on, not this endpoint's job.
 */
@Service
class RetryDecisionCommandService {

    private static final String RESOLUTION = "Retried after credit-score provider outage";

    private final ApplicationTransitionService applicationTransitionService;
    private final WorkflowTaskResolutionService workflowTaskResolutionService;
    private final UnderwritingSnapshotRepository underwritingSnapshotRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    RetryDecisionCommandService(
            ApplicationTransitionService applicationTransitionService,
            WorkflowTaskResolutionService workflowTaskResolutionService,
            UnderwritingSnapshotRepository underwritingSnapshotRepository,
            OutboxEventPublisher outboxEventPublisher) {
        this.applicationTransitionService = applicationTransitionService;
        this.workflowTaskResolutionService = workflowTaskResolutionService;
        this.underwritingSnapshotRepository = underwritingSnapshotRepository;
        this.outboxEventPublisher = outboxEventPublisher;
    }

    @Transactional
    @Audited(action = "CASE_DECISION_RETRIED", targetType = "Application", targetId = "#applicationId")
    RetryDecisionResponse retry(String actor, UUID applicationId) {
        // Same reasoning as CaseDecisionCommandService: WorkflowTransitionService's shared table
        // isn't enough on its own here either, so the current-status check comes first.
        ApplicationStatus current = applicationTransitionService.currentStatus(applicationId);
        if (current != ApplicationStatus.REFERRED) {
            throw new IllegalApplicationTransitionException(current, ApplicationStatus.UNDERWRITING);
        }

        WorkflowTaskView task =
                workflowTaskResolutionService
                        .findOpenTask(applicationId, WorkflowTaskType.CREDIT_SCORE_PROVIDER_UNAVAILABLE)
                        .orElseThrow(() -> new NoRetryableProviderOutageTaskException(applicationId));

        UnderwritingSnapshot snapshot =
                underwritingSnapshotRepository.findByApplicationId(applicationId).stream()
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "No UnderwritingSnapshot for application " + applicationId));

        applicationTransitionService.transitionTo(applicationId, ApplicationStatus.UNDERWRITING);
        outboxEventPublisher.enqueue(
                new UnderwritingSnapshotCreatedEvent(applicationId, snapshot.getApplicationVersionId()));
        workflowTaskResolutionService.markResolved(task.id(), RESOLUTION, actor);

        return new RetryDecisionResponse(applicationId, ApplicationStatus.UNDERWRITING, Instant.now());
    }
}
