package io.github.derrickmunyole.loandecisioning.decisioning;

import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import java.util.List;

record PolicyEvaluationResult(ApplicationStatus outcome, String band, List<String> reasons) {}
