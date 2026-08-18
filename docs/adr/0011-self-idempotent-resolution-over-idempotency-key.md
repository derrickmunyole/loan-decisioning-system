# 0011. `WorkflowTask` resolution is self-idempotent, not routed through the `Idempotency-Key` mechanism

## Status

Accepted

## Context

Milestone 4, Epic 4.2 added two endpoints that flip an existing `workflow_task` to `RESOLVED`: `POST /work-queue/{id}/resolve` and (via `CaseDecisionCommandService`'s retrofit) the task-resolution side effect of `POST /cases/{id}/decision` and `POST /cases/{id}/retry-decision`. ADR 0005 already established this codebase's generic answer to "duplicate request must not double-apply an effect" — the `Idempotency-Key` HTTP header mechanism, required on `POST /applications/{id}/submit` and `POST /cases/{id}/decision`'s Epic 4.1 mutation. The obvious-looking choice was to require `Idempotency-Key` here too, for consistency.

## Decision

**`WorkflowTask.markResolved(resolution, resolvedBy)` is self-idempotent instead**: if the task is already `RESOLVED`, the call is a no-op that doesn't overwrite who resolved it first or when. No `Idempotency-Key` header, no `idempotency_key` table row, on any of the three call sites that resolve a task.

The two mechanisms solve different-shaped problems. `Idempotency-Key` (ADR 0005) exists for operations that **create** something or otherwise have no natural "already done" state to check — a duplicate `POST /applications` without it would create two applications, because there's nothing on the (non-existent) target to compare against. Resolving a task is different: the target row already exists, already carries a `status` column, and "resolve an already-resolved task" has an obvious, cheap, correct answer (leave it exactly as the first resolution left it) without needing a client-supplied key, a reservation row, or a stored response to replay. Requiring `Idempotency-Key` here would mean solving an already-solved problem a second, heavier way.

## Consequences

A duplicate `resolve`/`decide`/`retry-decision` call (client retry, at-least-once redelivery of whatever triggered it, or a genuine double-click) is safe with zero extra client-side bookkeeping — no key to generate, no header to remember. The tradeoff: this pattern only works because the mutation is a simple status flip on a row that already exists and already remembers its own terminal state. It doesn't generalize to an operation that creates a new row or has no natural resting state to check against — those still belong on `Idempotency-Key`, per ADR 0005. Future endpoints should pick between the two by asking "does the target already carry the state I'd otherwise need a key to protect," not by copying whichever precedent is nearest in the code.
