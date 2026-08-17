package io.github.derrickmunyole.loandecisioning.decisioning;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.IdempotencyService;
import io.github.derrickmunyole.loandecisioning.infrastructure.api.RequestHash;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CaseDecisionService {

    private static final String SCOPE = "case.decision";

    private final IdempotencyService idempotencyService;
    private final CaseDecisionCommandService caseDecisionCommandService;

    public CaseDecisionService(
            IdempotencyService idempotencyService, CaseDecisionCommandService caseDecisionCommandService) {
        this.idempotencyService = idempotencyService;
        this.caseDecisionCommandService = caseDecisionCommandService;
    }

    public CaseDecisionResponse decide(
            String actor, UUID applicationId, String idempotencyKey, CaseDecisionRequest request) {
        String requestHash =
                RequestHash.of(applicationId.toString(), request.outcome().name(), request.reason());
        return idempotencyService.execute(
                SCOPE + ":" + applicationId,
                idempotencyKey,
                requestHash,
                CaseDecisionResponse.class,
                () -> caseDecisionCommandService.decide(actor, applicationId, request));
    }
}
