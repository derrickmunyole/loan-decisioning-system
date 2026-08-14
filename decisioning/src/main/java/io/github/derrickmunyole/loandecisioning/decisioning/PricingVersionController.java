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
@RequestMapping("/pricing")
class PricingVersionController {

    private final PricingVersionService pricingVersionService;

    PricingVersionController(PricingVersionService pricingVersionService) {
        this.pricingVersionService = pricingVersionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PricingVersionResponse create(@Valid @RequestBody CreatePricingVersionRequest request) {
        return pricingVersionService.create(request);
    }

    @PostMapping("/{id}/publish")
    PricingVersionResponse publish(@PathVariable UUID id) {
        return pricingVersionService.publish(id);
    }
}
