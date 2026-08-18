package io.github.derrickmunyole.loandecisioning.workflow.workqueue;

import io.github.derrickmunyole.loandecisioning.workflow.api.WorkflowTaskType;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role gating (UNDERWRITER/OPERATIONS_ANALYST) lives in SecurityConfig; the per-role scoping this
 * class applies on top narrows what each role actually sees: OPERATIONS_ANALYST gets everything
 * except UNDERWRITE_CASE (that's the underwriter's queue), UNDERWRITER gets UNDERWRITE_CASE only.
 */
@RestController
class WorkQueueController {

    private static final String OPERATIONS_ANALYST_AUTHORITY = "ROLE_OPERATIONS_ANALYST";

    private final WorkflowTaskRepository workflowTaskRepository;
    private final WorkQueueCommandService workQueueCommandService;

    WorkQueueController(
            WorkflowTaskRepository workflowTaskRepository, WorkQueueCommandService workQueueCommandService) {
        this.workflowTaskRepository = workflowTaskRepository;
        this.workQueueCommandService = workQueueCommandService;
    }

    @GetMapping("/work-queue")
    List<WorkflowTaskResponse> list(Authentication authentication) {
        boolean isOperationsAnalyst = isOperationsAnalyst(authentication);
        return workflowTaskRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(task -> isOperationsAnalyst == (task.getTaskType() != WorkflowTaskType.UNDERWRITE_CASE))
                .map(WorkflowTaskResponse::from)
                .toList();
    }

    @PostMapping("/work-queue/{id}/resolve")
    WorkflowTaskResponse resolve(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody ResolveWorkQueueTaskRequest request) {
        return workQueueCommandService.resolve(authentication.getName(), id, request);
    }

    private boolean isOperationsAnalyst(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(OPERATIONS_ANALYST_AUTHORITY));
    }
}
