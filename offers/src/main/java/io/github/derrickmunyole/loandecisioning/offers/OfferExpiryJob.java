package io.github.derrickmunyole.loandecisioning.offers;

import io.github.derrickmunyole.loandecisioning.origination.api.ApplicationTransitionService;
import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ages out {@code OFFERED} offers past their {@code expiresAt} — the same
 * poll-a-batch-on-a-timer shape as {@code OutboxRelay}, the only prior {@code @Scheduled} job in
 * this codebase. {@link Offer#expireIfOverdue} is self-idempotent, so a row already flipped by a
 * concurrent {@code POST /offers/{id}/accept} racing this sweep is just skipped, not double-
 * transitioned.
 */
@Component
class OfferExpiryJob {

    private final OfferRepository offerRepository;
    private final ApplicationTransitionService applicationTransitionService;
    private final OfferExpiryAuditor offerExpiryAuditor;
    private final Clock clock;

    OfferExpiryJob(
            OfferRepository offerRepository,
            ApplicationTransitionService applicationTransitionService,
            OfferExpiryAuditor offerExpiryAuditor,
            Clock clock) {
        this.offerRepository = offerRepository;
        this.applicationTransitionService = applicationTransitionService;
        this.offerExpiryAuditor = offerExpiryAuditor;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.offers.expiry-sweep-interval:60000}")
    @Transactional
    void sweep() {
        Instant now = clock.instant();
        List<Offer> overdue = offerRepository.findByStatusAndExpiresAtBefore(ApplicationStatus.OFFERED, now);
        for (Offer offer : overdue) {
            if (offer.expireIfOverdue(now)) {
                applicationTransitionService.transitionTo(offer.getApplicationId(), ApplicationStatus.OFFER_EXPIRED);
                offerExpiryAuditor.recordExpiry(offer.getApplicationId());
            }
        }
    }
}
