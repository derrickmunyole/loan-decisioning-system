package io.github.derrickmunyole.loandecisioning.workflow.workqueue;

/** {@code RESOLVED} is set by {@link WorkflowTask#markResolved} (Epic 4.2). */
public enum WorkflowTaskStatus {
    OPEN,
    RESOLVED
}
