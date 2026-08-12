package io.github.derrickmunyole.loandecisioning.origination.application;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.IdempotencyKeyConflictException;
import io.github.derrickmunyole.loandecisioning.infrastructure.api.IdempotencyKeyInProgressException;
import io.github.derrickmunyole.loandecisioning.origination.document.DocumentStorageException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "io.github.derrickmunyole.loandecisioning.origination")
class OriginationExceptionHandler {

    @ExceptionHandler(ApplicationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String handleNotFound(ApplicationNotFoundException e) {
        return e.getMessage();
    }

    @ExceptionHandler({ApplicationNotEditableException.class, IdempotencyKeyConflictException.class, IdempotencyKeyInProgressException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    String handleConflict(RuntimeException e) {
        return e.getMessage();
    }

    @ExceptionHandler(InvalidApplicationDataException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String handleInvalidData(InvalidApplicationDataException e) {
        return e.getMessage();
    }

    @ExceptionHandler(DocumentStorageException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    String handleStorageFailure(DocumentStorageException e) {
        return e.getMessage();
    }

    /**
     * Two racing requests for the same not-yet-existing applicant, or two racing submits for the
     * same application, can both pass their in-memory checks before either commits and then
     * collide on a DB unique constraint or {@code Application}'s {@code @Version} lock. The
     * message is deliberately generic rather than echoing the exception — retrying (the client
     * already has to for a 409) resolves cleanly since the loser's transaction rolled back.
     */
    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    String handleConcurrentWriteConflict() {
        return "Concurrent modification detected — please retry the request";
    }
}
