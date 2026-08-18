package io.github.derrickmunyole.loandecisioning.decisioning;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Moved here from {@code applicant-origination} in Epic 4.3 — same URL, different owning module
 * (see {@link ApplicationTimelineService}'s javadoc for why). Returns one of two shapes depending
 * on caller role: an {@link ApplicationTimelineResponse} aggregate for staff, or a plain {@code
 * List<TimelineEvent>} for the owning applicant — {@link ApplicationTimelineService} decides which.
 */
@RestController
@RequestMapping("/applications")
class ApplicationTimelineController {

    private final ApplicationTimelineService applicationTimelineService;

    ApplicationTimelineController(ApplicationTimelineService applicationTimelineService) {
        this.applicationTimelineService = applicationTimelineService;
    }

    @GetMapping("/{id}/timeline")
    Object timeline(Authentication authentication, @PathVariable UUID id) {
        return applicationTimelineService.getTimeline(authentication, id);
    }
}
