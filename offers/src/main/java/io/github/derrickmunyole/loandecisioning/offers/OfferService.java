package io.github.derrickmunyole.loandecisioning.offers;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.IdempotencyService;
import io.github.derrickmunyole.loandecisioning.infrastructure.api.RequestHash;
import io.github.derrickmunyole.loandecisioning.origination.api.ApplicationOwnershipService;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class OfferService {

    private static final String ACCEPT_SCOPE = "offer.accept";

    private final OfferRepository offerRepository;
    private final ApplicationOwnershipService applicationOwnershipService;
    private final IdempotencyService idempotencyService;
    private final OfferAcceptCommandService offerAcceptCommandService;

    public OfferService(
            OfferRepository offerRepository,
            ApplicationOwnershipService applicationOwnershipService,
            IdempotencyService idempotencyService,
            OfferAcceptCommandService offerAcceptCommandService) {
        this.offerRepository = offerRepository;
        this.applicationOwnershipService = applicationOwnershipService;
        this.idempotencyService = idempotencyService;
        this.offerAcceptCommandService = offerAcceptCommandService;
    }

    public OfferResponse accept(String username, UUID offerId, String idempotencyKey) {
        Offer offer = offerRepository.findById(offerId).orElseThrow(() -> new OfferNotFoundException(offerId));
        if (!applicationOwnershipService.isOwner(offer.getApplicationId(), username)) {
            throw new AccessDeniedException("Not the owning applicant");
        }

        String requestHash = RequestHash.of(offerId.toString());
        return idempotencyService.execute(
                ACCEPT_SCOPE + ":" + offerId,
                idempotencyKey,
                requestHash,
                OfferResponse.class,
                () -> offerAcceptCommandService.accept(offerId));
    }
}
