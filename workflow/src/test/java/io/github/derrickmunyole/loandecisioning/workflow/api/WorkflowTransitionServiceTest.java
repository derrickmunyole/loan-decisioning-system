package io.github.derrickmunyole.loandecisioning.workflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Exhaustively covers the full {@link ApplicationStatus} x {@link ApplicationStatus} cross-product
 * (16x16 = 256 pairs) against an expected table built independently from {@code
 * docs/blueprint.md} section 4 — the epic's stated done-criterion.
 */
class WorkflowTransitionServiceTest {

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> EXPECTED_LEGAL_EDGES =
            buildExpectedEdges();

    private final WorkflowTransitionService service = new WorkflowTransitionService();

    @Test
    void everyPairInTheFullCrossProductMatchesTheExpectedTable() {
        for (ApplicationStatus from : ApplicationStatus.values()) {
            for (ApplicationStatus to : ApplicationStatus.values()) {
                boolean expectedLegal = EXPECTED_LEGAL_EDGES.getOrDefault(from, Set.of()).contains(to);
                if (expectedLegal) {
                    assertThatCode(() -> service.validateTransition(from, to))
                            .as("%s -> %s should be legal", from, to)
                            .doesNotThrowAnyException();
                } else {
                    assertThatThrownBy(() -> service.validateTransition(from, to))
                            .as("%s -> %s should be illegal", from, to)
                            .isInstanceOf(IllegalApplicationTransitionException.class);
                }
            }
        }
    }

    @Test
    void everyNonTerminalStatusHasAtLeastOneLegalOutgoingEdge() {
        Set<ApplicationStatus> terminal =
                EnumSet.of(
                        ApplicationStatus.ACTIVE,
                        ApplicationStatus.DECLINED,
                        ApplicationStatus.OFFER_EXPIRED,
                        ApplicationStatus.WITHDRAWN,
                        ApplicationStatus.FUNDING_FAILED);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            if (terminal.contains(status)) {
                continue;
            }
            assertThat(EXPECTED_LEGAL_EDGES.getOrDefault(status, Set.of()))
                    .as("%s should have at least one legal outgoing edge", status)
                    .isNotEmpty();
        }
    }

    private static Map<ApplicationStatus, Set<ApplicationStatus>> buildExpectedEdges() {
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
        return Map.copyOf(table);
    }
}