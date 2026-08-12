package io.github.derrickmunyole.loandecisioning.origination.application;

import java.util.UUID;

public class ApplicationNotEditableException extends RuntimeException {

    public ApplicationNotEditableException(UUID applicationId) {
        super("Application " + applicationId + " is not in DRAFT status");
    }
}
