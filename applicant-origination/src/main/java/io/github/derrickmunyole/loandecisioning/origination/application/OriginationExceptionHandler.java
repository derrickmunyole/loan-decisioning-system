package io.github.derrickmunyole.loandecisioning.origination.application;

import io.github.derrickmunyole.loandecisioning.infrastructure.idempotency.IdempotencyKeyConflictException;
import io.github.derrickmunyole.loandecisioning.infrastructure.idempotency.IdempotencyKeyInProgressException;
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
}
