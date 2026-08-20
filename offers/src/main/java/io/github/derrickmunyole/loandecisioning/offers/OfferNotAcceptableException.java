package io.github.derrickmunyole.loandecisioning.offers;

import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import java.util.UUID;

/** Thrown when a caller tries to accept an offer that isn't currently {@code OFFERED}. */
class OfferNotAcceptableException extends RuntimeException {

    OfferNotAcceptableException(UUID offerId, ApplicationStatus currentStatus) {
        super("Offer " + offerId + " is not acceptable; current status is " + currentStatus);
    }
}
