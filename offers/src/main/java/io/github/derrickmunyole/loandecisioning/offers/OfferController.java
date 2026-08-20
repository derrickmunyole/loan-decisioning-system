package io.github.derrickmunyole.loandecisioning.offers;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/offers")
class OfferController {

    private final OfferService offerService;

    OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    @PostMapping("/{id}/accept")
    OfferResponse accept(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return offerService.accept(authentication.getName(), id, idempotencyKey);
    }
}