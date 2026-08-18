package io.github.derrickmunyole.loandecisioning.security.config;

import io.github.derrickmunyole.loandecisioning.security.jwt.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider::authenticate;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session ->
                                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/auth/login")
                                        .permitAll()
                                        .requestMatchers("/actuator/health/**")
                                        .permitAll()
                                        // response.sendError() forwards internally to /error, and
                                        // JwtAuthenticationFilter (a OncePerRequestFilter) skips
                                        // ERROR dispatches by default, so without this the real
                                        // status (e.g. 400 from a failed @Valid) gets masked by a
                                        // 403 from the security chain denying the unauthenticated
                                        // forward.
                                        .requestMatchers("/error")
                                        .permitAll()
                                        // Ordered before the broader /applications/** rule below
                                        // — this is the one /applications/** endpoint every human
                                        // role can reach, not just APPLICANT; ApplicationTimeline
                                        // Service dispatches the actual response shape by role.
                                        .requestMatchers("/applications/*/timeline")
                                        .hasAnyRole(
                                                "APPLICANT",
                                                "UNDERWRITER",
                                                "OPERATIONS_ANALYST",
                                                "POLICY_ADMIN",
                                                "AUDITOR")
                                        .requestMatchers("/applications/**")
                                        .hasRole("APPLICANT")
                                        .requestMatchers("/work-queue")
                                        .hasAnyRole("UNDERWRITER", "OPERATIONS_ANALYST")
                                        // Ordered before the broader /cases/** rule below —
                                        // requestMatchers is first-match-wins, and this one
                                        // targets a different role than the rest of /cases/**.
                                        .requestMatchers("/work-queue/*/resolve", "/cases/*/retry-decision")
                                        .hasRole("OPERATIONS_ANALYST")
                                        .requestMatchers("/cases/**")
                                        .hasRole("UNDERWRITER")
                                        .requestMatchers("/policies/**", "/scorecards/**", "/pricing/**")
                                        .hasRole("POLICY_ADMIN")
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(
                        jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
