package io.github.derrickmunyole.loandecisioning.decisioning;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {

    List<Decision> findByApplicationId(UUID applicationId);
}
