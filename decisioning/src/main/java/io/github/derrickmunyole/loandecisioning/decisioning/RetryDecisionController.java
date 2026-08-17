package io.github.derrickmunyole.loandecisioning.decisioning;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cases")
class RetryDecisionController {

    private final RetryDecisionService retryDecisionService;

    RetryDecisionController(RetryDecisionService retryDecisionService) {
        this.retryDecisionService = retryDecisionService;
    }

    @PostMapping("/{id}/retry-decision")
    RetryDecisionResponse retry(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return retryDecisionService.retry(authentication.getName(), id, idempotencyKey);
    }
}
