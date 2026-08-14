# 0008. Synchronous WebClient + Resilience4j for the credit-score client, called outside any transaction

## Status

Accepted

## Context

Milestone 3, Epic 3.4 needed to call `credit-score-service` (a synchronous, deterministic FastAPI scorer, Epic 3.3) from the Java decision engine — the first outbound HTTP call this codebase has ever made. The roadmap's A.9/3.4 locks in "Synchronous WebClient call (Resilience4j timeout/circuit-breaker)"; two things needed deciding that the roadmap doesn't spell out: where the call happens relative to the transaction that persists a `Decision`, and how failure is represented to the rest of the system.

**Where the call happens.** Epic 3.1 already established a "one handler does the whole hop, in one transaction" pattern (`ApplicationSubmittedHandler`, `UnderwritingRequestedHandler`) whenever nothing downstream needed an intermediate event yet. The same shape was available here: extend `UnderwritingRequestedHandler` to also call the credit-score client and write the `Decision`, all inside the transaction that already saves the `UnderwritingSnapshot`. That would hold a DB transaction open across a network call for the duration of Resilience4j's own timeout budget — real connection-pool and lock-hold risk under load — and it would mean a credit-score outage rolls back the snapshot save too, conflating two unrelated failure domains (snapshot persistence succeeding is not conditional on the scorer being reachable).

**How failure is represented.** Blueprint §3 says third-party failures "must not silently produce an approval/decline; they result in `PENDING_REVIEW` or a recoverable exception." The application's real 16-state model (blueprint §4) has no `PENDING_REVIEW` state — the only status that fits "needs a human, not an automated outcome" is the existing `REFERRED`.

## Decision

**New event, new consumer, call outside any transaction.** `UnderwritingRequestedHandler` (unchanged in shape from Epic 3.1) publishes `underwriting.snapshot.created` — a blueprint-named event nothing had published until now — in the same transaction as the snapshot save, exactly how it already publishes `underwriting.requested`. A new `DecisionEngineHandler` consumes it in its own transaction, on its own thread: it calls `CreditScoreClient.score(...)` *before* opening any write, then persists `Decision` and drives the status transition only after the call has already returned.

`CreditScoreClient.score` is `@CircuitBreaker(name = "creditScore")` (`resilience4j-spring-boot3`) wrapping a `WebClient` call that blocks with a configured timeout (`Mono.block(Duration)`, not `TimeLimiter` — that annotation targets reactive/async return types, and this call is deliberately synchronous). Any exception — timeout, non-2xx, connection failure, or the breaker itself open — propagates uncaught from the client; `DecisionEngineHandler` catches broadly around the one call site and treats all of them the same way: `UNDERWRITING → REFERRED`, no `Decision` row (there's no valid score or `model_version` to record), plus an ops-visible `workflow_task` via a new `workflow.api.WorkflowTaskCreationService` port — the first caller of `workflow_task` creation from outside `workflow` itself, so `WorkflowTaskType` moved from `workflow.workqueue` into `workflow.api` alongside it, same "extract on the second real caller" pattern as `ApplicationVersionQueryService`/`ApplicationTransitionService` in Epic 2.3.

A second, unrelated "can't produce a normal decision" path — no published `PolicyVersion`/`ScorecardVersion`/`PricingVersion` yet — is deliberately *not* given the same bespoke treatment. It's a platform-configuration gap, not a per-application problem, so it's left to throw and fall through to the existing retry → DLQ → generic `workflow_task` path Epic 2.2 already built, rather than inventing a second custom status-transition-plus-ops-task shape for a scenario the roadmap's done-criterion doesn't name.

## Consequences

**`docs/blueprint.md`'s `PENDING_REVIEW` wording is corrected to `REFERRED` in the same PR** — there is no `PENDING_REVIEW` state in the real state model, and this ADR is where that divergence gets resolved rather than left implicit.

**A future reader of the roadmap's "one consumer, no intermediate events" precedent from Epic 2.3 would expect this hop to follow the same shape.** It doesn't, on purpose: 2.3's precedent applies when nothing downstream consumes an intermediate event; 3.4 is exactly the case where something finally does, and the transaction-boundary risk of a synchronous outbound HTTP call is a stronger reason to split than "fewer moving parts" is to not.

**The circuit breaker and the AMQP retry interceptor now overlap in scope.** Both exist to avoid hammering a failing dependency; a redelivered message after a transient failure will likely find the breaker already open and fail fast rather than repeat the network call, which is fine — the breaker's job is protecting `credit-score-service` across *all* applications during an outage, not just the one that first tripped it.

**No `Decision` row is ever written for the outage path**, even though it might be useful to record "we tried and failed" for audit purposes. Revisit if operations ever needs a queryable history of skipped automated attempts distinct from the `workflow_task` the outage already raises.
