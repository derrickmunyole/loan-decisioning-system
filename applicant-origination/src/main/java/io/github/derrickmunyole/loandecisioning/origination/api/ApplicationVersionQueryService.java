package io.github.derrickmunyole.loandecisioning.origination.api;

import io.github.derrickmunyole.loandecisioning.origination.application.ApplicationVersion;
import io.github.derrickmunyole.loandecisioning.origination.application.ApplicationVersionRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Read-only access to immutable {@code ApplicationVersion} rows for modules outside {@code
 * origination} — e.g. {@code verification}, which needs the declared submission data to run its
 * checks but can't reach {@code origination}'s internal entities/repositories directly under the
 * ArchUnit module-boundary rule.
 */
@Service
public class ApplicationVersionQueryService {

    private final ApplicationVersionRepository applicationVersionRepository;

    public ApplicationVersionQueryService(ApplicationVersionRepository applicationVersionRepository) {
        this.applicationVersionRepository = applicationVersionRepository;
    }

    public Optional<ApplicationVersionView> findByApplicationIdAndVersionNumber(
            UUID applicationId, int versionNumber) {
        return applicationVersionRepository
                .findByApplicationIdAndVersionNumber(applicationId, versionNumber)
                .map(ApplicationVersionQueryService::toView);
    }

    private static ApplicationVersionView toView(ApplicationVersion version) {
        return new ApplicationVersionView(
                version.getId(),
                version.getApplicationId(),
                version.getVersionNumber(),
                version.getRequestedAmountKes(),
                version.getRequestedTermMonths(),
                version.getDeclaredMonthlyIncomeKes(),
                version.getDeclaredEmploymentStatus().name(),
                version.getDeclaredEmployerName(),
                version.getLoanPurpose());
    }
}
