package io.github.derrickmunyole.loandecisioning.offers;

import io.github.derrickmunyole.loandecisioning.workflow.api.ApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Immutable commercial terms (blueprint §5) with a mutable {@code status} — same shape as {@code
 * Application} itself, not {@code Decision}'s append-only pattern, since an offer's own lifecycle
 * (OFFERED → ACCEPTED/OFFER_EXPIRED/WITHDRAWN) has to change in place. Reuses {@link
 * ApplicationStatus} for that lifecycle rather than a new {@code OfferStatus} enum, the same way
 * {@code Decision.outcome} already reuses it — {@code Offer.status} and {@code Application.status}
 * change in lockstep on every transition (accept moves both), so a second enum would just be a
 * second source of truth to keep in sync.
 */
@Entity
@Getter
@Table(name = "offer")
public class Offer {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "decision_id", nullable = false, unique = true)
    private UUID decisionId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "principal_kes", nullable = false)
    private BigDecimal principalKes;

    @Column(name = "apr_basis_points", nullable = false)
    private int aprBasisPoints;

    @Column(name = "term_months", nullable = false)
    private int termMonths;

    @Column(name = "monthly_payment_kes", nullable = false)
    private BigDecimal monthlyPaymentKes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Offer() {}

    public Offer(
            UUID decisionId,
            UUID applicationId,
            BigDecimal principalKes,
            int aprBasisPoints,
            int termMonths,
            BigDecimal monthlyPaymentKes,
            Instant expiresAt) {
        this.decisionId = decisionId;
        this.applicationId = applicationId;
        this.principalKes = principalKes;
        this.aprBasisPoints = aprBasisPoints;
        this.termMonths = termMonths;
        this.monthlyPaymentKes = monthlyPaymentKes;
        this.status = ApplicationStatus.OFFERED;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    /**
     * Checks the actual clock, not just {@code status} — a request can arrive after {@code
     * expiresAt} but before the sweep job ({@link OfferExpiryJob}) has gotten around to flipping
     * this offer to {@code OFFER_EXPIRED}. {@code WorkflowTransitionService}'s shared table would
     * happily permit {@code OFFERED → ACCEPTED} regardless of the clock, the same reason {@code
     * CaseDecisionCommandService} (Epic 4.1) can't rely on that table alone either.
     */
    public void accept(Instant now) {
        if (status != ApplicationStatus.OFFERED) {
            throw new OfferNotAcceptableException(id, status);
        }
        if (now.isAfter(expiresAt)) {
            this.status = ApplicationStatus.OFFER_EXPIRED;
            throw new OfferExpiredException(id);
        }
        this.status = ApplicationStatus.ACCEPTED;
    }

    /**
     * Self-idempotent, same shape as {@code WorkflowTask.markResolved} — a no-op for anything not
     * currently {@code OFFERED}, so the sweep job doesn't need its own guard against re-expiring
     * an offer that was already accepted or withdrawn in the meantime.
     */
    public boolean expireIfOverdue(Instant now) {
        if (status != ApplicationStatus.OFFERED || !now.isAfter(expiresAt)) {
            return false;
        }
        this.status = ApplicationStatus.OFFER_EXPIRED;
        return true;
    }
}
