package io.github.derrickmunyole.loandecisioning.origination.application;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.OutboxEventPublisher;
import io.github.derrickmunyole.loandecisioning.infrastructure.api.Audited;
import io.github.derrickmunyole.loandecisioning.origination.api.ApplicationNotFoundException;
import io.github.derrickmunyole.loandecisioning.origination.api.ApplicationSubmittedEvent;
import io.github.derrickmunyole.loandecisioning.origination.applicant.Applicant;
import io.github.derrickmunyole.loandecisioning.origination.applicant.ApplicantRepository;
import io.github.derrickmunyole.loandecisioning.origination.consent.Consent;
import io.github.derrickmunyole.loandecisioning.origination.consent.ConsentRepository;
import io.github.derrickmunyole.loandecisioning.origination.consent.ConsentType;
import io.github.derrickmunyole.loandecisioning.origination.document.Document;
import io.github.derrickmunyole.loandecisioning.origination.document.DocumentRepository;
import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import io.github.derrickmunyole.loandecisioning.workflow.api.WorkflowTransitionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Holds the mutation methods that must always be invoked through the Spring-managed proxy (never
 * self-invoked) so {@code @Audited}'s AOP advice actually fires — split out from {@link
 * ApplicationService} because that class calls into {@link
 * io.github.derrickmunyole.loandecisioning.infrastructure.api.IdempotencyService}, and a
 * self-invoked call from within the same bean would bypass the proxy entirely.
 */
@Service
class ApplicationCommandService {

    private static final String CONSENT_VERSION = "1.0";
    private static final int FIRST_VERSION_NUMBER = 1;

    private final ApplicantRepository applicantRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationVersionRepository applicationVersionRepository;
    private final ConsentRepository consentRepository;
    private final DocumentRepository documentRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final WorkflowTransitionService workflowTransitionService;

    ApplicationCommandService(
            ApplicantRepository applicantRepository,
            ApplicationRepository applicationRepository,
            ApplicationVersionRepository applicationVersionRepository,
            ConsentRepository consentRepository,
            DocumentRepository documentRepository,
            OutboxEventPublisher outboxEventPublisher,
            WorkflowTransitionService workflowTransitionService) {
        this.applicantRepository = applicantRepository;
        this.applicationRepository = applicationRepository;
        this.applicationVersionRepository = applicationVersionRepository;
        this.consentRepository = consentRepository;
        this.documentRepository = documentRepository;
        this.outboxEventPublisher = outboxEventPublisher;
        this.workflowTransitionService = workflowTransitionService;
    }

    @Transactional
    @Audited(action = "APPLICATION_CREATED", targetType = "Application", targetId = "#applicationId")
    ApplicationResponse create(String username, UUID applicationId, CreateApplicationRequest request) {
        Applicant applicant =
                applicantRepository
                        .findByUsername(username)
                        .orElseGet(
                                () ->
                                        applicantRepository.save(
                                                new Applicant(
                                                        username,
                                                        request.fullName(),
                                                        request.email(),
                                                        request.phone())));
        Application application =
                applicationRepository.save(new Application(applicationId, applicant.getId()));
        return ApplicationResponse.from(application);
    }

    @Transactional
    @Audited(action = "APPLICATION_SUBMITTED", targetType = "Application", targetId = "#applicationId")
    ApplicationResponse submit(UUID applicationId, SubmitApplicationRequest request) {
        Application application =
                applicationRepository
                        .findById(applicationId)
                        .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        if (application.getRequestedAmountKes() == null
                || application.getRequestedTermMonths() == null
                || application.getDeclaredMonthlyIncomeKes() == null
                || application.getDeclaredEmploymentStatus() == null) {
            throw new InvalidApplicationDataException(
                    "Application " + applicationId + " is missing required draft fields");
        }

        workflowTransitionService.validateTransition(application.getStatus(), ApplicationStatus.SUBMITTED);
        application.transitionTo(ApplicationStatus.SUBMITTED);
        application.setCurrentVersionNumber(FIRST_VERSION_NUMBER);

        ApplicationVersion version =
                applicationVersionRepository.save(
                        new ApplicationVersion(
                                applicationId,
                                FIRST_VERSION_NUMBER,
                                application.getRequestedAmountKes(),
                                application.getRequestedTermMonths(),
                                application.getDeclaredMonthlyIncomeKes(),
                                application.getDeclaredEmploymentStatus(),
                                application.getDeclaredEmployerName(),
                                application.getLoanPurpose()));

        consentRepository.save(new Consent(version.getId(), ConsentType.DATA_PROCESSING, CONSENT_VERSION));
        consentRepository.save(new Consent(version.getId(), ConsentType.CREDIT_CHECK, CONSENT_VERSION));

        for (Document document : documentRepository.findByApplicationId(applicationId)) {
            document.attachToVersion(version.getId());
            documentRepository.save(document);
        }

        outboxEventPublisher.enqueue(
                new ApplicationSubmittedEvent(
                        applicationId, application.getApplicantId(), FIRST_VERSION_NUMBER));

        // The verification module's application.submitted consumer (Epic 2.3) now owns the
        // SUBMITTED->VERIFYING->UNDERWRITING hops, atomically, once it processes this event.
        return ApplicationResponse.from(application);
    }
}
