package io.github.derrickmunyole.loandecisioning.infrastructure.probe;

import jakarta.validation.constraints.NotBlank;

record DemoEventRequest(@NotBlank String recipient) {}
