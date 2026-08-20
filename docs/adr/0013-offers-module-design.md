# 0013. Offers module: trigger mechanism, pricing shape, and audit target type

## Status

Accepted

## Context

Milestone 5, Epic 5.1 needed a new `offers` module: a versioned `Offer` auto-created from an `APPROVED`/`CONDITIONAL_APPROVAL` `Decision`, an idempotent `POST /offers/{id}/accept`, and a scheduled expiry sweep. Several decisions had to be made during implementation that the roadmap's one-line scope doesn't settle.

**How an approval becomes an offer.** `offers` needs to read `Decision` (owned by `decisioning`) and drive `Application`/`Offer` state via `workflow.api`/`origination.api`. For `offers` to do that, it must depend on `decisioning`; `decisioning` can never depend back on `offers` without a cycle. A synchronous call from `DecisionEngineHandler`/`CaseDecisionCommandService` into `offers` is therefore structurally impossible — the same dependency-direction constraint ADR 0010 and ADR 0012 already resolved for `retry-decision` and the timeline endpoint.

**Where the applicant sees the offer.** Blueprint §3 lists "view status/offer" as an applicant permission, but the roadmap's endpoint table only names `POST /offers/{id}/accept`. Without a GET, there is no way for the applicant to discover an offer id to accept. The natural URL, `GET /applications/{id}/offer`, faces the identical dependency-direction problem: `applicant-origination` cannot depend on `offers`.

**How to price an offer.** `PricingVersion.aprTermRulesJson` has existed since Epic 3.2 but was, per its own commit history, "captured for traceability only, never consulted" — no consumer had ever defined what shape that JSON should take.

**Whether `Offer` needs its own status enum.** `Offer` has a mutable lifecycle (`OFFERED → ACCEPTED | OFFER_EXPIRED | WITHDRAWN`) that already exists, named identically, as four values of `workflow.api.ApplicationStatus` — the same values `Application.status` moves through in lockstep.

**How offer-lifecycle audit events surface on the timeline.** Epic 4.3's `GET /applications/{id}/timeline` aggregates audit events by querying `AuditEventRepository` with a fixed `targetType`. Whatever `targetType` this epic's new `@Audited` calls use determines whether staff ever see offer creation/acceptance/expiry in that aggregate at all.

## Decision

**`decision.created` is a real, always-published event**, not scoped to approval outcomes. Blueprint §7 already names it. Published from both places a `Decision` row is saved — `DecisionEngineHandler.recordDecision` and `CaseDecisionCommandService.decide`'s override branch — carrying `(decisionId, applicationId, outcome)`. `offers`'s own `DecisionCreatedHandler` filters to `APPROVED`/`CONDITIONAL_APPROVAL`; the publish side does not. This is the same "publish the fact, let the consumer decide" shape `application.submitted` already established, and it means an underwriter's override to `APPROVED` produces an offer through the identical path an automated approval does, with no special-casing.

**`GET /applications/{id}/offer` lives in `offers`, not `applicant-origination`** — same reasoning ADR 0012 already used for the timeline endpoint. `POST /offers/{id}/accept` was always going to live in `offers`; putting the GET there too keeps both offer-related endpoints in the module that owns the data, at the cost of the URL's `/applications/{id}/...` prefix not matching its owning module (an established, now three-times-repeated tradeoff in this codebase).

**`PricingVersion.aprTermRulesJson` is now defined as `{"tiers": {"<outcome>": {"aprBasisPoints": N, "termMonths": N}}}`**, keyed by the `Decision`'s own outcome (`APPROVED` vs `CONDITIONAL_APPROVAL`) rather than by credit-score band. The `decision.created` payload already carries the outcome; keying by band would have required extending `decisioning.api` to expose a raw score/band that nothing currently persists on `Decision`, for a distinction the roadmap's Epic 5.1 scope doesn't ask for. `PricingEvaluator`/`AmortizationCalculator` are pure, framework-free, table-tested functions, matching `decisioning`'s `PolicyEvaluator` precedent. Seven pre-existing integration tests across `decisioning`/`workflow` had published a placeholder pricing shape (`{"baseAprPercent": ..., "termMonths": ...}`) from before any real consumer existed; all seven were updated to the real shape as part of this epic, since every one of their `APPROVED`/`CONDITIONAL_APPROVAL` outcomes would otherwise have silently dead-lettered a `decision.created` message on every run.

**`Offer.status` reuses `ApplicationStatus`**, not a new `OfferStatus` enum — the same precedent `Decision.outcome` already set. A second enum would only be a second source of truth to keep in sync, since the two fields change together on every transition.

**All three offer-lifecycle audit actions — `APPLICATION_OFFERED`, `OFFER_ACCEPTED`, `OFFER_EXPIRED` — target `"Application"`**, not `"Offer"`. The alternative (targeting `"Offer"`, since that's the entity that technically changed) would have made acceptance and expiry invisible to the Epic 4.3 timeline endpoint's `events` section, which queries `targetType = "Application"` — staff would see an offer get created and then nothing else, even after it was accepted or expired. `OfferAcceptCommandService.accept` and `OfferExpiryAuditor.recordExpiry` both take `applicationId` as an explicit parameter for this, since `@Audited`'s SpEL `targetId` expression only sees method parameters, never a return value — the same shape `CaseDecisionCommandService.decide` already uses for the same reason.

**`Offer.accept(Instant now)` re-checks expiry itself**, not just `status` — `WorkflowTransitionService`'s shared table permits `OFFERED → ACCEPTED` unconditionally, so a request arriving after `expiresAt` but before the sweep job runs must be rejected by `offers`'s own domain method, the same "shared table alone isn't a strict-enough guard" reasoning `CaseDecisionCommandService` already established for `REFERRED`-only overrides (ADR 0007's status update).

**`SystemServicePrincipalAspect`'s pointcut now covers `@Scheduled`, not just `@RabbitListener`.** `OfferExpiryJob` is the first scheduled job needing to call an `@Audited` method; without this widening, its actor would have recorded as `"anonymous"` instead of `"system_service"`. `OutboxRelay`, the only prior `@Scheduled` job, never touched anything security-context-dependent, so this gap existed silently since Milestone 1.3 and only surfaced building this epic.

## Consequences

An underwriter override and an automated approval produce an offer through literally the same consumer code, which is the main correctness win of the event-based trigger — there is no second code path to keep in sync. The cost is eventual consistency: an approval visibly reaches `OFFERED` only after the next outbox relay tick, not synchronously with the decision, same as every other cross-module hop in this system.

Pricing tiered by outcome rather than by score band is deliberately less expressive than a real lender's rate sheet would be — a future epic wanting risk-based pricing within a single outcome band would need to extend `decisioning.api` to expose the score/band `offers` doesn't have today, and re-key `aprTermRulesJson` accordingly. That's an additive, not breaking, change to a JSON shape this ADR is the first to define.

Auditing all three offer-lifecycle events under `"Application"` means the timeline's `events` section is now the complete story for both application-level and offer-level automated actions, at the cost of `Offer`-scoped audit queries (e.g. "show me every event for this specific offer id" once an application has more than one, if a future epic ever re-offers after `OFFER_EXPIRED`/`WITHDRAWN`) not being directly queryable by `targetId` — they'd need to be found via the owning application instead.
