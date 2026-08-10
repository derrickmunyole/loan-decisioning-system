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
 * Every {@code @RabbitListener} method runs under a synthetic {@code SYSTEM_SERVICE} principal,
 * so system-driven effects (e.g. {@code @Audited} calls made from a listener) still produce a
 * non-null actor instead of "anonymous".
 */
@Aspect
@Component
class SystemServicePrincipalAspect {

    private static final String SYSTEM_SERVICE_USERNAME = "system_service";

    @Around("@annotation(org.springframework.amqp.rabbit.annotation.RabbitListener)")
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
