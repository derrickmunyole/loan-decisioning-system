package io.github.derrickmunyole.loandecisioning.infrastructure.messaging;

import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Every {@code @RabbitListener} or {@code @Scheduled} method runs under a synthetic {@code
 * SYSTEM_SERVICE} principal, so system-driven effects (e.g. {@code @Audited} calls made from a
 * listener or a scheduled job) still produce a non-null actor instead of "anonymous". {@code
 * @Scheduled} was added to the pointcut in Epic 5.1, when {@code OfferExpiryJob} became the first
 * scheduled job needing it — {@code OutboxRelay} (the only prior one) never touches anything
 * security-context-dependent, so the gap never surfaced before.
 */
@Aspect
@Component
class SystemServicePrincipalAspect {

    private static final String SYSTEM_SERVICE_USERNAME = "system_service";

    @Around(
            "@annotation(org.springframework.amqp.rabbit.annotation.RabbitListener) || "
                    + "@annotation(org.springframework.scheduling.annotation.Scheduled)")
    Object runAsSystemService(ProceedingJoinPoint joinPoint) throws Throwable {
        var authentication =
                new UsernamePasswordAuthenticationToken(
                        SYSTEM_SERVICE_USERNAME,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_SERVICE")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        try {
            return joinPoint.proceed();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
