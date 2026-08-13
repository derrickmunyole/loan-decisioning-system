package io.github.derrickmunyole.loandecisioning.verification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationCaseRepository extends JpaRepository<VerificationCase, UUID> {

    List<VerificationCase> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
}
