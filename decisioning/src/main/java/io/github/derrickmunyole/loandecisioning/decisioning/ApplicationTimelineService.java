package io.github.derrickmunyole.loandecisioning.decisioning;

import io.github.derrickmunyole.loandecisioning.decisioning.api.DecisionQueryService;
import io.github.derrickmunyole.loandecisioning.infrastructure.api.AuditQueryService;
import io.github.derrickmunyole.loandecisioning.origination.api.ApplicationOwnershipService;
import io.github.derrickmunyole.loandecisioning.origination.api.ApplicationTransitionService;
import io.github.derrickmunyole.loandecisioning.verification.api.VerificationEvidenceQueryService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lives in {@code decisioning}, not {@code applicant-origination} where the endpoint used to live
 * — the aggregate needs {@code Decision} (decisioning itself) and evidence
 * ({@code verification.api}), and both of those modules already depend on {@code origination}, so
 * {@code origination} depending back on either would be a cycle. {@code decisioning} is the only
 * module that can legally see all three at once (same reasoning as retry-decision's placement in
 * Epic 4.2, ADR 0010; see ADR 0012 for this endpoint's own writeup, including the role-dispatched
 * response shape).
 */
@Service
class ApplicationTimelineService {

    private static final String AUDIT_TARGET_TYPE = "Application";
    private static final Set<String> STAFF_AUTHORITIES =
            Set.of(
                    "ROLE_UNDERWRITER",
                    "ROLE_OPERATIONS_ANALYST",
                    "ROLE_POLICY_ADMIN",
                    "ROLE_AUDITOR");

    private final ApplicationTransitionService applicationTransitionService;
    private final ApplicationOwnershipService applicationOwnershipService;
    private final DecisionQueryService decisionQueryService;
    private final VerificationEvidenceQueryService verificationEvidenceQueryService;
    private final AuditQueryService auditQueryService;

    ApplicationTimelineService(
            ApplicationTransitionService applicationTransitionService,
            ApplicationOwnershipService applicationOwnershipService,
            DecisionQueryService decisionQueryService,
            VerificationEvidenceQueryService verificationEvidenceQueryService,
            AuditQueryService auditQueryService) {
        this.applicationTransitionService = applicationTransitionService;
        this.applicationOwnershipService = applicationOwnershipService;
        this.decisionQueryService = decisionQueryService;
        this.verificationEvidenceQueryService = verificationEvidenceQueryService;
        this.auditQueryService = auditQueryService;
    }

    @Transactional(readOnly = true)
    Object getTimeline(Authentication authentication, UUID applicationId) {
        // Existence check -- throws ApplicationNotFoundException (mapped to 404) if missing.
        applicationTransitionService.currentStatus(applicationId);

        if (isStaff(authentication)) {
            return new ApplicationTimelineResponse(
                    applicationId,
                    decisionQueryService.findByApplicationId(applicationId).stream()
                            .map(DecisionSummary::from)
                            .toList(),
                    verificationEvidenceQueryService.findByApplicationId(applicationId).stream()
                            .map(EvidenceSummary::from)
                            .toList(),
                    events(applicationId));
        }

        if (!applicationOwnershipService.isOwner(applicationId, authentication.getName())) {
            throw new AccessDeniedException("Not the owning applicant");
        }
        return events(applicationId);
    }

    private List<TimelineEvent> events(UUID applicationId) {
        return auditQueryService.findByTarget(AUDIT_TARGET_TYPE, applicationId.toString()).stream()
                .map(TimelineEvent::from)
                .toList();
    }

    private boolean isStaff(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .anyMatch(STAFF_AUTHORITIES::contains);
    }
}
