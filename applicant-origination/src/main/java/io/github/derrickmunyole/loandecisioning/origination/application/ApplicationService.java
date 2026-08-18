package io.github.derrickmunyole.loandecisioning.origination.application;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.Audited;
import io.github.derrickmunyole.loandecisioning.infrastructure.api.IdempotencyService;
import io.github.derrickmunyole.loandecisioning.infrastructure.api.RequestHash;
import io.github.derrickmunyole.loandecisioning.origination.api.ApplicationNotFoundException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private static final String CREATE_SCOPE = "application.create";
    private static final String SUBMIT_SCOPE = "application.submit";
    private static final Set<Integer> ALLOWED_TERM_MONTHS = Set.of(12, 24, 36, 48);

    private final ApplicationRepository applicationRepository;
    private final ApplicationCommandService applicationCommandService;
    private final IdempotencyService idempotencyService;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            ApplicationCommandService applicationCommandService,
            IdempotencyService idempotencyService) {
        this.applicationRepository = applicationRepository;
        this.applicationCommandService = applicationCommandService;
        this.idempotencyService = idempotencyService;
    }

    public ApplicationResponse createDraft(
            String username, String idempotencyKey, CreateApplicationRequest request) {
        UUID applicationId = UUID.randomUUID();
        String requestHash =
                RequestHash.of(username, request.fullName(), request.email(), request.phone());
        return idempotencyService.execute(
                CREATE_SCOPE + ":" + username,
                idempotencyKey,
                requestHash,
                ApplicationResponse.class,
                () -> applicationCommandService.create(username, applicationId, request));
    }

    @Transactional
    @Audited(action = "APPLICATION_DRAFT_UPDATED", targetType = "Application", targetId = "#applicationId")
    public ApplicationResponse updateDraft(UUID applicationId, PatchApplicationRequest request) {
        if (!ALLOWED_TERM_MONTHS.contains(request.requestedTermMonths())) {
            throw new InvalidApplicationDataException(
                    "requestedTermMonths must be one of " + ALLOWED_TERM_MONTHS);
        }
        Application application =
                applicationRepository
                        .findById(applicationId)
                        .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        application.updateDraft(
                request.requestedAmountKes(),
                request.requestedTermMonths(),
                request.declaredMonthlyIncomeKes(),
                request.declaredEmploymentStatus(),
                request.declaredEmployerName(),
                request.loanPurpose());
        return ApplicationResponse.from(application);
    }

    public ApplicationResponse submit(
            UUID applicationId, String idempotencyKey, SubmitApplicationRequest request) {
        String requestHash =
                RequestHash.of(applicationId.toString(), String.valueOf(request.consentAccepted()));
        return idempotencyService.execute(
                SUBMIT_SCOPE + ":" + applicationId,
                idempotencyKey,
                requestHash,
                ApplicationResponse.class,
                () -> applicationCommandService.submit(applicationId, request));
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(UUID applicationId) {
        Application application =
                applicationRepository
                        .findById(applicationId)
                        .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        return ApplicationResponse.from(application);
    }
}
