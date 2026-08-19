package io.github.derrickmunyole.loandecisioning.decisioning;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingVersionRepository extends JpaRepository<PricingVersion, UUID> {

    Optional<PricingVersion> findFirstByStatusOrderByPublishedAtDesc(VersionStatus status);
}
