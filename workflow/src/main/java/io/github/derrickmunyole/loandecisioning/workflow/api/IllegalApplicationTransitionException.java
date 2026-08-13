package io.github.derrickmunyole.loandecisioning.workflow.api;

public class IllegalApplicationTransitionException extends RuntimeException {

    public IllegalApplicationTransitionException(ApplicationStatus from, ApplicationStatus to) {
        super("Cannot transition Application from " + from + " to " + to);
    }
}
