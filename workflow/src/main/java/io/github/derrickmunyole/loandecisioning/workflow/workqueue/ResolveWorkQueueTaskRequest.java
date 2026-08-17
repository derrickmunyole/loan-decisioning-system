package io.github.derrickmunyole.loandecisioning.workflow.workqueue;

import jakarta.validation.constraints.NotBlank;

record ResolveWorkQueueTaskRequest(@NotBlank String resolution) {}
