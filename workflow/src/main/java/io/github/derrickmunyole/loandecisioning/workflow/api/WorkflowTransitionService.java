package io.github.derrickmunyole.loandecisioning.workflow.api;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Validates {@link ApplicationStatus} transitions against the state model in {@code
 * docs/blueprint.md} section 4. Pure validation only — no persistence, audit, or outbox side
 * effects. The caller mutates its own aggregate and emits audit/outbox itself, inside the same
 * transaction as the call here, so the two stay atomic without this module reaching into
 * applicant-origination's entities (see ADR 0007 for why the "atomically" property in the roadmap
 * is satisfied this way rather than by this service owning those writes).
 */
@Service
public class WorkflowTransitionService {

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> LEGAL_TRANSITIONS =
            buildTransitionTable();

    public void validateTransition(ApplicationStatus from, ApplicationStatus to) {
        if (!LEGAL_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalApplicationTransitionException(from, to);
        }
    }

    private static Map<ApplicationStatus, Set<ApplicationStatus>> buildTransitionTable() {
        Map<ApplicationStatus, Set<ApplicationStatus>> table = new EnumMap<>(ApplicationStatus.class);
        table.put(ApplicationStatus.DRAFT, EnumSet.of(ApplicationStatus.SUBMITTED));
        table.put(ApplicationStatus.SUBMITTED, EnumSet.of(ApplicationStatus.VERIFYING));
        table.put(ApplicationStatus.VERIFYING, EnumSet.of(ApplicationStatus.UNDERWRITING));
        table.put(
                ApplicationStatus.UNDERWRITING,
                EnumSet.of(
                        ApplicationStatus.APPROVED,
                        ApplicationStatus.DECLINED,
                        ApplicationStatus.REFERRED,
                        ApplicationStatus.CONDITIONAL_APPROVAL));
        table.put(
                ApplicationStatus.REFERRED,
                EnumSet.of(
                        ApplicationStatus.UNDERWRITING, ApplicationStatus.APPROVED, ApplicationStatus.DECLINED));
        table.put(ApplicationStatus.APPROVED, EnumSet.of(ApplicationStatus.OFFERED));
        table.put(ApplicationStatus.CONDITIONAL_APPROVAL, EnumSet.of(ApplicationStatus.OFFERED));
        table.put(
                ApplicationStatus.OFFERED,
                EnumSet.of(
                        ApplicationStatus.ACCEPTED, ApplicationStatus.OFFER_EXPIRED, ApplicationStatus.WITHDRAWN));
        table.put(ApplicationStatus.ACCEPTED, EnumSet.of(ApplicationStatus.FUNDING_PENDING));
        table.put(
                ApplicationStatus.FUNDING_PENDING,
                EnumSet.of(ApplicationStatus.FUNDED, ApplicationStatus.FUNDING_FAILED));
        table.put(ApplicationStatus.FUNDED, EnumSet.of(ApplicationStatus.ACTIVE));
        // ACTIVE, DECLINED, OFFER_EXPIRED, WITHDRAWN are terminal within this scope.
        // FUNDING_FAILED's retry/resolution target is deferred to Milestone 4.2 (ops exception
        // handling) rather than guessed at here.
        return Map.copyOf(table);
    }
}
