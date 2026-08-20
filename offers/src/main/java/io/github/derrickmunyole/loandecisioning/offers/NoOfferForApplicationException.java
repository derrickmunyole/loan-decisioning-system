package io.github.derrickmunyole.loandecisioning.offers;

import java.util.UUID;

class NoOfferForApplicationException extends RuntimeException {

    NoOfferForApplicationException(UUID applicationId) {
        super("Application " + applicationId + " has no offer");
    }
}