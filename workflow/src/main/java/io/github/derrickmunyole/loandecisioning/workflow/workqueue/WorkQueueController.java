package io.github.derrickmunyole.loandecisioning.workflow.workqueue;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only for now. Role gating (UNDERWRITER/OPERATIONS_ANALYST) lives in SecurityConfig;
 * per-role scoping ("ops_analyst sees only verification/funding exceptions") and any
 * resolve/retry action arrive in Epic 4.2.
 */
@RestController
public class WorkQueueController {

    private final WorkflowTaskRepository workflowTaskRepository;

    public WorkQueueController(WorkflowTaskRepository workflowTaskRepository) {
        this.workflowTaskRepository = workflowTaskRepository;
    }

    @GetMapping("/work-queue")
    public List<WorkflowTaskResponse> list() {
        return workflowTaskRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(WorkflowTaskResponse::from)
                .toList();
    }
}
