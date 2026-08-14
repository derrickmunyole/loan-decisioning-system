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
@RequestMapping("/scorecards")
class ScorecardVersionController {

    private final ScorecardVersionService scorecardVersionService;

    ScorecardVersionController(ScorecardVersionService scorecardVersionService) {
        this.scorecardVersionService = scorecardVersionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ScorecardVersionResponse create(@Valid @RequestBody CreateScorecardVersionRequest request) {
        return scorecardVersionService.create(request);
    }

    @PostMapping("/{id}/publish")
    ScorecardVersionResponse publish(@PathVariable UUID id) {
        return scorecardVersionService.publish(id);
    }
}
