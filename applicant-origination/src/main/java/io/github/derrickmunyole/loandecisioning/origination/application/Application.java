package io.github.derrickmunyole.loandecisioning.origination.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Mutable aggregate holding draft fields directly (no separate "draft version" concept); {@link
 * ApplicationVersion} rows are created only at submit time and are immutable from then on.
 * Carries {@code @Version} because at-least-once delivery elsewhere in the system can cause
 * concurrent transition attempts (see roadmap's architecture spec).
 *
 * <p>{@code id} is caller-assigned rather than {@code @UuidGenerator}-generated: the creating
 * service needs the id before the entity is persisted so {@code @Audited}'s SpEL (which only
 * binds method parameters, not return values) can reference it as the audit target.
 */
@Entity
@Getter
@Table(name = "application")
public class Application {

    @Id
    private UUID id;

    @Column(name = "applicant_id", nullable = false)
    private UUID applicantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(name = "current_version_number")
    private Integer currentVersionNumber;

    @Column(name = "requested_amount_kes")
    private BigDecimal requestedAmountKes;

    @Column(name = "requested_term_months")
    private Integer requestedTermMonths;

    @Column(name = "declared_monthly_income_kes")
    private BigDecimal declaredMonthlyIncomeKes;

    @Enumerated(EnumType.STRING)
    @Column(name = "declared_employment_status")
    private EmploymentStatus declaredEmploymentStatus;

    @Column(name = "declared_employer_name")
    private String declaredEmployerName;

    @Column(name = "loan_purpose")
    private String loanPurpose;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Application() {}

    public Application(UUID id, UUID applicantId) {
        this.id = id;
        this.applicantId = applicantId;
        this.status = ApplicationStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void updateDraft(
            BigDecimal requestedAmountKes,
            Integer requestedTermMonths,
            BigDecimal declaredMonthlyIncomeKes,
            EmploymentStatus declaredEmploymentStatus,
            String declaredEmployerName,
            String loanPurpose) {
        requireDraft();
        this.requestedAmountKes = requestedAmountKes;
        this.requestedTermMonths = requestedTermMonths;
        this.declaredMonthlyIncomeKes = declaredMonthlyIncomeKes;
        this.declaredEmploymentStatus = declaredEmploymentStatus;
        this.declaredEmployerName = declaredEmployerName;
        this.loanPurpose = loanPurpose;
        this.updatedAt = Instant.now();
    }

    public void markSubmitted(int versionNumber) {
        requireDraft();
        this.status = ApplicationStatus.SUBMITTED;
        this.currentVersionNumber = versionNumber;
        this.updatedAt = Instant.now();
    }

    public boolean isDraft() {
        return this.status == ApplicationStatus.DRAFT;
    }

    private void requireDraft() {
        if (this.status != ApplicationStatus.DRAFT) {
            throw new ApplicationNotEditableException(id);
        }
    }
}
