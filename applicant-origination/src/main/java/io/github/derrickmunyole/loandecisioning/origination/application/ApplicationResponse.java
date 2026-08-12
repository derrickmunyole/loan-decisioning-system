package io.github.derrickmunyole.loandecisioning.origination.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Applicant-safe projection — reused across create, patch, submit, and GET responses. */
public record ApplicationResponse(
        UUID id,
        ApplicationStatus status,
        Integer currentVersionNumber,
        BigDecimal requestedAmountKes,
        Integer requestedTermMonths,
        BigDecimal declaredMonthlyIncomeKes,
        EmploymentStatus declaredEmploymentStatus,
        String declaredEmployerName,
        String loanPurpose,
        Instant createdAt,
        Instant updatedAt) {

    public static ApplicationResponse from(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getStatus(),
                application.getCurrentVersionNumber(),
                application.getRequestedAmountKes(),
                application.getRequestedTermMonths(),
                application.getDeclaredMonthlyIncomeKes(),
                application.getDeclaredEmploymentStatus(),
                application.getDeclaredEmployerName(),
                application.getLoanPurpose(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }
}
