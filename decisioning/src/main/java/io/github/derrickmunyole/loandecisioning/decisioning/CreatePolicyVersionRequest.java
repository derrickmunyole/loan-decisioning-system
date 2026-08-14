package io.github.derrickmunyole.loandecisioning.decisioning;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Map;

record CreatePolicyVersionRequest(
        @NotNull LocalDate effectiveDate, @NotEmpty Map<String, Object> rules) {}
