package io.github.derrickmunyole.loandecisioning.verification;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface VerificationAttemptMarkerRepository extends JpaRepository<VerificationAttemptMarker, UUID> {

    Optional<VerificationAttemptMarker> findByApplicationId(UUID applicationId);
}
