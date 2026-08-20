package io.github.derrickmunyole.loandecisioning.offers;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.Audited;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * A dedicated bean, called from {@link OfferExpiryJob} as a genuine inter-bean call, so {@code
 * @Audited}'s AOP advice actually fires — unlike {@code VerificationTransitionAuditor}/{@code
 * DecisionTransitionAuditor} (Epic 4.3), this isn't a retrofit: automated Application transitions
 * going unaudited was a real gap found only after the fact there, so the expiry sweep is designed
 * with its own auditing collaborator from the start rather than repeating that gap.
 *
 * <p>{@code targetType} is {@code "Application"}, matching {@link OfferCreatedAuditor} and {@code
 * OFFER_ACCEPTED} — not {@code "Offer"} — so the Epic 4.3 timeline endpoint's {@code targetType =
 * "Application"} audit query surfaces every step of an offer's lifecycle, not just its creation.
 */
@Service
class OfferExpiryAuditor {

    @Audited(action = "OFFER_EXPIRED", targetType = "Application", targetId = "#applicationId")
    void recordExpiry(UUID applicationId) {}
}
