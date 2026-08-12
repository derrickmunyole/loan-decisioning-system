package io.github.derrickmunyole.loandecisioning.infrastructure.api;

/** The original request for this key is still being processed; ask the caller to retry later. */
public class IdempotencyKeyInProgressException extends RuntimeException {

    public IdempotencyKeyInProgressException(String scope, String idempotencyKey) {
        super("Request for Idempotency-Key '" + idempotencyKey + "' on " + scope + " is still in progress");
    }
}
