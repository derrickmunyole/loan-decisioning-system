package io.github.derrickmunyole.loandecisioning.origination.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Applicant profile fields are only used the first time this username creates an application. */
public record CreateApplicationRequest(
        @NotBlank String fullName, @NotBlank @Email String email, @NotBlank String phone) {}
