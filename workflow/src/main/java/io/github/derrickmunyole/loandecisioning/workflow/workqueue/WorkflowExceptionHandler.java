package io.github.derrickmunyole.loandecisioning.workflow.workqueue;

import io.github.derrickmunyole.loandecisioning.workflow.api.WorkflowTaskNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "io.github.derrickmunyole.loandecisioning.workflow")
class WorkflowExceptionHandler {

    @ExceptionHandler(WorkflowTaskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String handleNotFound(WorkflowTaskNotFoundException e) {
        return e.getMessage();
    }

    @ExceptionHandler(WorkQueueTaskOutOfScopeException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    String handleOutOfScope(WorkQueueTaskOutOfScopeException e) {
        return e.getMessage();
    }
}
