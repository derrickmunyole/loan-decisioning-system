# 0007. WorkflowTransitionService is validation-only, not an audit/outbox owner

## Status

Accepted

## Context

Milestone 2, Epic 2.1 needed the hand-rolled state machine core the roadmap locks in at A.3: "an allowed-transitions map validated inside a `WorkflowTransitionService` (`@Transactional`, emits audit + outbox atomically, throws a domain exception mapped to 409 on an illegal transition)." Taken literally, that phrasing means the service itself writes the audit row and enqueues the outbox event. Two things stand in the way of doing that literally:

- ADR 0001's module-boundary rule means `workflow` cannot import `applicant-origination`'s `Application` entity or repository, so it cannot persist a status change itself — only the aggregate-owning module can.
- `@Audited`'s `action` attribute (see `AuditAspect`) is a compile-time literal, not SpEL-evaluated like `targetId` is. A single generic `WorkflowTransitionService` method reused across different transitions has no fixed action string to give it, so it cannot be `@Audited` in the way every other mutating service method in this codebase is.

Three alternatives were evaluated in conversation before building: (1) a pure validation service, with the caller keeping ownership of the entity mutation, `@Audited`, and outbox enqueue exactly as `ApplicationCommandService.submit` already does; (2) a callback-taking `transition(...)` method where `WorkflowTransitionService` itself invokes a caller-supplied mutation `Runnable` and then writes audit + outbox, which would require either extending `AuditAspect` to support a SpEL-evaluated `action` or introducing a new imperative audit-write API alongside the existing declarative `@Audited` one; (3) a second, workflow-owned `state_transition_log` table, parallel to `audit_event`, sidestepping the `@Audited` limitation entirely.

## Decision

**Pure validation service.** `WorkflowTransitionService.validateTransition(ApplicationStatus from, ApplicationStatus to)` does one thing: look up `from` in a fixed `Map<ApplicationStatus, Set<ApplicationStatus>>` transition table (built from `docs/blueprint.md` section 4) and throw `IllegalApplicationTransitionException` if `to` isn't a legal target. No `@Transactional`, no repository, no audit write, no outbox call. `ApplicationCommandService.submit` calls it once per hop (`DRAFT→SUBMITTED`, then `SUBMITTED→VERIFYING`) and, exactly as before, does its own entity mutation, keeps its single `@Audited(action = "APPLICATION_SUBMITTED")` covering the whole method, and enqueues `ApplicationSubmittedEvent` via `OutboxEventPublisher` itself.

Rejected alternative (2) for widening the change into `platform-infrastructure` — code every module depends on — to serve a concern (multi-aggregate transition audit) that has exactly one aggregate (`Application`) actually implemented today; Offer and Funding are Milestone 5 and their real shape isn't known yet, so building the generic callback contract now is designing against a guess. Rejected alternative (3) because it duplicates `audit_event`'s job rather than solving a genuinely different problem the way the outbox/idempotency/inbox trio (ADR 0005) does, and risks a real regression: `GET /applications/{id}/timeline` reads only `AuditQueryService` today, so a second log source would need its own timeline merge or transitions would silently be invisible to it.

**Atomicity is achieved by shared transaction scope, not by this service owning the writes.** `ApplicationCommandService.submit` is one `@Transactional` method; `WorkflowTransitionService.validateTransition` calls happen inside it, so a rollback undoes the status change, version/consent/document writes, and the outbox enqueue together, same as it did before this epic. The roadmap's "emits audit + outbox atomically" is satisfied as a transactional property of the whole method, not as a responsibility physically inside `WorkflowTransitionService`.

## Consequences

**A future reader of the roadmap text alone would expect audit-writing code inside `WorkflowTransitionService` and not find it.** This ADR is that documented divergence — worth checking against if Milestone 5's Offer/Funding transitions turn out to need a shared audit/outbox path after all; revisit alternative (2) then, with two real aggregates' actual shapes in hand instead of one guessed-at shape.

**No generic "show me every state transition" query exists.** Each transition's audit trail rides on whatever business-event action the calling module already logs (`APPLICATION_SUBMITTED`, not a uniform `APPLICATION_STATUS_CHANGED`). Acceptable for now since `docs/blueprint.md`'s timeline requirement is per-application chronological history, which this still satisfies — revisit only if a cross-application "all transitions to DECLINED this week" style query becomes a real requirement.

**`FUNDING_FAILED`'s outgoing edge is deliberately absent from the transition table.** The blueprint's state diagram gestures at "operations retry/resolution" without specifying the retry target, and Milestone 4.2 (ops exception handling, manual retry/resolve) is where that mechanism actually gets built. Guessing a target now risks encoding the wrong graph; the table leaves `FUNDING_FAILED` terminal until that epic defines the real behavior.
