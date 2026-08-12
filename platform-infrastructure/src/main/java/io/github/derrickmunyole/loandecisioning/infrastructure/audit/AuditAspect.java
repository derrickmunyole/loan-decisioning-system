package io.github.derrickmunyole.loandecisioning.infrastructure.audit;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.Audited;
import io.github.derrickmunyole.loandecisioning.infrastructure.correlation.CorrelationIdFilter;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
class AuditAspect {

    private static final String ANONYMOUS_ACTOR = "anonymous";

    private final AuditEventRepository auditEventRepository;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    AuditAspect(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @AfterReturning("@annotation(audited)")
    void recordAudit(JoinPoint joinPoint, Audited audited) {
        String targetId = evaluateTargetId(joinPoint, audited.targetId());
        String actor = currentActor();
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        auditEventRepository.save(
                new AuditEvent(actor, audited.action(), audited.targetType(), targetId, correlationId));
    }

    private String evaluateTargetId(JoinPoint joinPoint, String expression) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }
        Object value = expressionParser.parseExpression(expression).getValue(context);
        return String.valueOf(value);
    }

    private String currentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : ANONYMOUS_ACTOR;
    }
}
