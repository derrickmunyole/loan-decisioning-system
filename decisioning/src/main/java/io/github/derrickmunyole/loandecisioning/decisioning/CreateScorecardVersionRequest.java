package io.github.derrickmunyole.loandecisioning.decisioning;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

record CreateScorecardVersionRequest(@NotEmpty Map<String, Object> formulaConfig) {}
