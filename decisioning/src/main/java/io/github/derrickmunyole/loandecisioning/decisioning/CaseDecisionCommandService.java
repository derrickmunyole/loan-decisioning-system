package io.github.derrickmunyole.loandecisioning.decisioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.derrickmunyole.loandecisioning.infrastructure.api.Audited;
import io.github.derrickmunyole.loandecisioning.origination.api.ApplicationTransitionService;
import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import io.github.derrickmunyole.loandecisioning.workflow.api.IllegalApplicationTransitionException;
import io.github.derrickmunyole.loandecisioning.workflow.api.WorkflowTaskResolutionService;
import io.github.derrickmunyole.loandecisioning.workflow.api.WorkflowTaskType;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds the mutation method that must always be invoked through the Spring-managed proxy so
 * {@code @Audited}'s AOP advice actually fires — split out from {@link CaseDecisionService} for
 * the same reason {@code ApplicationCommandService} is split from {@code ApplicationService}: that
 * class calls into {@link io.github.derrickmunyole.loandecisioning.infrastructure.api.IdempotencyService},
 * and a self-invoked call from within the same bean would bypass the proxy entirely.
 */
@Service
class CaseDecisionCommandService {

    private final DecisionRepository decisionRepository;
    private final ApplicationTransitionService applicationTransitionService;
    private final WorkflowTaskResolutionService workflowTaskResolutionService;
    private final ObjectMapper objectMapper;

    CaseDecisionCommandService(
            DecisionRepository decisionRepository,
            ApplicationTransitionService applicationTransitionService,
            WorkflowTaskResolutionService workflowTaskResolutionService,
            ObjectMapper objectMapper) {
        this.decisionRepository = decisionRepository;
        this.applicationTransitionService = applicationTransitionService;
        this.workflowTaskResolutionService = workflowTaskResolutionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @Audited(action = "CASE_DECISION_RECORDED", targetType = "Application", targetId = "#applicationId")
    CaseDecisionResponse decide(String actor, UUID applicationId, CaseDecisionRequest request) {
        // WorkflowTransitionService's shared table also permits UNDERWRITING -> APPROVED/DECLINED
        // for the automated engine's own use, so that table alone can't be the guard here — a
        // user-invoked request must not be able to ride that same edge to bypass the engine.
        ApplicationStatus current = applicationTransitionService.currentStatus(applicationId);
        if (current != ApplicationStatus.REFERRED) {
            throw new IllegalApplicationTransitionException(current, request.outcome());
        }

        if (request.outcome() == ApplicationStatus.UNDERWRITING) {
            applicationTransitionService.transitionTo(applicationId, ApplicationStatus.UNDERWRITING);
            resolveOpenUnderwriteCaseTask(applicationId, actor, request.outcome());
            return CaseDecisionResponse.evidenceRequested(applicationId, request.reason(), actor);
        }

        Decision automated =
                decisionRepository
                        .findFirstByApplicationIdOrderByDecidedAtDesc(applicationId)
                        .orElseThrow(() -> new NoAutomatedDecisionToOverrideException(applicationId));

        Decision override =
                decisionRepository.save(
                        new Decision(
                                applicationId,
                                automated.getUnderwritingSnapshotId(),
                                automated.getPolicyVersionId(),
                                automated.getScorecardVersionId(),
                                automated.getPricingVersionId(),
                                automated.getCreditScoreModelVersion(),
                                request.outcome(),
                                writeReasonCodesJson(request.reason()),
                                actor,
                                automated.getId()));
        applicationTransitionService.transitionTo(applicationId, request.outcome());
        resolveOpenUnderwriteCaseTask(applicationId, actor, request.outcome());
        return CaseDecisionResponse.fromOverride(override, request.reason());
    }

    /**
     * No-op if there's no open {@code UNDERWRITE_CASE} task for this application. That's the
     * normal case for a credit-score-provider-outage {@code REFERRED} (Epic 3.4 raises {@code
     * CREDIT_SCORE_PROVIDER_UNAVAILABLE} there instead) reached via this method's {@code
     * UNDERWRITING} branch — the only one of the three outcomes that doesn't require an automated
     * {@link Decision} to already exist.
     */
    private void resolveOpenUnderwriteCaseTask(UUID applicationId, String actor, ApplicationStatus outcome) {
        workflowTaskResolutionService
                .findOpenTask(applicationId, WorkflowTaskType.UNDERWRITE_CASE)
                .ifPresent(
                        task ->
                                workflowTaskResolutionService.markResolved(
                                        task.id(), "Resolved via POST /cases/{id}/decision (" + outcome + ")", actor));
    }

    private String writeReasonCodesJson(String reason) {
        try {
            return objectMapper.writeValueAsString(List.of(reason));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize case decision reason: " + reason, e);
        }
    }
}
