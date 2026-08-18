package io.github.derrickmunyole.loandecisioning.workflow.api;

import java.util.UUID;

public class WorkflowTaskNotFoundException extends RuntimeException {

    public WorkflowTaskNotFoundException(UUID taskId) {
        super("WorkflowTask " + taskId + " not found");
    }
}
