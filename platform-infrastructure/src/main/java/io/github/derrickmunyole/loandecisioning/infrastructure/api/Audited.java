package io.github.derrickmunyole.loandecisioning.infrastructure.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method whose successful completion should produce an {@link AuditEvent}. {@code
 * targetId} is a SpEL expression evaluated against the method's parameters (e.g. {@code
 * "#username"} or {@code "#request.applicationId()"}). Only fires on normal return — a method
 * that throws produces no audit row, since "this happened" would be misleading.
 *
 * <p>Captures actor/action/target/correlation ID only. Before/after diff capture is deliberately
 * not built yet — nothing in Milestone 1 mutates state in a way that needs it; add it when a real
 * use case does, not speculatively.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audited {

    String action();

    String targetType();

    String targetId();
}
