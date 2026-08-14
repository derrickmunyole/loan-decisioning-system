package io.github.derrickmunyole.loandecisioning.decisioning;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PolicyVersionRepository extends JpaRepository<PolicyVersion, UUID> {

    Optional<PolicyVersion> findFirstByStatusOrderByPublishedAtDesc(VersionStatus status);
}
