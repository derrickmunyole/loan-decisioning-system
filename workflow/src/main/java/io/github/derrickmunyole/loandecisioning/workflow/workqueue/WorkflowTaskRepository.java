package io.github.derrickmunyole.loandecisioning.workflow.workqueue;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowTaskRepository extends JpaRepository<WorkflowTask, UUID> {

    List<WorkflowTask> findAllByOrderByCreatedAtDesc();
}
