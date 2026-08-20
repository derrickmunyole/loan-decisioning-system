package io.github.derrickmunyole.loandecisioning.decisioning.api;

import io.github.derrickmunyole.loandecisioning.decisioning.PricingVersion;
import io.github.derrickmunyole.loandecisioning.decisioning.PricingVersionRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Read-only access to {@code PricingVersion} rows for modules outside {@code decisioning}. Looks
 * up by id rather than "currently published" on purpose: {@code offers} needs the exact pricing
 * version a {@code Decision} was computed against (captured on {@code Decision.pricingVersionId}
 * since Epic 3.4), not whatever happens to be published when the offer is built — the same
 * "everything decision-related carries its exact version IDs" principle {@code Decision} itself
 * follows.
 */
@Service
public class PricingVersionQueryService {

    private final PricingVersionRepository pricingVersionRepository;

    public PricingVersionQueryService(PricingVersionRepository pricingVersionRepository) {
        this.pricingVersionRepository = pricingVersionRepository;
    }

    public Optional<PricingVersionView> findById(UUID pricingVersionId) {
        return pricingVersionRepository.findById(pricingVersionId).map(PricingVersionQueryService::toView);
    }

    private static PricingVersionView toView(PricingVersion pricingVersion) {
        return new PricingVersionView(pricingVersion.getId(), pricingVersion.getAprTermRulesJson());
    }
}
