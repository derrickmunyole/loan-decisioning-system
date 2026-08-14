package io.github.derrickmunyole.loandecisioning.decisioning;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "io.github.derrickmunyole.loandecisioning.decisioning")
class DecisioningExceptionHandler {

    @ExceptionHandler(VersionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String handleNotFound(VersionNotFoundException e) {
        return e.getMessage();
    }

    @ExceptionHandler(VersionAlreadyPublishedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    String handleAlreadyPublished(VersionAlreadyPublishedException e) {
        return e.getMessage();
    }
}
