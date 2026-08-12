# 0001. Maven multi-module reactor, grown incrementally

## Status

Accepted

## Context

The blueprint (§8) calls for a modular monolith: one deployable application with clear internal module boundaries, extracting services later only once contracts stabilize. The roadmap's target shape is ~7 Maven modules for Milestone 1, growing toward ~11-12 by Milestone 5 (`verification`, `decisioning`, `offers`, `funding`, `notifications` added as later milestones need them). Standing up all of those modules on day one would front-load POM boilerplate and IntelliJ reimport friction with no corresponding value this early — nothing depends on the empty modules yet.

## Decision

Start with the modules each epic actually needs, not the full target set: `platform-app` (the only module producing an executable jar — hosts REST controllers and, later, RabbitMQ listeners in one process), `platform-common` (shared value types, base entity types, exception hierarchy — currently a near-empty placeholder), and `platform-security` (added in Epic 1.2, once there was a real reason for it: roles, JWT, security config). Each module owns its own `db/migration/<module>/` Flyway location on the classpath, aggregated via `spring.flyway.locations` in `platform-app`. Cross-module access is meant to go only through an `xxx.api` package per module, never direct JPA entity/repository imports across boundaries — but as of this ADR that boundary is a convention only, **not yet enforced by ArchUnit** despite the roadmap's stated intent to enforce it from the start. That's a real gap, not a deliberate deferral; it should be closed before more than 2-3 modules exist and cross-module temptation grows.

## Consequences

Reactor stays easy to navigate and rebuild while there's little in it. The tradeoff is that module boundaries are currently honor-system only — nothing stops a future epic from reaching across modules the wrong way until ArchUnit tests exist. Follow-up: add an ArchUnit test module/suite before Milestone 2 introduces `workflow` and the module count starts climbing.

**Status update (Epic 1.6 ADR audit):** the reactor is now at 5 modules (`platform-common`, `platform-security`, `platform-infrastructure`, `applicant-origination`, `platform-app`) — past the "2-3 modules" threshold this ADR named, with ArchUnit still unaddressed. The commitment above ("before Milestone 2 introduces `workflow`") hasn't technically been missed yet since Milestone 2 hasn't started, but there's no slack left in it: this needs to land at the very start of Milestone 2, before `workflow` adds a sixth module and the boundary this ADR is meant to protect gets a second undefended crossing point.

**Status update (Milestone 2 prep, before Epic 2.1):** the ArchUnit gap is closed. `ModuleBoundaryTest` in `platform-app` (analyzing the full reactor, since `platform-app` is the only module with every other module on its classpath) enforces that a module's internals are reachable from outside only through its `xxx.api` package — one rule per bounded-context module, scoped to `infrastructure` and `origination` today, extending naturally to `workflow`/`verification`/`decisioning`/etc. as they're added.

Writing the rule surfaced a real, pre-existing violation: `applicant-origination` was reaching directly into `infrastructure.audit`/`infrastructure.idempotency` (5 classes: `Audited`, `IdempotencyService`, `RequestHash`, and two idempotency exceptions) instead of through `infrastructure.api`. These were relocated into `infrastructure.api` rather than grandfathered as an exception — they were always meant to be infrastructure's public surface (ADR 0005 already describes `IdempotencyService.execute(...)` as mirroring `OutboxEventPublisher.enqueue(...)`'s shape, and the latter was already in `.api`), so the fix was a clean move, not a compromise.

`platform-common` and `platform-security` are deliberately exempt as *targets* of the `.api`-only rule — not an oversight, a scope decision. `common` is a shared kernel (value types, base entity types, exception hierarchy) meant to be used everywhere; gating it behind an `.api` package would just be ceremony around code with no private internals to protect. `security` is wired by the Spring framework itself (filter chain, `@PreAuthorize`), not imported directly by other bounded contexts — nothing currently reaches into it, and if something someday did reach into its internals, that would be a different, framework-boundary question rather than the bounded-context-privacy problem this ADR's rule targets. Both remain bound as *callers*: the rule still fails if `common` or `security` code reaches into `infrastructure`'s or `origination`'s internals.