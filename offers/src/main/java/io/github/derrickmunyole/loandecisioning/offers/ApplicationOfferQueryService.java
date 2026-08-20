package io.github.derrickmunyole.loandecisioning.offers;

import io.github.derrickmunyole.loandecisioning.origination.api.ApplicationOwnershipService;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs {@code GET /applications/{id}/offer} — owned by {@code offers}, not {@code
 * applicant-origination}, the same dependency-direction reason {@code
 * GET /applications/{id}/timeline} moved to {@code decisioning} in Epic 4.3: the response needs
 * {@code Offer}, and {@code origination} can never depend on {@code offers} without a cycle.
 * Blueprint §3 lists "view status/offer" as an applicant permission but the roadmap's endpoint
 * table doesn't name this route explicitly — added since there's otherwise no way for the
 * applicant to discover an offer id to accept.
 */
@Service
class ApplicationOfferQueryService {

    private final OfferRepository offerRepository;
    private final ApplicationOwnershipService applicationOwnershipService;

    ApplicationOfferQueryService(
            OfferRepository offerRepository, ApplicationOwnershipService applicationOwnershipService) {
        this.offerRepository = offerRepository;
        this.applicationOwnershipService = applicationOwnershipService;
    }

    @Transactional(readOnly = true)
    OfferResponse getCurrentOffer(String username, UUID applicationId) {
        if (!applicationOwnershipService.isOwner(applicationId, username)) {
            throw new AccessDeniedException("Not the owning applicant");
        }
        Offer offer =
                offerRepository
                        .findFirstByApplicationIdOrderByCreatedAtDesc(applicationId)
                        .orElseThrow(() -> new NoOfferForApplicationException(applicationId));
        return OfferResponse.from(offer);
    }
}
