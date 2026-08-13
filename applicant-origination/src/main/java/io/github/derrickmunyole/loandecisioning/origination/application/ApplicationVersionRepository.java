package io.github.derrickmunyole.loandecisioning.origination.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationVersionRepository extends JpaRepository<ApplicationVersion, UUID> {

    List<ApplicationVersion> findByApplicationIdOrderByVersionNumberAsc(UUID applicationId);

    Optional<ApplicationVersion> findByApplicationIdAndVersionNumber(UUID applicationId, int versionNumber);
}
