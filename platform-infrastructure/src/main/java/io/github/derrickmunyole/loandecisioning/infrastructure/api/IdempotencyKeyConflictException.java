package io.github.derrickmunyole.loandecisioning.infrastructure.api;

/** The same Idempotency-Key was reused with a different request body. */
public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException(String scope, String idempotencyKey) {
        super("Idempotency-Key '" + idempotencyKey + "' for " + scope + " was reused with a different request");
    }
}
