package io.github.derrickmunyole.loandecisioning.infrastructure.api;

import io.github.derrickmunyole.loandecisioning.infrastructure.messaging.ConsumedEvent;
import io.github.derrickmunyole.loandecisioning.infrastructure.messaging.ConsumedEventRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The {@code consumed_event} dedupe pattern, generalized for listeners living outside this
 * module. Every {@code @RabbitListener} until now has lived inside {@code platform-infrastructure}
 * and could reach {@link ConsumedEventRepository} directly; a listener outside this module can't
 * (see {@code ModuleBoundaryTest}), so this is the boundary-respecting way in. Imperative, not
 * callback-based — mirrors {@code NotificationRequestedHandler}'s existing
 * check-then-do-work-then-mark shape rather than wrapping the caller's work in a
 * {@code Supplier}, which would obscure the call site (same reasoning as ADR 0007's rejection of a
 * callback-taking {@code WorkflowTransitionService}). Not {@code @Transactional} itself — the
 * caller's own {@code @Transactional} handler method is expected to wrap both the dedupe check and
 * the business write in one transaction, same as {@code NotificationRequestedHandler} already does
 * inline.
 */
@Service
public class AmqpDedupeService {

    private final ConsumedEventRepository consumedEventRepository;

    public AmqpDedupeService(ConsumedEventRepository consumedEventRepository) {
        this.consumedEventRepository = consumedEventRepository;
    }

    public boolean alreadyConsumed(String consumerName, UUID eventId) {
        return consumedEventRepository.existsByConsumerNameAndEventId(consumerName, eventId);
    }

    public void markConsumed(String consumerName, UUID eventId) {
        consumedEventRepository.save(new ConsumedEvent(consumerName, eventId));
    }
}
