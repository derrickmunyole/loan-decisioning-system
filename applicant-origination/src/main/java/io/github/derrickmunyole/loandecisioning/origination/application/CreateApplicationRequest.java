package io.github.derrickmunyole.loandecisioning.origination.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Applicant profile fields are only used the first time this username creates an application. */
public record CreateApplicationRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email @Size(max = 200) String email,
        @NotBlank @Size(max = 30) String phone) {}
