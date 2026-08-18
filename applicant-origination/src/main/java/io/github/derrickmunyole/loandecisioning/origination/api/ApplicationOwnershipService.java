package io.github.derrickmunyole.loandecisioning.origination.api;

import io.github.derrickmunyole.loandecisioning.origination.applicant.ApplicantRepository;
import io.github.derrickmunyole.loandecisioning.origination.application.Application;
import io.github.derrickmunyole.loandecisioning.origination.application.ApplicationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read port for modules outside {@code origination} that need the same ownership check {@code
 * ApplicationAccessGuard} already backs for {@code @PreAuthorize} inside {@code origination}
 * itself. Extracted in Epic 4.3 for {@code decisioning}'s new {@code
 * GET /applications/{id}/timeline} controller, which dispatches an applicant caller to a
 * different, narrower response than a staff caller — a decision the coarse endpoint-level role
 * gate in {@code SecurityConfig} can't make on its own, so it has to be checked imperatively
 * rather than declared via {@code @PreAuthorize} on a controller living in a different module.
 */
@Service
public class ApplicationOwnershipService {

    private final ApplicationRepository applicationRepository;
    private final ApplicantRepository applicantRepository;

    public ApplicationOwnershipService(
            ApplicationRepository applicationRepository, ApplicantRepository applicantRepository) {
        this.applicationRepository = applicationRepository;
        this.applicantRepository = applicantRepository;
    }

    @Transactional(readOnly = true)
    public boolean isOwner(UUID applicationId, String username) {
        return applicationRepository
                .findById(applicationId)
                .map(Application::getApplicantId)
                .flatMap(applicantRepository::findById)
                .map(applicant -> applicant.getUsername().equals(username))
                .orElse(false);
    }
}
