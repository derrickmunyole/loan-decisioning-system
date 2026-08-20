package io.github.derrickmunyole.loandecisioning.offers;

import java.util.UUID;

/** Thrown by {@link Offer#accept} when the offer's expiry has passed but no sweep had marked it yet. */
class OfferExpiredException extends RuntimeException {

    OfferExpiredException(UUID offerId) {
        super("Offer " + offerId + " has expired");
    }
}
