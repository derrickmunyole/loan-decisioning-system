package io.github.derrickmunyole.loandecisioning.origination.application;

public class InvalidApplicationDataException extends RuntimeException {

    public InvalidApplicationDataException(String message) {
        super(message);
    }
}
