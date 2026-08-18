# Loan Decisioning and Origination Platform — Technical Blueprint

## 1. Product boundary

Build a portfolio-grade, synthetic-data platform for **unsecured consumer installment loans**.

| Assumption | Initial value |
|---|---|
| Loan amount | KES 50,000–1,250,000 |
| Term | 12, 24, 36, or 48 months |
| Rate | Fixed APR, supplied by a versioned pricing policy |
| Decision outcomes | approved, declined, referred, conditional approval |
| Funding | One-time simulated disbursement through a mock payment provider |
| Repayments | Schedule generation only; no payment collection or delinquency servicing |
| Data | Fully synthetic; no real applicants or credit decisions |

The system is an **origination and decisioning** platform, not a production lender. It must never present its decisions as suitable for real lending.

## 2. Primary actors and permissions

| Actor | Permissions |
|---|---|
| Applicant | Create/edit draft application, submit, upload documents, view status/offer, accept offer |
| Underwriter | Review referred cases, request information, approve/decline/override with a mandatory reason |
| Operations analyst | Triage verification and disbursement exceptions; cannot change a credit decision |
| Policy administrator | Publish new policy and pricing versions; cannot alter historical decisions |
| Auditor | Read-only access to cases, decision evidence, and audit log |
| System service | Run verification, scoring, decisioning, notification, and payment workflows |

## 3. Functional requirements

### Application lifecycle

1. An applicant creates a draft and may update it while in `DRAFT`.
2. Submission validates required fields, captures consent, creates an immutable application version, and starts processing.
3. The platform obtains mock identity and income verification results asynchronously.
4. It builds an immutable underwriting snapshot from the submitted version and verified evidence.
5. A versioned policy and scorecard produce a decision: `APPROVED`, `DECLINED`, `REFERRED`, or `CONDITIONAL_APPROVAL`.
6. A referred case enters an underwriter queue. Overrides require a reason and create a new decision record; they never overwrite the automated decision.
7. An approved/conditional case receives a versioned offer with a finite expiration. Acceptance triggers funding.
8. A mock disbursement provider replies asynchronously. Success activates the loan and generates a repayment schedule; failure opens an operations exception.

### Decision integrity

- Every decision must retain the exact application version, underwriting snapshot, policy version, scorecard version, pricing version, input values, result, and reason codes.
- Policies are declarative and immutable after publication; a new change creates a new version.
- The first scorecard is deterministic and explainable (weighted bands), not ML.
- The system must distinguish a verified fact from an applicant-declared fact.

### Workflow and operations

- State transitions are explicit, authorized, and recorded.
- Long-running work runs asynchronously with retries, backoff, a dead-letter queue, and a manual-resolution path.
- Third-party failures must not silently produce an approval/decline; they result in `REFERRED` (routed to an underwriter, same as any other case needing human judgment) or a recoverable exception.
- Notifications are sent through an outbox so an application update cannot be committed without an eventual notification record.

## 4. State model

```text
DRAFT
  → SUBMITTED
  → VERIFYING
  → UNDERWRITING
  → APPROVED | DECLINED | REFERRED | CONDITIONAL_APPROVAL

REFERRED
  → UNDERWRITING                 (additional evidence)
  → APPROVED | DECLINED          (underwriter decision)

APPROVED | CONDITIONAL_APPROVAL
  → OFFERED
  → ACCEPTED
  → FUNDING_PENDING
  → FUNDED → ACTIVE
  → FUNDING_FAILED               (operations retry/resolution)

OFFERED → OFFER_EXPIRED | WITHDRAWN
```

The workflow engine must reject invalid transitions, such as funding a declined application or accepting an expired offer.

## 5. Data model

### Core transactional entities

| Entity | Key fields | Notes |
|---|---|---|
| `applicant` | `id`, PII reference, contact fields, created time | PII should be separated from lending data in a real deployment |
| `application` | `id`, `applicant_id`, current state, current version, idempotency key | Mutable aggregate pointing to immutable records |
| `application_version` | `id`, `application_id`, version number, requested amount/term, declared data, submitted time | Immutable after submission |
| `consent` | `id`, application version, consent type/version, timestamp | Captured before verification |
| `document` | `id`, application/version, storage key, type, status, checksum | Never place document bytes in the relational database |
| `verification_case` | `id`, application/version, provider, type, status, request/response reference | Identity, income, and bank-account verification |
| `underwriting_snapshot` | `id`, application version, normalized facts JSON, evidence references, created time | Immutable input to every decision |
| `policy_version` | `id`, status, effective date, rules JSON, checksum | Draft → published; published rows immutable |
| `scorecard_version` | `id`, status, formula/config, checksum | Draft → published; deterministic and explainable |
| `pricing_version` | `id`, status, APR/term rules, checksum | Draft → published; separate risk eligibility from pricing |
| `decision` | `id`, snapshot/policy/scorecard/pricing IDs, outcome, reason codes, actor, decided time | Append-only; includes automated and manual decisions |
| `offer` | `id`, decision ID, principal, APR, payment, term, expiry, status | Immutable commercial terms |
| `funding_instruction` | `id`, offer ID, provider idempotency key, amount, destination token, status | One instruction per accepted offer |
| `disbursement` | `id`, instruction ID, provider reference, status, timestamps | Created/updated from provider callback events |
| `loan_account` | `id`, application ID, funded principal, APR, term, activated time | Created only after successful funding |
| `repayment_schedule_item` | `id`, loan account ID, due date, principal, interest, status | Generated schedule only in v1 |

### Cross-cutting entities

| Entity | Purpose |
|---|---|
| `workflow_task` | Human work queue: review document, underwrite case, resolve funding failure |
| `audit_event` | Append-only actor/action/target/before-after metadata and correlation ID |
| `outbox_event` | Transactional record for events awaiting publication |
| `inbox_message` | Deduplication record for incoming provider callbacks and consumed events |
| `notification` | Message intent, delivery status, template/version, correlation ID |

### Relationships

```text
applicant 1 ── * application 1 ── * application_version
application_version 1 ── * verification_case / document / consent
application_version 1 ── 1 underwriting_snapshot
underwriting_snapshot 1 ── * decision 1 ── 0..1 offer
offer 1 ── 0..1 funding_instruction 1 ── * disbursement
application 1 ── 0..1 loan_account 1 ── * repayment_schedule_item
```

## 6. API boundary

| Endpoint | Purpose |
|---|---|
| `POST /applications` | Create draft; accepts `Idempotency-Key` |
| `PATCH /applications/{id}` | Update a draft only |
| `POST /applications/{id}/submit` | Validate, snapshot version, start workflow |
| `POST /applications/{id}/documents` | Create secure upload intent / document metadata |
| `GET /applications/{id}` | Applicant-safe state and status projection |
| `GET /applications/{id}/timeline` | Authorized decision and workflow history |
| `POST /offers/{id}/accept` | Accept unexpired offer idempotently |
| `GET /work-queue` | Underwriter/operations task queue, role-scoped to each role's own subset |
| `POST /work-queue/{id}/resolve` | Operations acknowledges/closes a task; requires resolution note |
| `POST /cases/{id}/decision` | Underwriter decision or override; requires reason code |
| `POST /cases/{id}/retry-decision` | Operations retries a case referred by a credit-score provider outage |
| `POST /policies` and `POST /policies/{id}/publish` | Create and publish an immutable policy version |
| `POST /scorecards` and `POST /scorecards/{id}/publish` | Create and publish an immutable scorecard version |
| `POST /pricing` and `POST /pricing/{id}/publish` | Create and publish an immutable pricing version |
| `POST /webhooks/mock-payments` | Receive signed, deduplicated provider status callbacks |

## 7. Events and asynchronous work

Use a transactional outbox from the primary database. Consumers must be idempotent by `event_id`.

```text
application.submitted
verification.requested
verification.completed | verification.failed
underwriting.snapshot.created
decision.created
case.referred
offer.created | offer.accepted | offer.expired
funding.requested
disbursement.succeeded | disbursement.failed
loan.activated
notification.requested
```

For the mock payment provider, `funding.requested` creates an instruction with a provider idempotency key. The provider emits one signed webhook; duplicate delivery is expected and safely ignored through `inbox_message`.

## 8. Architecture map

```text
[Applicant Web App]                 [Underwriter Console]
          │                                    │
          └───────────────┬────────────────────┘
                          ▼
                    [API / Auth]
                          │
        ┌─────────────────┼───────────────────┐
        ▼                 ▼                   ▼
[Application service] [Workflow service] [Policy admin service]
        │                 │                   │
        └───────┬─────────┴──────────┬────────┘
                ▼                    ▼
       [PostgreSQL + outbox]    [Object storage]
                │
                ▼
             [Event broker]
      ┌─────────┼───────────┬──────────────┐
      ▼         ▼           ▼              ▼
[Verification] [Decision] [Notifications] [Funding adapter]
  adapters      engine          worker           │
                                                 ▼
                                      [Mock payment provider]
                                                 │ webhook
                                                 └────────────→ API / inbox
```

Start as a modular monolith: one deployable application with clear modules, PostgreSQL, object storage, a worker process, and a queue/broker. Extract the decision engine and provider adapters only after their contracts stabilize. This preserves realism without premature distributed-systems overhead.

## 9. Non-functional requirements and acceptance checks

| Area | Initial target |
|---|---|
| Correctness | Every decision is reproducible from stored immutable inputs and version IDs |
| Idempotency | Repeating submit, offer acceptance, or callback does not create a second decision, offer, or funding instruction |
| Authorization | Applicant, underwriter, operations, policy admin, and auditor permissions are enforced server-side |
| Auditability | All privileged actions and state transitions appear in append-only audit events |
| Resilience | Provider timeout/error retries; exhausted failures create an operational task |
| Privacy | Synthetic data only; redacted logs; secrets externalized; documents in private object storage |
| Observability | Correlation IDs, structured logs, metrics for queue age/provider error/decision latency, and traces across async work |
| Testing | Unit policy tests, state-transition tests, contract tests for provider adapters, integration tests for outbox and dedupe, end-to-end happy/failure paths |

## 10. Delivery sequence

1. Foundation: authentication/roles, database migrations, audit event, outbox, application draft/submission.
2. Workflow: state machine, task queue, synthetic verification adapters, documents metadata.
3. Decisioning: underwriting snapshot, policy/scorecard/pricing versions, automated decision and reasons.
4. Operations: underwriter console actions, override controls, timeline and evidence view.
5. Lending completion: offer, acceptance, mock disbursement, callback dedupe, loan account and repayment schedule.
6. Hardening: observability, fault injection, load/authorization tests, architecture decision records, and a demo scenario.

## 11. Definition of a compelling demo

Show three cases:

1. **Auto-approved**: verified income supports affordability; policy and scorecard approve; the offer is accepted; a duplicate acceptance and duplicate payment webhook are safely ignored.
2. **Referred and overridden**: a verification mismatch triggers manual review; an underwriter requests evidence and issues an override with a recorded rationale.
3. **Funding failure**: payment provider returns a recoverable error; the workflow creates an operations task; a retry resolves it without double disbursement.

Each screen or API response should link to the decision version, reasons, evidence references, event timeline, and audit history.
