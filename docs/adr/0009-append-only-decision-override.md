# 0009. Decision overrides are new append-only rows, not a mutation or a parallel table

## Status

Accepted

## Context

Milestone 4, Epic 4.1 needed `POST /cases/{id}/decision` to let an underwriter override a `REFERRED` case to `APPROVED`/`DECLINED`. The roadmap's own done-criterion (Milestone 4 table) already settles the shape at a high level — "creates a **new** decision record referencing the automated one" — but not the concrete mechanism, and `Decision` is one of this codebase's append-only entities (see the architecture summary's "everything decision-related is immutable/append-only" rule): no update path exists, and none should be added just for this.

Three shapes were available: (1) mutate the existing automated `Decision` row's outcome in place; (2) a separate `DecisionOverride` table referencing the original `Decision`; (3) a second, ordinary `Decision` row that references the one it supersedes.

## Decision

**A second `Decision` row, linked via a new nullable `overrides_decision_id` column** (self-referencing FK on `decision`) pointing at the automated decision it supersedes. `CaseDecisionCommandService.decide` saves this new row exactly the same way `DecisionEngineHandler` saves an automated one — same table, same insert-only repository, no update statement anywhere in the path.

Rejected (1) outright: mutating `Decision` breaks the append-only invariant every other decisioning entity in this codebase already holds, and would make `Decision.decidedAt`/`reasons` ambiguous — is it describing the automated run or the human correction? Rejected (2): a parallel `DecisionOverride` table means every future reader of "what did we ultimately decide for this application" has to know to check two tables and merge them, and duplicates most of `Decision`'s own columns (outcome, reasons, decided-by, decided-at) to describe a structurally identical fact. The self-referencing FK keeps `Decision` the single source of truth — `findFirstByApplicationIdOrderByDecidedAtDesc` already answers "what's the current decision" with no join, and `overrides_decision_id` is there for anyone who wants to walk back to what was superseded.

## Consequences

The original automated decision stays queryable, unedited, forever — exactly the roadmap's stated done-criterion ("original automated decision stays queryable alongside the override, both in the audit trail"). Querying "the current decision" is a single `ORDER BY decided_at DESC LIMIT 1`, not a two-table merge. The cost: `Decision` now has a column (`overrides_decision_id`) that's `null` on the large majority of rows (every automated decision, and every override-to-`UNDERWRITING` case, which records no `Decision` row at all — see `CaseDecisionCommandService`) and populated only on the minority that are human overrides — an acceptable sparsity given the alternative was a second table carrying the same problem in a different shape.
