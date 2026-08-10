package io.github.derrickmunyole.loandecisioning.security.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
