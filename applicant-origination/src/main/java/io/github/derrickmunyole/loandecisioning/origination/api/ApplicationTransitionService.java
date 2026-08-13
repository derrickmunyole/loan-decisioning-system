package io.github.derrickmunyole.loandecisioning.origination.api;

import io.github.derrickmunyole.loandecisioning.origination.application.Application;
import io.github.derrickmunyole.loandecisioning.origination.application.ApplicationNotFoundException;
import io.github.derrickmunyole.loandecisioning.origination.application.ApplicationRepository;
import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import io.github.derrickmunyole.loandecisioning.workflow.api.WorkflowTransitionService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write port for modules outside {@code origination} that need to drive an {@code Application}
 * through the state machine — e.g. {@code verification}'s async consumer — without reaching into
 * the internal {@code Application} entity/{@code ApplicationRepository} directly. Joins the
 * caller's existing transaction under default ({@code REQUIRED}) propagation, so a caller that
 * calls this twice in one method (e.g. VERIFYING then, later, UNDERWRITING) and then throws still
 * rolls back both hops atomically.
 */
@Service
public class ApplicationTransitionService {

    private final ApplicationRepository applicationRepository;
    private final WorkflowTransitionService workflowTransitionService;

    public ApplicationTransitionService(
            ApplicationRepository applicationRepository, WorkflowTransitionService workflowTransitionService) {
        this.applicationRepository = applicationRepository;
        this.workflowTransitionService = workflowTransitionService;
    }

    @Transactional
    public void transitionTo(UUID applicationId, ApplicationStatus newStatus) {
        Application application =
                applicationRepository
                        .findById(applicationId)
                        .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        workflowTransitionService.validateTransition(application.getStatus(), newStatus);
        application.transitionTo(newStatus);
    }
}
