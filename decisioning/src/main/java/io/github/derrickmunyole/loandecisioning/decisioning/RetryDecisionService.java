package io.github.derrickmunyole.loandecisioning.decisioning;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.IdempotencyService;
import io.github.derrickmunyole.loandecisioning.infrastructure.api.RequestHash;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RetryDecisionService {

    private static final String SCOPE = "case.retry-decision";

    private final IdempotencyService idempotencyService;
    private final RetryDecisionCommandService retryDecisionCommandService;

    public RetryDecisionService(
            IdempotencyService idempotencyService, RetryDecisionCommandService retryDecisionCommandService) {
        this.idempotencyService = idempotencyService;
        this.retryDecisionCommandService = retryDecisionCommandService;
    }

    public RetryDecisionResponse retry(String actor, UUID applicationId, String idempotencyKey) {
        String requestHash = RequestHash.of(applicationId.toString());
        return idempotencyService.execute(
                SCOPE + ":" + applicationId,
                idempotencyKey,
                requestHash,
                RetryDecisionResponse.class,
                () -> retryDecisionCommandService.retry(actor, applicationId));
    }
}
