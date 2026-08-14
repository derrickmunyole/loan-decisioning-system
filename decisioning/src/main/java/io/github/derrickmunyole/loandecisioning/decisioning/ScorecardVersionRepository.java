package io.github.derrickmunyole.loandecisioning.decisioning;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ScorecardVersionRepository extends JpaRepository<ScorecardVersion, UUID> {

    Optional<ScorecardVersion> findFirstByStatusOrderByPublishedAtDesc(VersionStatus status);
}
