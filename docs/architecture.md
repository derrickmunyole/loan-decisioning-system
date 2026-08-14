# Architecture diagrams

Living diagrams of the system as actually built, rendered with [Mermaid](https://mermaid.js.org/) so they display natively on GitHub with no build step. These are a companion to `docs/blueprint.md` (the *what/why*) and `docs/roadmap.md` (the *how/when*) — where the prose in those documents and a diagram here disagree, the diagram should be corrected to match the shipped code, not the other way around.

**Update these in the same PR as the code change that invalidates them**, the same discipline as CLAUDE.md's project-state paragraph and the README's status section. Each diagram below names its source of truth in the codebase so "does this diagram still match reality" is a mechanical check, not a judgment call.

This file will grow one diagram per architecturally-significant epic. A polished HTML/CSS rendering is planned as a post-completion portfolio pass — see the epics below for the Mermaid originals that pass will be built from.

## Application lifecycle (state machine)

Source of truth: `workflow/.../workflow/api/ApplicationStatus.java` (the 16 states) and `WorkflowTransitionService.buildTransitionTable()` (the legal edges). Mirrors `docs/blueprint.md` §4.

```mermaid
stateDiagram-v2
    [*] --> DRAFT

    DRAFT --> SUBMITTED
    SUBMITTED --> VERIFYING
    VERIFYING --> UNDERWRITING

    UNDERWRITING --> APPROVED
    UNDERWRITING --> DECLINED
    UNDERWRITING --> REFERRED
    UNDERWRITING --> CONDITIONAL_APPROVAL

    REFERRED --> UNDERWRITING : additional evidence
    REFERRED --> APPROVED : underwriter decision
    REFERRED --> DECLINED : underwriter decision

    APPROVED --> OFFERED
    CONDITIONAL_APPROVAL --> OFFERED

    OFFERED --> ACCEPTED
    OFFERED --> OFFER_EXPIRED
    OFFERED --> WITHDRAWN

    ACCEPTED --> FUNDING_PENDING
    FUNDING_PENDING --> FUNDED
    FUNDING_PENDING --> FUNDING_FAILED

    FUNDED --> ACTIVE

    DECLINED --> [*]
    OFFER_EXPIRED --> [*]
    WITHDRAWN --> [*]
    ACTIVE --> [*]

    note right of FUNDING_FAILED
        No outgoing edge yet — retry/resolution
        is Milestone 4.2 (ops exception handling),
        not guessed at here.
    end note
```

`WorkflowTransitionService` validates every `from → to` edge shown above and throws `IllegalApplicationTransitionException` (409) on anything not drawn — e.g. funding a declined application or accepting an expired offer. The service is pure validation only; it doesn't persist, audit, or publish. The caller (`ApplicationCommandService` for the `DRAFT → SUBMITTED` hop, `verification`'s `ApplicationSubmittedHandler` for `SUBMITTED → VERIFYING → UNDERWRITING`) owns the entity mutation, `@Audited` call, and outbox enqueue, all inside its own transaction — see ADR 0007.

As of Epic 3.4, live traffic reaches `APPROVED`, `DECLINED`, `REFERRED`, and `CONDITIONAL_APPROVAL` — the automated decision engine drives all four edges out of `UNDERWRITING`. Everything from `OFFERED` onward is still drawn from the blueprint's state model but not yet exercised by running code (Milestone 4+). The `REFERRED → UNDERWRITING`/`REFERRED → APPROVED`/`REFERRED → DECLINED` edges (underwriter override) are also not yet exercised — that's Milestone 4's `4.1`.

## Module dependency graph

Source of truth: each module's `pom.xml` (`<dependencies>`) for the edges, `ModuleBoundaryTest` for which modules are ArchUnit-enforced. An arrow means "depends on" — it points from the dependent module to the module it compiles against.

```mermaid
flowchart TD
    subgraph sharedKernel["Shared kernel &amp; framework-wired (exempt from ArchUnit as targets)"]
        common(["platform-common"])
        security(["platform-security"])
    end

    subgraph boundedContexts["Bounded contexts (internals reachable only via xxx.api)"]
        infrastructure["platform-infrastructure"]
        workflow["workflow"]
        origination["applicant-origination"]
        verification["verification"]
        decisioning["decisioning"]
    end

    app{{"platform-app (deployable)"}}

    infrastructure --> common
    security --> common

    workflow --> infrastructure

    origination --> common
    origination --> infrastructure
    origination --> workflow

    verification --> infrastructure
    verification --> origination
    verification --> workflow

    decisioning --> infrastructure
    decisioning --> origination
    decisioning --> verification
    decisioning --> workflow

    app --> common
    app --> security
    app --> infrastructure
    app --> origination
    app --> workflow
    app --> verification
    app --> decisioning
```

`platform-app` is the only executable jar — one deployable hosting both REST controllers and `@RabbitListener`s, per the roadmap's modular-monolith decision. It's also the only module allowed to depend on all seven others; none of the bounded-context modules depend on each other's siblings except through the edges drawn above (e.g. `verification` reaches `applicant-origination` only through `origination.api`'s `ApplicationVersionQueryService`/`ApplicationTransitionService` ports, and `decisioning` reaches `applicant-origination` and `verification` the same way — the "extract a port on the second real caller" pattern ADR 0007 documents for `workflow`). Epic 3.4 gave `workflow` a second external caller of its own: `decisioning` needs to raise a `workflow_task` when the credit-score provider is unreachable, so `WorkflowTaskType` moved from `workflow.workqueue` (internal) to `workflow.api` alongside the new `WorkflowTaskCreationService` port — the same pattern applied one module over.

`platform-common` and `platform-security` are exempt as ArchUnit *targets* (nothing stops another module from importing their public classes directly — `common` is a shared kernel of value types/base entities/exceptions meant to be used everywhere, and `security` is wired by the Spring framework itself via filter chain/annotations rather than imported directly). They're still bound as *callers*: `ModuleBoundaryTest` would fail if either reached into, say, `infrastructure`'s or `origination`'s internals.

Not yet present: `offers`, `funding`, `notifications` — later Milestone 3+ epics, per the roadmap's module list.

## Submit → verify → underwriting (async golden path)

Source of truth: `ApplicationCommandService.submit`, `OutboxRelay.relay`, `ApplicationSubmittedListener`/`Handler`, `WorkflowTransitionService`. This is the one flow that currently exercises all three of the project's structural pillars at once — the transactional outbox, the shared state-machine validation port, and the AMQP `consumed_event` dedupe mechanism — so it's the clearest single diagram of the async backbone actually working end to end.

```mermaid
sequenceDiagram
    autonumber
    actor Client as CLI (synthetic-data-generator)
    participant Origination as ApplicationCommandService (origination)
    participant Workflow as WorkflowTransitionService (workflow)
    participant DB as Postgres
    participant Relay as OutboxRelay (infrastructure, scheduled)
    participant Broker as RabbitMQ (loan.events exchange)
    participant Handler as ApplicationSubmittedHandler (verification)

    Note over Client,DB: Synchronous HTTP request, one transaction
    Client->>Origination: POST /applications/{id}/submit
    Origination->>Workflow: validateTransition(DRAFT, SUBMITTED)
    Workflow-->>Origination: legal
    Origination->>DB: save ApplicationVersion + Consent, attach Documents,<br/>status=SUBMITTED, insert outbox_event(application.submitted)
    Origination-->>Client: 200 { status: SUBMITTED }

    Note over Relay,Broker: Async, scheduled relay with publisher confirms
    Relay->>DB: lockNextPendingBatch() (every ~2s)
    Relay->>Broker: convertAndSend(loan.events, application.submitted)
    Broker-->>Relay: confirm ack
    Relay->>DB: mark outbox_event PUBLISHED (only inside the confirm callback)

    Note over Broker,Handler: Async, AMQP consumer, its own transaction
    Broker->>Handler: deliver to verification.application-submitted.queue
    Handler->>DB: alreadyConsumed(consumer, eventId)? (consumed_event dedupe)
    Handler->>Origination: transitionTo(applicationId, VERIFYING)
    Origination->>Workflow: validateTransition(SUBMITTED, VERIFYING)
    Workflow-->>Origination: legal
    Origination->>DB: status=VERIFYING
    Handler->>Origination: findByApplicationIdAndVersionNumber(applicationId, 1)
    Origination-->>Handler: ApplicationVersionView

    alt declaredEmployerName is the transient-failure trigger, attempt < 2
        Handler->>DB: recordAttemptAndGetCount(applicationId) [REQUIRES_NEW]
        Handler--xBroker: throws SimulatedTransientVerificationFailureException<br/>(transaction rolls back, container nacks, retry interceptor redelivers)
    end

    Handler->>Handler: checkIdentity() + checkIncome() (SyntheticVerificationEngine, deterministic)
    Handler->>DB: save VerificationCase(IDENTITY), VerificationCase(INCOME)
    Handler->>Origination: transitionTo(applicationId, UNDERWRITING)
    Origination->>Workflow: validateTransition(VERIFYING, UNDERWRITING)
    Workflow-->>Origination: legal
    Origination->>DB: status=UNDERWRITING
    Handler->>DB: insert outbox_event(underwriting.requested)
    Handler->>DB: markConsumed(consumer, eventId)
    Handler-->>Broker: ack (AcknowledgeMode.AUTO, after process() returns)
```

Two details that otherwise require reading three separate files to piece together:

- **Steps 11–27 are one `@Transactional` method** (`ApplicationSubmittedHandler.process`). If the transient-failure branch throws, everything in that range rolls back — including the `VERIFYING` transition and the `underwriting.requested` outbox insert — so a redelivered attempt safely re-validates `SUBMITTED → VERIFYING` instead of finding the aggregate already past it. The one exception is the attempt counter itself: `recordAttemptAndGetCount` runs in a separate `REQUIRES_NEW` transaction specifically so the count survives the rollback its own trigger causes.
- **The listener/handler split is deliberate**, not incidental: `@RabbitListener` (not shown as a separate lifeline above — it's a thin wrapper around `Handler`) only acks after `process()` returns, so the message can't be acked before its transaction actually commits. See ADR 0004 for why the opposite ordering was a real bug here in Epic 2.2.

This is also the boundary the state diagram above flags: `Handler` never drives status past `UNDERWRITING`. The `decisioning` module picks up from step 26 onward, in its own async chain diagrammed next.

## Underwriting → automated decision (async golden path, continued)

Source of truth: `UnderwritingRequestedHandler` (Epic 3.1), `DecisionEngineHandler`/`CreditScoreClient` (Epic 3.4), `PolicyEvaluator`. Picks up exactly where the diagram above leaves off — `underwriting.requested` was enqueued as step 26's outbox insert. Two consumers, two transactions, deliberately kept apart: the credit-score HTTP call (ADR 0008) must never run while a DB transaction is held open, so the snapshot build (3.1) and the decision itself (3.4) are split across a second outbox hop (`underwriting.snapshot.created`) rather than one handler doing both.

```mermaid
sequenceDiagram
    autonumber
    participant Broker as RabbitMQ (loan.events exchange)
    participant Snapshot as UnderwritingRequestedHandler (decisioning)
    participant DB as Postgres
    participant Relay as OutboxRelay (infrastructure, scheduled)
    participant Decision as DecisionEngineHandler (decisioning)
    participant Score as credit-score-service (FastAPI, HTTP)
    participant Origination as ApplicationTransitionService (origination)
    participant Workflow as WorkflowTransitionService (workflow)

    Note over Broker,DB: Async, AMQP consumer, its own transaction
    Broker->>Snapshot: deliver underwriting.requested
    Snapshot->>DB: alreadyConsumed(consumer, eventId)? (consumed_event dedupe)
    Snapshot->>DB: save UnderwritingSnapshot (facts JSON, insert-only)
    Snapshot->>DB: insert outbox_event(underwriting.snapshot.created)
    Snapshot->>DB: markConsumed(consumer, eventId)
    Snapshot-->>Broker: ack (after process() returns)

    Note over Relay,Broker: Async, scheduled relay with publisher confirms
    Relay->>DB: lockNextPendingBatch() (every ~2s)
    Relay->>Broker: convertAndSend(loan.events, underwriting.snapshot.created)
    Broker-->>Relay: confirm ack
    Relay->>DB: mark outbox_event PUBLISHED

    Note over Broker,Decision: Async, AMQP consumer, own transaction — no HTTP call inside it yet
    Broker->>Decision: deliver decisioning.underwriting-snapshot-created.queue
    Decision->>DB: alreadyConsumed(consumer, eventId)?
    Decision->>DB: load UnderwritingSnapshot + published Policy/Scorecard/PricingVersion

    alt verification evidence contains a FAILED check
        Decision->>DB: save Decision(REFERRED), no credit-score call
    else evidence clean
        Note over Decision,Score: Still inside the @Transactional method, but the call itself<br/>is synchronous WebClient + Resilience4j — deliberately outside any DB write
        Decision->>Score: POST /score (@CircuitBreaker)
        alt provider reachable
            Score-->>Decision: score, modelVersion, reasonContributions
            Decision->>Decision: PolicyEvaluator.evaluate(score, bandCutoffs, bandOutcomes)
            Decision->>DB: save Decision(outcome, full version traceability)
        else timeout / circuit open / connection refused
            Decision->>Origination: transitionTo(applicationId, REFERRED)
            Decision->>DB: create workflow_task(CREDIT_SCORE_PROVIDER_UNAVAILABLE)
            Note over Decision,DB: No Decision row — no valid score/model version to record
        end
    end

    opt a Decision row was recorded
        Decision->>Origination: transitionTo(applicationId, outcome)
        Origination->>Workflow: validateTransition(UNDERWRITING, outcome)
        Workflow-->>Origination: legal
        Origination->>DB: status=outcome
    end

    Decision->>DB: markConsumed(consumer, eventId)
    Decision-->>Broker: ack (after process() returns)
```

Three things worth calling out explicitly:

- **Why a second outbox hop instead of one handler doing both jobs**: `UnderwritingRequestedHandler`'s transaction only ever touches Postgres — cheap to hold open. `DecisionEngineHandler`'s does not: it makes a real outbound HTTP call to `credit-score-service`. Holding a DB transaction open across that call would tie up a connection-pool slot for however long the provider takes (or times out), so the two are split across a real event rather than one handler calling the other directly. See ADR 0008.
- **The `@CircuitBreaker` wraps a blocking call, not a reactive one** — `CreditScoreClient` uses `WebClient` but calls `.block(timeout)` immediately, since this call is synchronous by roadmap design. Resilience4j's `TimeLimiter` (built for reactive/async return types) doesn't apply here; the timeout is enforced by `.block(Duration)` itself.
- **No `Decision` row on the outage path is deliberate, not an oversight** — recording one would mean inventing a placeholder `credit_score_model_version`, which breaks the "a `Decision` always carries the exact version IDs it was computed from" invariant. The `workflow_task` is what makes the outage ops-visible instead.