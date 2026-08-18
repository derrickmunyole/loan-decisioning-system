package io.github.derrickmunyole.loandecisioning.decisioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.derrickmunyole.loandecisioning.decisioning.api.UnderwritingSnapshotCreatedEvent;
import io.github.derrickmunyole.loandecisioning.infrastructure.api.AmqpDedupeService;
import io.github.derrickmunyole.loandecisioning.origination.api.ApplicationTransitionService;
import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import io.github.derrickmunyole.loandecisioning.workflow.api.WorkflowTaskCreationService;
import io.github.derrickmunyole.loandecisioning.workflow.api.WorkflowTaskType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Split out from {@link UnderwritingSnapshotCreatedListener} for the same ack-after-commit reason
 * as {@code NotificationRequestedHandler} (ADR 0004). Own transaction, own dedupe, own thread —
 * deliberately separate from {@link UnderwritingRequestedHandler} (Epic 3.1) so the credit-score
 * call (ADR 0008) never runs while a DB transaction is held open.
 *
 * <p>Three distinct "can't produce a normal automated decision" paths, each handled differently
 * on purpose:
 *
 * <ul>
 *   <li>No published {@code PolicyVersion}/{@code ScorecardVersion}/{@code PricingVersion} —
 *       treated as a genuine platform-configuration gap, not a per-application problem. Left to
 *       throw and go through the existing retry → DLQ → {@code workflow_task} path (Epic 2.2)
 *       rather than inventing a bespoke status transition for a case that isn't the application's
 *       fault at all.
 *   <li>Verification evidence contains a failed check — a real, expected business outcome (the
 *       sentinel triggers in {@code SyntheticVerificationEngine} exist precisely so this path is
 *       reachable) — or the credit-score band itself maps to {@code REFERRED} per the published
 *       {@code PolicyVersion}'s {@code bandOutcomes}: either way, {@code recordDecision} raises a
 *       {@code WorkflowTaskType.UNDERWRITE_CASE} task (Epic 4.1) whenever it saves a {@code
 *       REFERRED} {@code Decision}, so the case surfaces on the underwriter's {@code GET
 *       /work-queue}, not operations', regardless of which of the two paths produced it.
 *   <li>The credit-score provider call fails (timeout, circuit open, non-2xx, connection
 *       failure) — {@code REFERRED}, no {@code Decision} row (there's no valid score/model
 *       version to record), plus an ops-visible {@code workflow_task} via the new {@code
 *       WorkflowTaskCreationService} port. This one gets bespoke handling, not the generic DLQ
 *       path, because the roadmap's Epic 3.4 done-criterion names it explicitly.
 * </ul>
 */
@Service
class DecisionEngineHandler {

    static final String CONSUMER_NAME = "decision-engine-listener";
    // Matches SystemServicePrincipalAspect's synthetic principal username (lowercase).
    private static final String ACTOR = "system_service";
    private static final String FAILED_EVIDENCE_STATUS = "FAILED";

    private final AmqpDedupeService amqpDedupeService;
    private final UnderwritingSnapshotRepository underwritingSnapshotRepository;
    private final PolicyVersionRepository policyVersionRepository;
    private final ScorecardVersionRepository scorecardVersionRepository;
    private final PricingVersionRepository pricingVersionRepository;
    private final DecisionRepository decisionRepository;
    private final CreditScoreClient creditScoreClient;
    private final ApplicationTransitionService applicationTransitionService;
    private final WorkflowTaskCreationService workflowTaskCreationService;
    private final ObjectMapper objectMapper;

    DecisionEngineHandler(
            AmqpDedupeService amqpDedupeService,
            UnderwritingSnapshotRepository underwritingSnapshotRepository,
            PolicyVersionRepository policyVersionRepository,
            ScorecardVersionRepository scorecardVersionRepository,
            PricingVersionRepository pricingVersionRepository,
            DecisionRepository decisionRepository,
            CreditScoreClient creditScoreClient,
            ApplicationTransitionService applicationTransitionService,
            WorkflowTaskCreationService workflowTaskCreationService,
            ObjectMapper objectMapper) {
        this.amqpDedupeService = amqpDedupeService;
        this.underwritingSnapshotRepository = underwritingSnapshotRepository;
        this.policyVersionRepository = policyVersionRepository;
        this.scorecardVersionRepository = scorecardVersionRepository;
        this.pricingVersionRepository = pricingVersionRepository;
        this.decisionRepository = decisionRepository;
        this.creditScoreClient = creditScoreClient;
        this.applicationTransitionService = applicationTransitionService;
        this.workflowTaskCreationService = workflowTaskCreationService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    void process(Message message) throws IOException {
        UUID eventId = UUID.fromString((String) message.getMessageProperties().getHeader("eventId"));
        if (amqpDedupeService.alreadyConsumed(CONSUMER_NAME, eventId)) {
            return;
        }

        UnderwritingSnapshotCreatedEvent event =
                objectMapper.readValue(message.getBody(), UnderwritingSnapshotCreatedEvent.class);

        UnderwritingSnapshot snapshot =
                underwritingSnapshotRepository
                        .findByApplicationVersionId(event.applicationVersionId())
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "No UnderwritingSnapshot for application version "
                                                        + event.applicationVersionId()));
        UnderwritingFacts facts = objectMapper.readValue(snapshot.getFactsJson(), UnderwritingFacts.class);

        PolicyVersion policy =
                policyVersionRepository
                        .findFirstByStatusOrderByPublishedAtDesc(VersionStatus.PUBLISHED)
                        .orElseThrow(() -> new NoSuchElementException("No published PolicyVersion"));
        ScorecardVersion scorecard =
                scorecardVersionRepository
                        .findFirstByStatusOrderByPublishedAtDesc(VersionStatus.PUBLISHED)
                        .orElseThrow(() -> new NoSuchElementException("No published ScorecardVersion"));
        PricingVersion pricing =
                pricingVersionRepository
                        .findFirstByStatusOrderByPublishedAtDesc(VersionStatus.PUBLISHED)
                        .orElseThrow(() -> new NoSuchElementException("No published PricingVersion"));

        boolean verificationFailed =
                facts.evidence().stream()
                        .anyMatch(item -> FAILED_EVIDENCE_STATUS.equals(item.status()));
        if (verificationFailed) {
            recordDecision(
                    event.applicationId(),
                    snapshot.getId(),
                    policy.getId(),
                    scorecard.getId(),
                    pricing.getId(),
                    null,
                    ApplicationStatus.REFERRED,
                    List.of("Verification evidence contains a failed check; referring for manual review"));
            amqpDedupeService.markConsumed(CONSUMER_NAME, eventId);
            return;
        }

        CreditScoreResponse scoreResponse;
        try {
            scoreResponse =
                    creditScoreClient.score(
                            facts.requestedAmountKes(),
                            facts.requestedTermMonths(),
                            facts.declaredMonthlyIncomeKes(),
                            facts.declaredEmploymentStatus());
        } catch (Exception e) {
            applicationTransitionService.transitionTo(event.applicationId(), ApplicationStatus.REFERRED);
            workflowTaskCreationService.createTask(
                    WorkflowTaskType.CREDIT_SCORE_PROVIDER_UNAVAILABLE,
                    event.applicationId(),
                    "CREDIT_SCORE_PROVIDER_UNAVAILABLE",
                    "Application " + event.applicationId() + ": " + e.getMessage(),
                    null);
            amqpDedupeService.markConsumed(CONSUMER_NAME, eventId);
            return;
        }

        Map<String, Integer> bandCutoffs = parseBandCutoffs(scorecard.getFormulaConfigJson());
        PolicyRulesConfig rules = parsePolicyRules(policy.getRulesJson());

        PolicyEvaluationResult evaluation =
                PolicyEvaluator.evaluate(
                        scoreResponse.score(),
                        facts.declaredEmploymentStatus(),
                        bandCutoffs,
                        rules.excludedEmploymentStatuses(),
                        rules.bandOutcomes());

        List<String> reasons = new ArrayList<>();
        for (CreditScoreResponse.ReasonContribution contribution : scoreResponse.reasonContributions()) {
            reasons.add(contribution.detail());
        }
        reasons.addAll(evaluation.reasons());

        recordDecision(
                event.applicationId(),
                snapshot.getId(),
                policy.getId(),
                scorecard.getId(),
                pricing.getId(),
                scoreResponse.modelVersion(),
                evaluation.outcome(),
                reasons);

        amqpDedupeService.markConsumed(CONSUMER_NAME, eventId);
    }

    private void recordDecision(
            UUID applicationId,
            UUID underwritingSnapshotId,
            UUID policyVersionId,
            UUID scorecardVersionId,
            UUID pricingVersionId,
            String creditScoreModelVersion,
            ApplicationStatus outcome,
            List<String> reasons) {
        try {
            String reasonCodesJson = objectMapper.writeValueAsString(reasons);
            decisionRepository.save(
                    new Decision(
                            applicationId,
                            underwritingSnapshotId,
                            policyVersionId,
                            scorecardVersionId,
                            pricingVersionId,
                            creditScoreModelVersion,
                            outcome,
                            reasonCodesJson,
                            ACTOR,
                            null));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize decision reason codes: " + reasons, e);
        }
        applicationTransitionService.transitionTo(applicationId, outcome);
        if (outcome == ApplicationStatus.REFERRED) {
            workflowTaskCreationService.createTask(
                    WorkflowTaskType.UNDERWRITE_CASE,
                    applicationId,
                    "UNDERWRITE_CASE",
                    "Application "
                            + applicationId
                            + " referred for manual underwriting review: "
                            + String.join("; ", reasons),
                    null);
        }
    }

    private Map<String, Integer> parseBandCutoffs(String formulaConfigJson) {
        try {
            return objectMapper.readValue(formulaConfigJson, ScorecardFormulaConfig.class).bandCutoffs();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to parse ScorecardVersion formulaConfig: " + formulaConfigJson, e);
        }
    }

    private PolicyRulesConfig parsePolicyRules(String rulesJson) {
        try {
            return objectMapper.readValue(rulesJson, PolicyRulesConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse PolicyVersion rules: " + rulesJson, e);
        }
    }

    private record ScorecardFormulaConfig(Map<String, Integer> bandCutoffs) {}

    private record PolicyRulesConfig(
            List<String> excludedEmploymentStatuses, Map<String, String> bandOutcomes) {}
}
