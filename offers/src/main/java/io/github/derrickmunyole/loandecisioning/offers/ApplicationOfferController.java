package io.github.derrickmunyole.loandecisioning.offers;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/applications")
class ApplicationOfferController {

    private final ApplicationOfferQueryService applicationOfferQueryService;

    ApplicationOfferController(ApplicationOfferQueryService applicationOfferQueryService) {
        this.applicationOfferQueryService = applicationOfferQueryService;
    }

    @GetMapping("/{id}/offer")
    OfferResponse getOffer(Authentication authentication, @PathVariable UUID id) {
        return applicationOfferQueryService.getCurrentOffer(authentication.getName(), id);
    }
}
