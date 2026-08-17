package io.github.derrickmunyole.loandecisioning.decisioning;

import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * The automated (or, from Epic 4.1 onward, manual override) outcome for one application, carrying
 * the exact snapshot/policy/scorecard/pricing/credit-score-model versions it was computed from
 * (blueprint §5's decision-integrity requirement). Append-only, no update path — an override
 * doesn't edit this row, it creates a new one (Epic 4.1).
 */
@Entity
@Getter
@Table(name = "decision")
public class Decision {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "underwriting_snapshot_id", nullable = false)
    private UUID underwritingSnapshotId;

    @Column(name = "policy_version_id", nullable = false)
    private UUID policyVersionId;

    @Column(name = "scorecard_version_id", nullable = false)
    private UUID scorecardVersionId;

    @Column(name = "pricing_version_id", nullable = false)
    private UUID pricingVersionId;

    @Column(name = "credit_score_model_version")
    private String creditScoreModelVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus outcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reason_codes_json", nullable = false, columnDefinition = "jsonb")
    private String reasonCodesJson;

    @Column(nullable = false)
    private String actor;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    @Column(name = "overrides_decision_id")
    private UUID overridesDecisionId;

    protected Decision() {}

    public Decision(
            UUID applicationId,
            UUID underwritingSnapshotId,
            UUID policyVersionId,
            UUID scorecardVersionId,
            UUID pricingVersionId,
            String creditScoreModelVersion,
            ApplicationStatus outcome,
            String reasonCodesJson,
            String actor,
            UUID overridesDecisionId) {
        this.applicationId = applicationId;
        this.underwritingSnapshotId = underwritingSnapshotId;
        this.policyVersionId = policyVersionId;
        this.scorecardVersionId = scorecardVersionId;
        this.pricingVersionId = pricingVersionId;
        this.creditScoreModelVersion = creditScoreModelVersion;
        this.outcome = outcome;
        this.reasonCodesJson = reasonCodesJson;
        this.actor = actor;
        this.decidedAt = Instant.now();
        this.overridesDecisionId = overridesDecisionId;
    }
}
