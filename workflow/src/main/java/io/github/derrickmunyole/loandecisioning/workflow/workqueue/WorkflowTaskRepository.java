package io.github.derrickmunyole.loandecisioning.workflow.workqueue;

import io.github.derrickmunyole.loandecisioning.workflow.api.WorkflowTaskType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowTaskRepository extends JpaRepository<WorkflowTask, UUID> {

    List<WorkflowTask> findAllByOrderByCreatedAtDesc();

    Optional<WorkflowTask> findFirstByApplicationIdAndTaskTypeAndStatusOrderByCreatedAtDesc(
            UUID applicationId, WorkflowTaskType taskType, WorkflowTaskStatus status);
}
