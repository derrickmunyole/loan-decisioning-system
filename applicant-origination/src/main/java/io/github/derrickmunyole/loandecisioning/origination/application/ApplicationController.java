package io.github.derrickmunyole.loandecisioning.origination.application;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse create(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateApplicationRequest request) {
        return applicationService.createDraft(authentication.getName(), idempotencyKey, request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@applicationAccessGuard.isOwner(#id, authentication.name)")
    public ApplicationResponse update(
            @PathVariable UUID id, @Valid @RequestBody PatchApplicationRequest request) {
        return applicationService.updateDraft(id, request);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("@applicationAccessGuard.isOwner(#id, authentication.name)")
    public ApplicationResponse submit(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SubmitApplicationRequest request) {
        return applicationService.submit(id, idempotencyKey, request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@applicationAccessGuard.isOwner(#id, authentication.name)")
    public ApplicationResponse get(@PathVariable UUID id) {
        return applicationService.get(id);
    }
}
