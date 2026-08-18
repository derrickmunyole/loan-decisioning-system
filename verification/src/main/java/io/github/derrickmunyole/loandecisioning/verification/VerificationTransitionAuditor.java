package io.github.derrickmunyole.loandecisioning.verification;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.Audited;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Closes a real NFR gap surfaced in Epic 4.3: the blueprint states "all privileged actions and
 * state transitions appear in append-only audit events," but {@link ApplicationSubmittedHandler}'s
 * two automated hops (SUBMITTED -&gt; VERIFYING, VERIFYING -&gt; UNDERWRITING) never called {@link
 * Audited} at all. A separate bean, not two more annotated methods on {@code
 * ApplicationSubmittedHandler} itself, because {@code @Audited} is AOP-woven via a Spring proxy —
 * calling an annotated method on {@code this} from inside the same class bypasses the proxy
 * entirely and silently never audits. These no-op bodies exist only to carry the annotation; the
 * actor resolves to {@code system_service} via {@code SystemServicePrincipalAspect}'s synthetic
 * principal, since every call here originates inside a {@code @RabbitListener}.
 */
@Service
class VerificationTransitionAuditor {

    @Audited(
            action = "APPLICATION_VERIFICATION_STARTED",
            targetType = "Application",
            targetId = "#applicationId")
    void recordVerificationStarted(UUID applicationId) {}

    @Audited(
            action = "APPLICATION_VERIFICATION_COMPLETED",
            targetType = "Application",
            targetId = "#applicationId")
    void recordVerificationCompleted(UUID applicationId) {}
}
