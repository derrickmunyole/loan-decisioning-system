package io.github.derrickmunyole.loandecisioning.decisioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.derrickmunyole.loandecisioning.decisioning.api.UnderwritingSnapshotCreatedEvent;
import io.github.derrickmunyole.loandecisioning.infrastructure.api.AmqpDedupeService;
import io.github.derrickmunyole.loandecisioning.infrastructure.api.OutboxEventPublisher;
import io.github.derrickmunyole.loandecisioning.origination.api.ApplicationVersionQueryService;
import io.github.derrickmunyole.loandecisioning.origination.api.ApplicationVersionView;
import io.github.derrickmunyole.loandecisioning.verification.api.UnderwritingRequestedEvent;
import io.github.derrickmunyole.loandecisioning.verification.api.VerificationCaseView;
import io.github.derrickmunyole.loandecisioning.verification.api.VerificationEvidenceQueryService;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Split out from {@link UnderwritingRequestedListener} for the same ack-after-commit reason as
 * {@code NotificationRequestedHandler} (ADR 0004).
 *
 * <p>The {@code consumed_event} dedupe check below covers redelivery of the same message; the
 * {@code existsByApplicationVersionId} check plus the DB's own unique constraint on {@code
 * application_version_id} (see the {@code decisioning} migration) is the independent, real
 * backstop the roadmap's Epic 3.1 done-criterion asks for — it holds even against two distinct
 * {@code underwriting.requested} deliveries (different {@code eventId}) for the same application
 * version, which AMQP dedupe alone can't catch.
 *
 * <p>Epic 3.4 adds {@code underwriting.snapshot.created} — a blueprint-named event nothing
 * published until the decision engine existed to consume it — enqueued only on the branch that
 * actually just inserted the snapshot, not on a redelivery that finds one already there.
 */
@Service
class UnderwritingRequestedHandler {

    static final String CONSUMER_NAME = "underwriting-requested-listener";

    private final AmqpDedupeService amqpDedupeService;
    private final ApplicationVersionQueryService applicationVersionQueryService;
    private final VerificationEvidenceQueryService verificationEvidenceQueryService;
    private final UnderwritingSnapshotRepository underwritingSnapshotRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final ObjectMapper objectMapper;

    UnderwritingRequestedHandler(
            AmqpDedupeService amqpDedupeService,
            ApplicationVersionQueryService applicationVersionQueryService,
            VerificationEvidenceQueryService verificationEvidenceQueryService,
            UnderwritingSnapshotRepository underwritingSnapshotRepository,
            OutboxEventPublisher outboxEventPublisher,
            ObjectMapper objectMapper) {
        this.amqpDedupeService = amqpDedupeService;
        this.applicationVersionQueryService = applicationVersionQueryService;
        this.verificationEvidenceQueryService = verificationEvidenceQueryService;
        this.underwritingSnapshotRepository = underwritingSnapshotRepository;
        this.outboxEventPublisher = outboxEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    void process(Message message) throws IOException {
        UUID eventId = UUID.fromString((String) message.getMessageProperties().getHeader("eventId"));
        if (amqpDedupeService.alreadyConsumed(CONSUMER_NAME, eventId)) {
            return;
        }

        UnderwritingRequestedEvent event =
                objectMapper.readValue(message.getBody(), UnderwritingRequestedEvent.class);

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

        if (!underwritingSnapshotRepository.existsByApplicationVersionId(version.id())) {
            String factsJson = objectMapper.writeValueAsString(buildFacts(version, event.applicationId()));
            underwritingSnapshotRepository.save(
                    new UnderwritingSnapshot(event.applicationId(), version.id(), factsJson));
            outboxEventPublisher.enqueue(
                    new UnderwritingSnapshotCreatedEvent(event.applicationId(), version.id()));
        }

        amqpDedupeService.markConsumed(CONSUMER_NAME, eventId);
    }

    private UnderwritingFacts buildFacts(ApplicationVersionView version, UUID applicationId) {
        var evidence =
                verificationEvidenceQueryService.findByApplicationId(applicationId).stream()
                        .map(UnderwritingRequestedHandler::toEvidenceItem)
                        .toList();
        return new UnderwritingFacts(
                version.requestedAmountKes(),
                version.requestedTermMonths(),
                version.declaredMonthlyIncomeKes(),
                version.declaredEmploymentStatus(),
                version.declaredEmployerName(),
                version.loanPurpose(),
                evidence);
    }

    private static UnderwritingFacts.EvidenceItem toEvidenceItem(VerificationCaseView caseView) {
        return new UnderwritingFacts.EvidenceItem(
                caseView.type(), caseView.status(), caseView.provider(), caseView.detail());
    }
}
