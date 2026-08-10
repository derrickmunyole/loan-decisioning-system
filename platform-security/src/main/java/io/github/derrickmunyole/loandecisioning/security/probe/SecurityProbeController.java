package io.github.derrickmunyole.loandecisioning.security.probe;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exists only to exercise the security wiring end to end (endpoint-level role gating in {@link
 * io.github.derrickmunyole.loandecisioning.security.config.SecurityConfig}, plus one
 * method-level {@code @PreAuthorize} ownership check) until Milestone 1.4 adds real role-gated
 * endpoints.
 */
@RestController
@RequestMapping("/internal/security-probe")
public class SecurityProbeController {

    @GetMapping("/underwriter-only")
    public String underwriterOnly(Authentication authentication) {
        return "hello " + authentication.getName();
    }

    @GetMapping("/self/{username}")
    @PreAuthorize("#username == authentication.name")
    public String selfOnly(@PathVariable String username) {
        return "hello " + username;
    }
}
