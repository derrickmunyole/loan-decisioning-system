package io.github.derrickmunyole.loandecisioning.origination.api;

import java.util.UUID;

public class ApplicationNotFoundException extends RuntimeException {

    public ApplicationNotFoundException(UUID applicationId) {
        super("Application " + applicationId + " not found");
    }
}
