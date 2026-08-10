package io.github.derrickmunyole.loandecisioning.infrastructure.probe;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exists only to exercise the outbox/relay/consumer/audit/notification plumbing end to end until
 * a real business flow (Epic 1.4+) produces {@code notification.requested} events itself.
 */
@RestController
@RequestMapping("/internal/plumbing-probe")
public class PlumbingProbeController {

    private final PlumbingProbeService plumbingProbeService;

    public PlumbingProbeController(PlumbingProbeService plumbingProbeService) {
        this.plumbingProbeService = plumbingProbeService;
    }

    @PostMapping("/demo-event")
    public DemoEventResponse createDemoEvent(@Valid @RequestBody DemoEventRequest request) {
        var event = plumbingProbeService.createDemoEvent(request.recipient());
        return new DemoEventResponse(event.notificationId());
    }
}
