package io.github.derrickmunyole.loandecisioning.offers;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.Audited;
import io.github.derrickmunyole.loandecisioning.origination.api.ApplicationTransitionService;
import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds the mutation method that must always be invoked through the Spring-managed proxy so
 * {@code @Audited}'s AOP advice actually fires — split out from {@link OfferService} for the same
 * reason {@code ApplicationCommandService}/{@code CaseDecisionCommandService} are split from their
 * idempotency-wrapping siblings.
 */
@Service
class OfferAcceptCommandService {

    private final OfferRepository offerRepository;
    private final ApplicationTransitionService applicationTransitionService;
    private final Clock clock;

    OfferAcceptCommandService(
            OfferRepository offerRepository, ApplicationTransitionService applicationTransitionService, Clock clock) {
        this.offerRepository = offerRepository;
        this.applicationTransitionService = applicationTransitionService;
        this.clock = clock;
    }

    @Transactional
    @Audited(action = "OFFER_ACCEPTED", targetType = "Offer", targetId = "#offerId")
    OfferResponse accept(UUID offerId) {
        Offer offer = offerRepository.findById(offerId).orElseThrow(() -> new OfferNotFoundException(offerId));
        offer.accept(clock.instant());
        applicationTransitionService.transitionTo(offer.getApplicationId(), ApplicationStatus.ACCEPTED);
        return OfferResponse.from(offer);
    }
}