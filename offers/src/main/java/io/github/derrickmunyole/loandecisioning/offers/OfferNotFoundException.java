package io.github.derrickmunyole.loandecisioning.offers;

import java.util.UUID;

class OfferNotFoundException extends RuntimeException {

    OfferNotFoundException(UUID id) {
        super("Offer " + id + " not found");
    }
}
