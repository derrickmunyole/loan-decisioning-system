package io.github.derrickmunyole.loandecisioning.decisioning;

import java.util.UUID;

/**
 * {@code POST /cases/{id}/retry-decision} only retries the credit-score-provider-outage path
 * (see {@link DecisionEngineHandler}) — thrown when there's no open {@code
 * CREDIT_SCORE_PROVIDER_UNAVAILABLE} task for the application, e.g. it was never referred for
 * that reason, or an earlier retry already resolved it.
 */
class NoRetryableProviderOutageTaskException extends RuntimeException {

    NoRetryableProviderOutageTaskException(UUID applicationId) {
        super(
                "Application "
                        + applicationId
                        + " has no open CREDIT_SCORE_PROVIDER_UNAVAILABLE task to retry");
    }
}
