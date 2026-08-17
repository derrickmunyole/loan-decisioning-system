package io.github.derrickmunyole.loandecisioning.decisioning;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cases")
class CaseDecisionController {

    private final CaseDecisionService caseDecisionService;

    CaseDecisionController(CaseDecisionService caseDecisionService) {
        this.caseDecisionService = caseDecisionService;
    }

    @PostMapping("/{id}/decision")
    CaseDecisionResponse decide(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CaseDecisionRequest request) {
        return caseDecisionService.decide(authentication.getName(), id, idempotencyKey, request);
    }
}
