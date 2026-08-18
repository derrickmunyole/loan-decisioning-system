# 0010. Credit-score-outage retry logic lives in `decisioning`, not `workflow`

## Status

Accepted

## Context

Milestone 4, Epic 4.2 needed an ops-facing retry action for the one automated failure mode Epic 3.4 (ADR 0008) leaves unresolved without human intervention: a credit-score-provider outage, which produces `REFERRED` with **no** `Decision` row plus a `CREDIT_SCORE_PROVIDER_UNAVAILABLE` `workflow_task`. The roadmap's Epic 4.2 line ("manual retry/resolve actions") names the capability but not which module owns it, and the obvious first guess — it's a work-queue action, so it belongs next to `POST /work-queue/{id}/resolve` in `workflow` — turned out not to be buildable at all.

Retrying needs three things only `decisioning` can already legally reach: the persisted `UnderwritingSnapshot` (to republish `underwriting.snapshot.created` against it), `ApplicationTransitionService` (to drive `REFERRED → UNDERWRITING`), and the `workflow_task` itself (to resolve it once retried). Checking actual `pom.xml` dependency declarations rather than assuming from URL/resource conventions settled it: `decisioning` already depends on `applicant-origination`, `workflow`, and `verification`; `workflow` does not depend on `decisioning`, and structurally can't — `decisioning` already depends on `workflow`, so the reverse edge would be a cycle.

## Decision

**`POST /cases/{id}/retry-decision` lives in `decisioning`**, alongside `POST /cases/{id}/decision` from Epic 4.1, not in `workflow` next to `/work-queue/{id}/resolve` despite the action being "resolve a work-queue task" in spirit. A new `workflow.api.WorkflowTaskResolutionService` port (read `findOpenTask` + write `markResolved`) is what lets `decisioning` find and resolve the task without reaching into `workflow.workqueue` internals — the same "extract a port on the second real caller" shape as every prior cross-module port in this codebase (ADR 0007's `ApplicationTransitionService`, Epic 3.4's `WorkflowTaskCreationService`).

Two other shapes were considered and rejected: moving `underwriting.snapshot.created` republishing and `Decision`-adjacent knowledge into `workflow` (would invert the module's actual dependency purpose — `workflow` is the generic task-queue primitive, not a decisioning-aware orchestrator); or a new shared module both `workflow` and `decisioning` depend on (disproportionate to a single ~30-line retry method, and the roadmap doesn't call for a general orchestration layer anywhere else).

## Consequences

The URL groups the two `UNDERWRITER`/`OPERATIONS_ANALYST` case-recovery actions together (`/cases/{id}/decision`, `/cases/{id}/retry-decision`) even though `resolve`'s sibling action for every other task type lives under `/work-queue/{id}/resolve` in a different module — a resource naming scheme that doesn't map 1:1 onto module boundaries. That's accepted as the honest cost of the dependency direction being real: a caller-facing URL can live wherever is convenient, but the code behind it can only live where the compile-time dependency graph allows. Future ops actions needing both `workflow_task` and decisioning-owned state should check this same direction before assuming symmetry with `/work-queue/{id}/resolve`.
