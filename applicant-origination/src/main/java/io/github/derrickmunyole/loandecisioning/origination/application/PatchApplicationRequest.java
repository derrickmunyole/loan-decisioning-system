package io.github.derrickmunyole.loandecisioning.origination.application;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Replaces the entire draft each call — no partial-field-merge semantics. */
public record PatchApplicationRequest(
        @NotNull @DecimalMin("50000") @DecimalMax("1250000") BigDecimal requestedAmountKes,
        @NotNull Integer requestedTermMonths,
        @NotNull @DecimalMin("0") BigDecimal declaredMonthlyIncomeKes,
        @NotNull EmploymentStatus declaredEmploymentStatus,
        String declaredEmployerName,
        String loanPurpose) {}
