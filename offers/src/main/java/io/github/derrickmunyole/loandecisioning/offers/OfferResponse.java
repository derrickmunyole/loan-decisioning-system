package io.github.derrickmunyole.loandecisioning.offers;

import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Shared across accept and {@code GET /applications/{id}/offer} responses. */
public record OfferResponse(
        UUID id,
        UUID applicationId,
        BigDecimal principalKes,
        int aprBasisPoints,
        int termMonths,
        BigDecimal monthlyPaymentKes,
        ApplicationStatus status,
        Instant expiresAt,
        Instant createdAt) {

    static OfferResponse from(Offer offer) {
        return new OfferResponse(
                offer.getId(),
                offer.getApplicationId(),
                offer.getPrincipalKes(),
                offer.getAprBasisPoints(),
                offer.getTermMonths(),
                offer.getMonthlyPaymentKes(),
                offer.getStatus(),
                offer.getExpiresAt(),
                offer.getCreatedAt());
    }
}
