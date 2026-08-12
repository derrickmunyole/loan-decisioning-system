package io.github.derrickmunyole.loandecisioning.origination.application;

import jakarta.validation.constraints.AssertTrue;

public record SubmitApplicationRequest(@AssertTrue(message = "consent must be accepted to submit") boolean consentAccepted) {}
