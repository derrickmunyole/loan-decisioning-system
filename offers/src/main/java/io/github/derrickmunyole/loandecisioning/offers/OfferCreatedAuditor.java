package io.github.derrickmunyole.loandecisioning.offers;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.Audited;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Called from {@link DecisionCreatedHandler} as a genuine inter-bean call, so {@code @Audited}'s
 * AOP advice actually fires — same shape as {@link OfferExpiryAuditor}, designed in from the
 * start rather than retrofitted the way Epic 4.3 had to for verification/decisioning's automated
 * transitions.
 */
@Service
class OfferCreatedAuditor {

    @Audited(action = "APPLICATION_OFFERED", targetType = "Application", targetId = "#applicationId")
    void recordOffered(UUID applicationId) {}
}