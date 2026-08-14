package io.github.derrickmunyole.loandecisioning.decisioning;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/policies")
class PolicyVersionController {

    private final PolicyVersionService policyVersionService;

    PolicyVersionController(PolicyVersionService policyVersionService) {
        this.policyVersionService = policyVersionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PolicyVersionResponse create(@Valid @RequestBody CreatePolicyVersionRequest request) {
        return policyVersionService.create(request);
    }

    @PostMapping("/{id}/publish")
    PolicyVersionResponse publish(@PathVariable UUID id) {
        return policyVersionService.publish(id);
    }
}
