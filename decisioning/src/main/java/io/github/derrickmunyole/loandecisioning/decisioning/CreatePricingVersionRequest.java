package io.github.derrickmunyole.loandecisioning.decisioning;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

record CreatePricingVersionRequest(@NotEmpty Map<String, Object> aprTermRules) {}
