package io.github.derrickmunyole.loandecisioning.verification.api;

import io.github.derrickmunyole.loandecisioning.verification.VerificationCase;
import io.github.derrickmunyole.loandecisioning.verification.VerificationCaseRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Read-only access to {@code VerificationCase} rows for modules outside {@code verification} —
 * the same "extract a port on the second real caller" pattern ADR 0007 documents, applied here
 * for {@code decisioning} (Epic 3.1), which can't reach {@code verification}'s internal entities
 * directly under the ArchUnit module-boundary rule.
 */
@Service
public class VerificationEvidenceQueryService {

    private final VerificationCaseRepository verificationCaseRepository;

    public VerificationEvidenceQueryService(VerificationCaseRepository verificationCaseRepository) {
        this.verificationCaseRepository = verificationCaseRepository;
    }

    public List<VerificationCaseView> findByApplicationId(UUID applicationId) {
        return verificationCaseRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId).stream()
                .map(VerificationEvidenceQueryService::toView)
                .toList();
    }

    private static VerificationCaseView toView(VerificationCase verificationCase) {
        return new VerificationCaseView(
                verificationCase.getId(),
                verificationCase.getApplicationId(),
                verificationCase.getApplicationVersionId(),
                verificationCase.getProvider(),
                verificationCase.getType().name(),
                verificationCase.getStatus().name(),
                verificationCase.getDetail(),
                verificationCase.getCreatedAt());
    }
}
