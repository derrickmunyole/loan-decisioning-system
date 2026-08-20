package io.github.derrickmunyole.loandecisioning.offers;

import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    boolean existsByDecisionId(UUID decisionId);

    List<Offer> findByStatusAndExpiresAtBefore(ApplicationStatus status, Instant cutoff);

    Optional<Offer> findFirstByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
