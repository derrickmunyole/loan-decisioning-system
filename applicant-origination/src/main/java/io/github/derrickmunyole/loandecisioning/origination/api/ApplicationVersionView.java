package io.github.derrickmunyole.loandecisioning.origination.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read-only snapshot of an immutable {@code ApplicationVersion}, exposed for modules that need
 * the declared submission data without reaching into {@code origination}'s internal entities.
 * Fields are primitive/String-typed (not the internal {@code EmploymentStatus} enum) so this type
 * stays fully self-contained across the module boundary.
 */
public record ApplicationVersionView(
        UUID id,
        UUID applicationId,
        int versionNumber,
        BigDecimal requestedAmountKes,
        int requestedTermMonths,
        BigDecimal declaredMonthlyIncomeKes,
        String declaredEmploymentStatus,
        String declaredEmployerName,
        String loanPurpose) {}
