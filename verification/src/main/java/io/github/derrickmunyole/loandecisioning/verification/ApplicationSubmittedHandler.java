package io.github.derrickmunyole.loandecisioning.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.derrickmunyole.loandecisioning.infrastructure.api.AmqpDedupeService;
import io.github.derrickmunyole.loandecisioning.origination.api.ApplicationSubmittedEvent;
import io.github.derrickmunyole.loandecisioning.origination.api.ApplicationTransitionService;
import io.github.derrickmunyole.loandecisioning.origination.api.ApplicationVersionQueryService;
import io.github.derrickmunyole.loandecisioning.origination.api.ApplicationVersionView;
import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Split out from {@link ApplicationSubmittedListener} for the same ack-after-commit reason as
 * {@code NotificationRequestedHandler} (ADR 0004).
 *
 * <p>Per Epic 2.3's design, this is the single consumer for the whole verification step — it owns
 * both the {@code SUBMITTED -> VERIFYING} and {@code VERIFYING -> UNDERWRITING} hops in one
 * transaction, rather than publishing separate {@code verification.requested}/{@code
 * verification.completed} events. Doing both hops atomically also happens to be required for
 * retry correctness: if the transient-failure trigger throws, the whole transaction — including
 * the VERIFYING hop — rolls back, so a redelivered attempt can safely re-validate {@code
 * SUBMITTED -> VERIFYING} instead of finding the application already past that state.
 */
@Service
class ApplicationSubmittedHandler {

    static final String CONSUMER_NAME = "application-submitted-listener";

    private final AmqpDedupeService amqpDedupeService;
    private final ApplicationTransitionService applicationTransitionService;
    private final ApplicationVersionQueryService applicationVersionQueryService;
    private final VerificationAttemptTracker verificationAttemptTracker;
    private final VerificationCaseRepository verificationCaseRepository;
    private final SyntheticVerificationEngine syntheticVerificationEngine;
    private final ObjectMapper objectMapper;

    ApplicationSubmittedHandler(
            AmqpDedupeService amqpDedupeService,
            ApplicationTransitionService applicationTransitionService,
            ApplicationVersionQueryService applicationVersionQueryService,
            VerificationAttemptTracker verificationAttemptTracker,
            VerificationCaseRepository verificationCaseRepository,
            SyntheticVerificationEngine syntheticVerificationEngine,
            ObjectMapper objectMapper) {
        this.amqpDedupeService = amqpDedupeService;
        this.applicationTransitionService = applicationTransitionService;
        this.applicationVersionQueryService = applicationVersionQueryService;
        this.verificationAttemptTracker = verificationAttemptTracker;
        this.verificationCaseRepository = verificationCaseRepository;
        this.syntheticVerificationEngine = syntheticVerificationEngine;
        this.objectMapper = objectMapper;
    }

    @Transactional
    void process(Message message) throws IOException {
        UUID eventId = UUID.fromString((String) message.getMessageProperties().getHeader("eventId"));
        if (amqpDedupeService.alreadyConsumed(CONSUMER_NAME, eventId)) {
            return;
        }

        ApplicationSubmittedEvent event =
                objectMapper.readValue(message.getBody(), ApplicationSubmittedEvent.class);

        applicationTransitionService.transitionTo(event.applicationId(), ApplicationStatus.VERIFYING);

        ApplicationVersionView version =
                applicationVersionQueryService
                        .findByApplicationIdAndVersionNumber(event.applicationId(), event.versionNumber())
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "No ApplicationVersion "
                                                        + event.versionNumber()
                                                        + " for application "
                                                        + event.applicationId()));

        if (syntheticVerificationEngine.isTransientFailureTrigger(version.declaredEmployerName())) {
            int attempt = verificationAttemptTracker.recordAttemptAndGetCount(event.applicationId());
            if (attempt < 2) {
                throw new SimulatedTransientVerificationFailureException(event.applicationId());
            }
        }

        VerificationOutcome identity = syntheticVerificationEngine.checkIdentity(version.declaredEmployerName());
        verificationCaseRepository.save(
                new VerificationCase(
                        event.applicationId(),
                        version.id(),
                        SyntheticVerificationEngine.PROVIDER,
                        VerificationType.IDENTITY,
                        identity.status(),
                        identity.detail()));

        VerificationOutcome income =
                syntheticVerificationEngine.checkIncome(
                        version.requestedAmountKes(), version.requestedTermMonths(), version.declaredMonthlyIncomeKes());
        verificationCaseRepository.save(
                new VerificationCase(
                        event.applicationId(),
                        version.id(),
                        SyntheticVerificationEngine.PROVIDER,
                        VerificationType.INCOME,
                        income.status(),
                        income.detail()));

        applicationTransitionService.transitionTo(event.applicationId(), ApplicationStatus.UNDERWRITING);

        amqpDedupeService.markConsumed(CONSUMER_NAME, eventId);
    }
}