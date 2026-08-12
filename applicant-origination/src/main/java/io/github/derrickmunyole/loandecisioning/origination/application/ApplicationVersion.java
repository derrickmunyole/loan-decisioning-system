package io.github.derrickmunyole.loandecisioning.origination.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

/** Immutable snapshot of a submitted application. Insert-only — no setters, ever. */
@Entity
@Getter
@Table(name = "application_version")
public class ApplicationVersion {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "requested_amount_kes", nullable = false)
    private BigDecimal requestedAmountKes;

    @Column(name = "requested_term_months", nullable = false)
    private int requestedTermMonths;

    @Column(name = "declared_monthly_income_kes", nullable = false)
    private BigDecimal declaredMonthlyIncomeKes;

    @Enumerated(EnumType.STRING)
    @Column(name = "declared_employment_status", nullable = false)
    private EmploymentStatus declaredEmploymentStatus;

    @Column(name = "declared_employer_name")
    private String declaredEmployerName;

    @Column(name = "loan_purpose")
    private String loanPurpose;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    protected ApplicationVersion() {}

    public ApplicationVersion(
            UUID applicationId,
            int versionNumber,
            BigDecimal requestedAmountKes,
            int requestedTermMonths,
            BigDecimal declaredMonthlyIncomeKes,
            EmploymentStatus declaredEmploymentStatus,
            String declaredEmployerName,
            String loanPurpose) {
        this.applicationId = applicationId;
        this.versionNumber = versionNumber;
        this.requestedAmountKes = requestedAmountKes;
        this.requestedTermMonths = requestedTermMonths;
        this.declaredMonthlyIncomeKes = declaredMonthlyIncomeKes;
        this.declaredEmploymentStatus = declaredEmploymentStatus;
        this.declaredEmployerName = declaredEmployerName;
        this.loanPurpose = loanPurpose;
        this.submittedAt = Instant.now();
    }
}
