package io.github.derrickmunyole.loandecisioning.decisioning;

import io.github.derrickmunyole.loandecisioning.origination.api.ApplicationNotFoundException;
import io.github.derrickmunyole.loandecisioning.workflow.api.IllegalApplicationTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "io.github.derrickmunyole.loandecisioning.decisioning")
class DecisioningExceptionHandler {

    @ExceptionHandler({
        VersionNotFoundException.class,
        ApplicationNotFoundException.class,
        NoRetryableProviderOutageTaskException.class
    })
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String handleNotFound(RuntimeException e) {
        return e.getMessage();
    }

    @ExceptionHandler({
        VersionAlreadyPublishedException.class,
        IllegalApplicationTransitionException.class,
        NoAutomatedDecisionToOverrideException.class
    })
    @ResponseStatus(HttpStatus.CONFLICT)
    String handleConflict(RuntimeException e) {
        return e.getMessage();
    }
}
