package io.github.derrickmunyole.loandecisioning.origination.application;

import io.github.derrickmunyole.loandecisioning.origination.applicant.ApplicantRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Backs the {@code @PreAuthorize} ownership checks on the applicant-facing endpoints. */
@Component("applicationAccessGuard")
public class ApplicationAccessGuard {

    private final ApplicationRepository applicationRepository;
    private final ApplicantRepository applicantRepository;

    public ApplicationAccessGuard(
            ApplicationRepository applicationRepository, ApplicantRepository applicantRepository) {
        this.applicationRepository = applicationRepository;
        this.applicantRepository = applicantRepository;
    }

    public boolean isOwner(UUID applicationId, String username) {
        return applicationRepository
                .findById(applicationId)
                .map(Application::getApplicantId)
                .flatMap(applicantRepository::findById)
                .map(applicant -> applicant.getUsername().equals(username))
                .orElse(false);
    }
}
