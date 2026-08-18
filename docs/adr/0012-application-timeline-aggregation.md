# 0012. Application timeline aggregation: module placement and role-dispatched response shape

## Status

Accepted

## Context

Milestone 4, Epic 4.3 needed `GET /applications/{id}/timeline` to satisfy blueprint §11: "Each screen or API response should link to the decision version, reasons, evidence references, event timeline, and audit history." The roadmap's Epic 4.3 line ("timeline flesh-out for full chronology + role-scoped visibility across all 5 human roles") names the goal but not the mechanism. Two real decisions had to be made during implementation.

**Where the endpoint lives.** The pre-4.3 endpoint lived in `applicant-origination`, gated to `APPLICANT` ownership only, returning audit events alone. The aggregate blueprint §11 calls for needs `Decision` (owned by `decisioning`) and verification evidence (`verification.api.VerificationEvidenceQueryService`). Both `verification` and `decisioning` already depend on `origination` — so `origination` depending back on either to build the aggregate would be a compile-time cycle. This is the identical shape ADR 0010 already resolved for `POST /cases/{id}/retry-decision`: `decisioning` is the only module that legally depends on all three (`origination`, `workflow`, `verification`) at once.

**How role-scoped visibility is enforced.** Blueprint §2 grants all five human roles access to application state in some form, but only the applicant owns a specific application. A single response shape for every caller would mean either over-exposing decision/evidence internals to the applicant (breaking the established "applicant-safe projection" precedent already used for `ApplicationResponse`) or under-serving staff with the applicant's narrow view.

## Decision

**The endpoint moved to `decisioning`, same URL.** `ApplicationTimelineController`/`ApplicationTimelineService` now live in `decisioning`, replacing the removed `applicant-origination` implementation entirely (no dead code path left behind). Two new read ports back this: `origination.api.ApplicationOwnershipService` (the ownership check `ApplicationAccessGuard` already provided, now reachable from outside `origination`) and `decisioning.api.DecisionQueryService`/`DecisionView` (the first external read port off `Decision`, parsing `reason_codes_json` into a plain `List<String>` at the port boundary so callers never need their own `ObjectMapper`).

**One URL, two response shapes, dispatched by role.** `ApplicationTimelineService.getTimeline` checks the caller's authorities: the four staff roles (`UNDERWRITER`, `OPERATIONS_ANALYST`, `POLICY_ADMIN`, `AUDITOR`) get an `ApplicationTimelineResponse` aggregate — `decisions` (outcome, reasons, and all four version IDs a `Decision` carries), `evidence` (verification check results), and `events` (the audit feed) as three distinct sections, not one merged sorted list, since blueprint §11's sentence names four separate things and each section draws from a different module's data. The owning `APPLICANT` gets the exact pre-4.3 response unchanged — a plain `List<TimelineEvent>` of audit events only. The ownership check moved from a declarative `@PreAuthorize` SpEL expression (which required the guard bean and the controller to live in the same module, or a cross-module SpEL reference ArchUnit's bytecode analysis can't see — the same class of gap `RabbitQueueNames` exists to avoid for inlined constants) to an imperative call inside the service.

`SecurityConfig` gains `.requestMatchers("/applications/*/timeline").hasAnyRole(...)` for all five human roles, ordered ahead of the broader `/applications/** -> APPLICANT` rule — the same first-match-wins pattern already used for `/work-queue/*/resolve` ahead of `/cases/**`. This is `AUDITOR`'s first endpoint access anywhere in this codebase.

## Consequences

A single `GET` genuinely satisfies blueprint §11 for every staff role without a second endpoint or a query parameter switch — the URL is the same one the applicant already knew, and Swagger/a REST client show the shape as `Object`, resolved by role at request time. The cost: the response schema isn't statically fixed the way most of this codebase's endpoints are (every other endpoint returns one concrete DTO type regardless of caller) — a caller has to know their own role to know which shape to expect, which is fine for this codebase's Swagger-driven demo usage but would need an OpenAPI `oneOf` or a documented split if this were a public API.

Reusing `TimelineEvent` for both the applicant's whole response and the aggregate's `events` section (rather than two separate types) keeps the two shapes visibly related — same events, different amount of company around them — at the cost of the applicant-facing type technically living in `decisioning` now, one hop further from `applicant-origination`'s other applicant-facing DTOs than before.
