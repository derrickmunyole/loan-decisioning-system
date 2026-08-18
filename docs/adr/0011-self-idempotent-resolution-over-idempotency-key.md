# 0011. `WorkflowTask` resolution is self-idempotent; it doesn't need its own `Idempotency-Key`

## Status

Accepted

## Context

Milestone 4, Epic 4.2 added `POST /work-queue/{id}/resolve`, whose entire effect is flipping an existing `workflow_task` to `RESOLVED`. ADR 0005 already established this codebase's generic answer to "duplicate request must not double-apply an effect" — the `Idempotency-Key` HTTP header mechanism, required on `POST /applications/{id}/submit` and (Epic 4.1) `POST /cases/{id}/decision`. The obvious-looking choice was to require `Idempotency-Key` on `/resolve` too, for consistency with those two.

Epic 4.2 also retrofitted `CaseDecisionCommandService.decide` and its own new `RetryDecisionCommandService.retry` to resolve a case's `UNDERWRITE_CASE`/`CREDIT_SCORE_PROVIDER_UNAVAILABLE` task as a side effect of their real work — but both of those endpoints already require `Idempotency-Key` for reasons unrelated to task resolution: `decide`'s primary effect is creating a new `Decision` row, `retry`'s is republishing `underwriting.snapshot.created` and transitioning `Application.status`, and neither of those effects has a natural "already done" state to check the way a status flip does. This ADR is about `/work-queue/{id}/resolve` specifically, the one endpoint where "how do we make a duplicate call safe" was a real, standalone question — not about the other two, where the question was already answered by ADR 0005 before task resolution ever entered the picture.

## Decision

**`WorkflowTask.markResolved(resolution, resolvedBy)` is self-idempotent**: if the task is already `RESOLVED`, the call is a no-op that doesn't overwrite who resolved it first or when. `POST /work-queue/{id}/resolve` requires no `Idempotency-Key` header and writes no `idempotency_key` table row — `markResolved`'s own check is the entire guard against a duplicate call.

The two mechanisms solve different-shaped problems. `Idempotency-Key` (ADR 0005) exists for operations that **create** something or otherwise have no natural "already done" state to check — a duplicate `POST /applications` without it would create two applications, because there's nothing on the (non-existent) target to compare against. Resolving a task is different: the target row already exists, already carries a `status` column, and "resolve an already-resolved task" has an obvious, cheap, correct answer (leave it exactly as the first resolution left it) without needing a client-supplied key, a reservation row, or a stored response to replay. Requiring `Idempotency-Key` on `/resolve` would mean solving an already-solved problem a second, heavier way.

`CaseDecisionCommandService.decide` and `RetryDecisionCommandService.retry` also call `markResolved` internally, but for a different, narrower reason: it makes their own task-resolution side effect safe to repeat *within* a transaction that `Idempotency-Key` already protects as a whole (a replayed `decide`/`retry` call re-runs the cached response path via `IdempotencyService`, so `markResolved`'s no-op branch is defense in depth, not the mechanism actually carrying those two endpoints' idempotency).

## Consequences

A duplicate `/work-queue/{id}/resolve` call (client retry, or a genuine double-click) is safe with zero extra client-side bookkeeping — no key to generate, no header to remember. The tradeoff: this pattern only works because the mutation is a simple status flip on a row that already exists and already remembers its own terminal state. It doesn't generalize to an operation that creates a new row or has no natural resting state to check against — those still belong on `Idempotency-Key`, per ADR 0005, exactly as `decide` and `retry` already demonstrate. Future endpoints should pick between the two by asking "does the target already carry the state I'd otherwise need a key to protect," not by copying whichever precedent is nearest in the code.
