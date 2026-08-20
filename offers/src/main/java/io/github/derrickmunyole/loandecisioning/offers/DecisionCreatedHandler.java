package io.github.derrickmunyole.loandecisioning.offers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.derrickmunyole.loandecisioning.decisioning.api.DecisionCreatedEvent;
import io.github.derrickmunyole.loandecisioning.decisioning.api.DecisionQueryService;
import io.github.derrickmunyole.loandecisioning.decisioning.api.DecisionView;
import io.github.derrickmunyole.loandecisioning.decisioning.api.PricingVersionQueryService;
import io.github.derrickmunyole.loandecisioning.decisioning.api.PricingVersionView;
import io.github.derrickmunyole.loandecisioning.infrastructure.api.AmqpDedupeService;
import io.github.derrickmunyole.loandecisioning.origination.api.ApplicationVersionQueryService;
import io.github.derrickmunyole.loandecisioning.origination.api.ApplicationVersionView;
import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Split out from {@link DecisionCreatedListener} for the same ack-after-commit reason as {@code
 * NotificationRequestedHandler} (ADR 0004).
 *
 * <p>{@code decision.created} fires for every {@code Decision}, not just approvals — this handler
 * is the one that filters to {@code APPROVED}/{@code CONDITIONAL_APPROVAL}, the same "publish the
 * fact, let the consumer decide" shape {@code application.submitted} already established. The
 * {@code consumed_event} dedupe check covers redelivery of the same message; {@code
 * OfferRepository.existsByDecisionId} plus the DB's own unique constraint on {@code decision_id}
 * (see the {@code offers} migration) is the independent backstop against two distinct {@code
 * decision.created} deliveries for the same decision, the same two-layer pattern Epic 3.1
 * established for {@code underwriting_snapshot}.
 */
@Service
class DecisionCreatedHandler {

    static final String CONSUMER_NAME = "decision-created-listener";
    private static final Set<ApplicationStatus> OFFERABLE_OUTCOMES =
            EnumSet.of(ApplicationStatus.APPROVED, ApplicationStatus.CONDITIONAL_APPROVAL);

    private final AmqpDedupeService amqpDedupeService;
    private final DecisionQueryService decisionQueryService;
    private final PricingVersionQueryService pricingVersionQueryService;
    private final ApplicationVersionQueryService applicationVersionQueryService;
    private final OfferRepository offerRepository;
    private final Clock clock;
    private final Duration offerExpiryPeriod;
    private final ObjectMapper objectMapper;

    DecisionCreatedHandler(
            AmqpDedupeService amqpDedupeService,
            DecisionQueryService decisionQueryService,
            PricingVersionQueryService pricingVersionQueryService,
            ApplicationVersionQueryService applicationVersionQueryService,
            OfferRepository offerRepository,
            Clock clock,
            @Value("${app.offers.expiry-period:P14D}") Duration offerExpiryPeriod,
            ObjectMapper objectMapper) {
        this.amqpDedupeService = amqpDedupeService;
        this.decisionQueryService = decisionQueryService;
        this.pricingVersionQueryService = pricingVersionQueryService;
        this.applicationVersionQueryService = applicationVersionQueryService;
        this.offerRepository = offerRepository;
        this.clock = clock;
        this.offerExpiryPeriod = offerExpiryPeriod;
        this.objectMapper = objectMapper;
    }

    @Transactional
    void process(Message message) throws IOException {
        UUID eventId = UUID.fromString((String) message.getMessageProperties().getHeader("eventId"));
        if (amqpDedupeService.alreadyConsumed(CONSUMER_NAME, eventId)) {
            return;
        }

        DecisionCreatedEvent event = objectMapper.readValue(message.getBody(), DecisionCreatedEvent.class);

        if (OFFERABLE_OUTCOMES.contains(event.outcome()) && !offerRepository.existsByDecisionId(event.decisionId())) {
            buildOffer(event);
        }

        amqpDedupeService.markConsumed(CONSUMER_NAME, eventId);
    }

    private void buildOffer(DecisionCreatedEvent event) {
        DecisionView decision =
                decisionQueryService
                        .findById(event.decisionId())
                        .orElseThrow(() -> new NoSuchElementException("No Decision " + event.decisionId()));
        ApplicationVersionView version =
                applicationVersionQueryService
                        .findById(decision.applicationVersionId())
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "No ApplicationVersion " + decision.applicationVersionId()));
        PricingVersionView pricing =
                pricingVersionQueryService
                        .findById(decision.pricingVersionId())
                        .orElseThrow(
                                () -> new NoSuchElementException("No PricingVersion " + decision.pricingVersionId()));

        PricingRulesConfig rules = parsePricingRules(pricing.aprTermRulesJson());
        PricingEvaluator.Terms terms = PricingEvaluator.evaluate(event.outcome().name(), rules.tiers());

        BigDecimal principal = version.requestedAmountKes();
        BigDecimal monthlyPayment =
                AmortizationCalculator.monthlyPayment(principal, terms.aprBasisPoints(), terms.termMonths());
        Instant expiresAt = clock.instant().plus(offerExpiryPeriod);

        offerRepository.save(
                new Offer(
                        decision.id(),
                        event.applicationId(),
                        principal,
                        terms.aprBasisPoints(),
                        terms.termMonths(),
                        monthlyPayment,
                        expiresAt));
    }

    private PricingRulesConfig parsePricingRules(String aprTermRulesJson) {
        try {
            return objectMapper.readValue(aprTermRulesJson, PricingRulesConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse PricingVersion aprTermRules: " + aprTermRulesJson, e);
        }
    }

    private record PricingRulesConfig(Map<String, PricingEvaluator.Terms> tiers) {}
}
