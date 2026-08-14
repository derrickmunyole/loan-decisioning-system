package io.github.derrickmunyole.loandecisioning.decisioning;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnderwritingSnapshotRepository extends JpaRepository<UnderwritingSnapshot, UUID> {

    boolean existsByApplicationVersionId(UUID applicationVersionId);

    List<UnderwritingSnapshot> findByApplicationId(UUID applicationId);
}
