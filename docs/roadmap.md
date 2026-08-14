# Loan Decisioning Platform — Technical Requirements & Phased Roadmap

Companion document to `blueprint.md`, which defines the *what/why*. This document defines the *how/when*: the concrete technology stack and an epic/sprint/milestone delivery plan.

**Development model**: this is an AI-assisted build — Claude implements the code; the user reviews and directs. "Building Java/Spring Boot familiarity" is a goal for understanding and reviewing the system, not a constraint that work proceed at manual-typing pace. Sprint/epic/milestone structure, done-criteria, testing, and review discipline still follow real SDLC practice — that rigor does not relax. What does not apply is solo-part-time calendar sizing (weeks per sprint, months to ship). Each epic below is one **sprint**: a cohesive, independently reviewable, independently testable increment with an explicit done-criterion, delivered in one focused pass.

## Locked architecture decisions

| Decision | Choice | Why |
|---|---|---|
| Repo structure | Monorepo, polyglot | One repo: Java Maven reactor for the core app, sibling directories for the two Python services, Docker Compose ties them together. Simplest for a single-owner project. |
| Build tool | Maven | Java 21, palantir-java-format already configured in IntelliJ. Broadest alignment with mainstream Spring Boot documentation/examples. |
| Async backbone | RabbitMQ from day one | Satisfies the blueprint's retry/backoff/DLQ/manual-resolution requirement with native broker mechanics rather than a hand-rolled DB-polling-only outbox. |
| Python scope | Credit-score model = synchronous FastAPI microservice. Synthetic data generator = offline CLI. | Keeps Python deliberately narrow: one real "external provider" service the Java decision engine calls, one dev/demo tool. Everything else stays in Java. |

---

## Part A — Technical Requirements

### A.1 Maven module layout

Start at **~7 modules**, not a large upfront split — a wide reactor from day one adds POM boilerplate disproportionate to the value, and premature boundaries are guesses. Split further only when a module's mixed concerns cause real friction.

| Module | Contents |
|---|---|
| `platform-app` | Executable jar. `@SpringBootApplication`, `application.yml` + profiles, security filter chain assembly, RabbitMQ config, aggregated Flyway locations. Hosts both `@RestController`s and `@RabbitListener`s — one deployable process, no separate worker. |
| `platform-common` | `Money` value type, base entity/aggregate types, ID generation, exception hierarchy + `ProblemDetail` advice, correlation-id support. |
| `platform-infrastructure` | Outbox table + relay, RabbitMQ topology (exchanges/queues/DLX), `consumed_event` dedupe, `audit_event` + `@Audited` aspect, minimal notification stub consumer. |
| `platform-security` | Role enum (6 actors), JWT issuance/validation, method-security config, `SYSTEM_SERVICE` principal propagation onto listener threads. |
| `applicant-origination` | Applicant, Application, ApplicationVersion (immutable), Consent, Document metadata. |
| `workflow` | Hand-rolled state machine core, `workflow_task`, work-queue. |

Added as later milestones need them: `verification`, `decisioning` (+ policy admin + credit-score client), `offers`, `funding` (+ loan account/repayment schedule), `notifications` (promoted out of `platform-infrastructure` once it has real channels).

**Boundary rule**: a module never imports another bounded context's JPA entity or repository directly. Cross-context access goes through an `xxx.api` package (DTOs + port interfaces) or, preferably, domain events. Enforced with an **ArchUnit** suite (`ModuleBoundaryTest` in `platform-app`) closed as Milestone 2 prep, before Epic 2.1 — see ADR 0001's status update and risk 5 below.

**Flyway**: each module ships `db/migration/<module>/` on the classpath; `platform-app` aggregates all locations. Timestamp-prefixed filenames (`V20260810120000__applicant__create_applicant.sql`), not sequential integers, since modules land migrations independently.

### A.2 Spring Boot stack

Spring Web (MVC — blocking is fine at this scale, simpler to learn than WebFlux), Spring Data JPA + PostgreSQL, Flyway, Spring Security (self-issued JWT), Spring Validation (`jakarta.validation`), Spring AMQP (RabbitMQ), Spring Boot Actuator, Testcontainers (Postgres/RabbitMQ/MinIO), springdoc-openapi, Logback + MDC correlation IDs, Micrometer, Resilience4j (timeout/circuit-breaker around the credit-score call — required by "third-party failures never silently approve/decline"), Lombok (getters only on immutable entities, no setters on append-only rows).

### A.3 State machine: hand-rolled, not Spring Statemachine

Enum + transition-table per aggregate (`ApplicationStatus`, `OfferStatus`, `FundingStatus`), each with an allowed-transitions map validated inside a `WorkflowTransitionService` (`@Transactional`, emits audit + outbox atomically, throws a domain exception mapped to 409 on an illegal transition). The blueprint's state model (§4) is small and well-defined — Spring Statemachine's regions/guards/actions/persistence machinery is disproportionate overhead and doesn't teach anything more load-bearing than getting the outbox and concurrency right.

### A.4 Outbox → RabbitMQ mechanics

- **Outbox table**: `outbox_event(id, aggregate_type, aggregate_id, event_type, payload JSONB, status, attempts, created_at, published_at, correlation_id)`, written in the *same* transaction as the business change — this is what gives atomicity without two-phase commit.
- **Relay**: `@Scheduled` poller, `SELECT ... FOR UPDATE SKIP LOCKED` on pending rows, publishes via `RabbitTemplate` with **publisher confirms** (`spring.rabbitmq.publisher-confirm-type=correlated`). Mark `PUBLISHED` only inside the confirm callback — marking it right after `convertAndSend` risks silently losing events on a crash between send and broker ack.
- **Topology**: topic exchange `loan.events`, routing key = event type. Each consumer module owns a durable queue bound to its routing patterns, each with `x-dead-letter-exchange`/`x-dead-letter-routing-key`. Spring AMQP's stateless retry interceptor (exponential backoff, capped attempts) runs before a message is dead-lettered.
- **DLQ → ops task**: a `@RabbitListener` on each DLQ inserts a `workflow_task`. This is a correctness requirement, not hardening polish — see A.5.
- **Consumer idempotency**: `consumed_event(consumer_name, event_id)` unique constraint, insert-or-skip in the same transaction as the business effect, dedupes RabbitMQ's at-least-once delivery.
- **Webhook dedupe**: a separate `inbox_message` table keyed by the mock payment provider's delivery id — distinct from `consumed_event` because it dedupes an inbound signed HTTP POST, not an AMQP message. Keep the three dedupe mechanisms (HTTP `Idempotency-Key`, AMQP `consumed_event`, webhook `inbox_message`) physically and conceptually separate even though the pattern rhymes.
- **Ack discipline**: `AcknowledgeMode.MANUAL`; ack only after the business transaction commits, never before.

### A.5 DLQ → ops task lands in Milestone 2, not hardening

The blueprint's own delivery sequence sketch lists this under a later "hardening" phase — that's a trap. "Exhausted failures create an operational task" is a correctness NFR, not polish, so it's pulled forward into Milestone 2 (Epic 2.2) here.

### A.6 JPA immutability patterns

- **Append-only** (`ApplicationVersion`, `Decision`, `PolicyVersion`/`ScorecardVersion`/`PricingVersion`, `AuditEvent`, `OutboxEvent`, `UnderwritingSnapshot`): no setters, fields set via constructor, package-private no-arg ctor for Hibernate, repositories expose only insert + read. Stretch (M6): `REVOKE UPDATE, DELETE` on these tables from the app's runtime DB role, defense in depth.
- **Mutable aggregates** (`Application.status`, `Offer.status`, `LoanAccount.status`): `@Version` optimistic locking — matters because at-least-once delivery can cause concurrent transition attempts.
- `Decision` stores the exact `underwriting_snapshot_id`, `policy_version_id`, `scorecard_version_id`, `pricing_version_id`, plus `credit_score_model_version` from the FastAPI response — **captured from the first decisioning sprint**, not retrofitted later; full reproducibility is much harder to backfill onto already-recorded decisions.
- **Money**: single `Money` value object in `platform-common`, `BigDecimal` fixed scale (2 for currency, 6 for APR), Postgres `numeric(x,y)`, `HALF_EVEN` rounding everywhere.

### A.7 Security / authz for the 6 roles

Hybrid: coarse **endpoint-level** role gating (`SecurityFilterChain.authorizeHttpRequests`) for module/resource boundaries (e.g. `/policies/**` → `POLICY_ADMIN` only), plus a handful of **method-level** `@PreAuthorize` ownership checks for rules an endpoint pattern can't express (an applicant may only `PATCH` their own draft). Auditor's read-only-everything falls out naturally from only granting `GET` mappings to that role. Deliberately **not** building a fine-grained ACL table — overkill for 6 static roles.

**Auth**: self-issued JWT (`/auth/login` against seeded synthetic users) — teaches the real Spring Security JWT filter chain, a common production pattern. Keycloak/OIDC is a legitimate stretch goal, not core scope.

**System principal**: `@RabbitListener` methods run under a synthetic `SYSTEM_SERVICE` `SecurityContext` set on the consumer thread, so system-driven transitions still produce a non-null `audit_event.actor`. Specced in Milestone 1 — easy to miss, awkward to retrofit.

### A.8 Object storage

MinIO (S3-compatible) in Docker Compose, accessed via AWS SDK v2 S3 client (same client code works against real AWS later). Bucket `loan-documents`, key = `applicationId/documentId`. The `document` table stores only the object key + metadata + content hash — bytes never touch Postgres. Simple proxy-upload (multipart POST → API → MinIO) to start; presigned direct-upload URLs are a stretch.

### A.9 Credit-score FastAPI service

- Plays the role of an external bureau/risk-score provider called synchronously by the Java decision engine — a deterministic, explainable, weighted-formula scorer (mirrors the blueprint's "first scorecard is deterministic, not ML"). It is **not** the policy decision itself; that stays in Java's scorecard/policy evaluation.
- `POST /score`: synthetic financial/employment features + requested amount/term in → numeric score, band, per-factor reason contributions, and a `model_version` string (persisted on `Decision`, see A.6) out.
- Introduced in **Milestone 3** — nothing needs it before there's a decision engine to call it.
- Contract-first: hand-documented OpenAPI/JSON contract; a WireMock stub test (fast, no live service needed) plus a genuine Testcontainers integration test against the real FastAPI image.
- Stretch (M6+, optional): swap in a real scikit-learn model trained on synthetic historical outcomes from the data generator, gated behind `model_version`, without changing the Java-side contract.

### A.10 Synthetic data generator

Python CLI (`synthetic-data-generator/`), Faker-based, `click`/`argparse` surface. Seeds only front-door entities (Applicant + draft/submitted Application + Document metadata) via `--target=api` mode against the real REST API — this dogfoods the API and never fakes terminal states, so demo applications flow through the real async pipeline. `--scenario auto-approve|refer|funding-fail` flags tune **input shape** (income/debt ratio, a verification-mismatch flag), not hard-coded score thresholds, so the generator doesn't break every time a policy version changes. A `--target=db` bulk-insert mode for load testing is a later (M6) addition, not needed at first.

Built as the **last epic of Milestone 1** — every later milestone benefits from seeded demo data.

### A.11 Mock payment provider

Built as an **internal delayed self-callback simulator** inside the `funding` module rather than a separate deployable — enough to exercise real async/webhook/signature/idempotency mechanics (a signed callback arrives after a simulated delay) without over-building infrastructure for a mock.

### A.12 Docker Compose (local dev/demo)

`postgres` (healthcheck), `rabbitmq:3-management` (management UI on 15672), `minio` (+ a one-shot `minio/mc` init container to create the bucket), `credit-score-service` (from Milestone 3), `app` (`platform-app` jar, depends on the others healthy). `application-docker.yml` (compose-networked hostnames) vs `application-local.yml` (IDE-run app against dockerized infra) profiles — the day-to-day loop is infra-in-Compose plus the app run/debugged from IntelliJ, not rebuilding images per edit.

### A.13 Single process, not a separate worker

One Spring Boot jar hosts both REST controllers and `@RabbitListener`s — simplest, still a faithful "modular monolith" per blueprint §8, no second deployable needed. A `--profile=worker`-only mode (web MVC disabled, listeners kept) is trivial to add later if horizontal scaling of consumers is ever needed — not built now.

### A.14 Testing strategy

- **Unit**: policy/scorecard evaluation as pure, table-tested functions; the full state-transition matrix from §4, every (state, event) pair, parameterized.
- **Contract**: credit-score client and mock-payment-provider adapter against WireMock stubs matching documented contracts.
- **Integration** (Testcontainers: Postgres + RabbitMQ + MinIO): outbox relay atomicity/publish-once, consumer idempotency (same event published twice → one effect), webhook dedupe (same payload twice → one state change).
- **E2E**: at least one automated golden-path (demo scenario 1) test in CI.
- **ArchUnit**: module-boundary rules — no cross-context entity/repository imports outside `xxx.api`.
- **Authz matrix**: 6 roles × endpoints sweep (Milestone 6).

---

## Part B — Phased Roadmap

**Sizing note**: AI-assisted build, so sprints are scoped by cohesive, reviewable increments — each epic below is one sprint. **24 sprints across 6 milestones**, sequenced strictly by dependency: a milestone starts only once the prior milestone's done-criteria are demonstrably met, never on a calendar cadence. Milestones can compress by running independent epics back-to-back; they must not compress by skipping done-criteria (tests, review, a working demo) — that discipline is the actual point of keeping SDLC structure in an AI-assisted build.

### Milestone 1 — Foundation (6 sprints, no dependencies)

| Epic | Scope | Done when |
|---|---|---|
| 1.1 Project skeleton & infra bootstrap | Maven reactor (parent + `platform-app` + `platform-common`), Docker Compose (postgres/rabbitmq/minio, healthchecks), Flyway empty baseline | `docker compose up` + app run gives `/actuator/health` = 200 against live Postgres/RabbitMQ |
| 1.2 Security & roles | `platform-security`, 6-role enum, self-issued JWT (`/auth/login`, seeded synthetic users), `SecurityFilterChain` + one `@PreAuthorize` example | Each role can log in and hit a role-gated test endpoint, verified by an integration test |
| 1.3 Cross-cutting plumbing | `platform-infrastructure` — outbox table + relay (confirmed publish), RabbitMQ topology + DLX, `consumed_event` dedupe, `audit_event` + `@Audited` aspect, correlation-id filter + structured logging, `SYSTEM_SERVICE` principal on listeners, minimal `notification` entity + log-only `notification.requested` consumer | A demo aggregate write produces exactly one consumer side-effect under forced duplicate delivery, with a matching audit row and a notification row |
| 1.4 Applicant & Application draft/submit | `applicant-origination` — Applicant, Application, ApplicationVersion (immutable), Consent (captured atomically at submit), Document metadata (MinIO-backed). Endpoints: `POST /applications` (Idempotency-Key), `PATCH /applications/{id}`, `POST /applications/{id}/submit`, `POST /applications/{id}/documents`, `GET /applications/{id}`, `GET /applications/{id}/timeline` | Draft → edit → submit produces an immutable version + consent snapshot + `application.submitted` outbox event + audit trail; duplicate submit with the same Idempotency-Key returns the identical response without a double-create |
| 1.5 Synthetic data generator v1 | Python CLI, `--target=api`, Faker-based, configurable count | Running it against a fresh stack populates N visible submitted applications |
| 1.6 ADR discipline starts here | One short ADR per major M1 decision (module topology, outbox+relay design, hand-rolled state machine, self-issued JWT, MinIO), written contemporaneously | ADRs exist alongside the code that implements them, not reconstructed later |

No demo scenario is achievable yet — this milestone is the substrate everything else builds on.

### Milestone 2 — Workflow & Verification (3 sprints, depends on M1)

| Epic | Scope | Done when |
|---|---|---|
| 2.1 State machine core | Hand-rolled transition tables + `WorkflowTransitionService` in `workflow`; submit now drives `DRAFT → SUBMITTED → VERIFYING` through the engine | Full legal/illegal state-transition unit-test matrix is green |
| 2.2 Work queue & DLQ → ops task | `workflow_task` entity, `GET /work-queue`, DLQ consumer inserting manual-resolution tasks on exhausted retries | A forced consumer failure surfaces as a work-queue item after retries exhaust, with no silently lost application |
| 2.3 Synthetic verification adapters | `verification` module, async consumer on `application.submitted`, deterministic mock identity/income checks (configurable to simulate mismatch/failure for later demo scenarios), drives `VERIFYING → UNDERWRITING` | Submitted applications auto-progress to UNDERWRITING, visible on the timeline; a simulated transient failure retries successfully |

### Milestone 3 — Decisioning (5 sprints, depends on M2)

| Epic | Scope | Done when |
|---|---|---|
| 3.1 Underwriting snapshot | Immutable `UnderwritingSnapshot`, created once per application on entering UNDERWRITING (unique constraint) | Integration test proves exactly-once creation |
| 3.2 Policy/scorecard/pricing admin | Immutable, publish-only `PolicyVersion`/`ScorecardVersion`/`PricingVersion`, each its own independently-versioned resource (`POST`/`POST .../publish` under `/policies`, `/scorecards`, `/pricing`, all `POLICY_ADMIN`) | Publishing a new version doesn't change which version an already-recorded decision references |
| 3.3 Credit-score FastAPI service v1 | New `credit-score-service/`, deterministic `POST /score`, `model_version` field, Dockerfile + compose wiring | Standalone `curl /score` works; registered in Compose |
| 3.4 Decision engine integration | Synchronous WebClient call (Resilience4j timeout/circuit-breaker), weighted-band evaluation, `Decision` (all 4 outcomes) with full version/evidence traceability + `credit_score_model_version` captured | All 4 outcomes reachable by tuning synthetic input; a forced credit-score outage yields `REFERRED`/ops task, never a silent wrong decision |
| 3.5 Contract tests | WireMock stub test + Testcontainers real-service integration test for the credit-score client | Both green in CI |

→ Demo scenario 1 (auto-approve) fully achievable; scenario 2's decisioning half is in place.

### Milestone 4 — Operations & Underwriter Actions (3–4 sprints, depends on M3)

| Epic | Scope | Done when |
|---|---|---|
| 4.1 Underwriter case actions | `POST /cases/{id}/decision` (reason code required), creates a **new** decision record referencing the automated one, transitions `REFERRED → APPROVED\|DECLINED` or back to `UNDERWRITING` | Original automated decision stays queryable alongside the override, both in the audit trail |
| 4.2 Ops exception handling | `ops_analyst`-scoped work-queue subset (verification/funding exceptions only, enforced by role), manual retry/resolve actions | The M2.2 forced-failure scenario is now resolvable by an ops_analyst via API |
| 4.3 Timeline/evidence completeness | `GET /applications/{id}/timeline` flesh-out for full chronology + role-scoped visibility across all 5 human roles | A single GET satisfies §11's "every screen links to decision version/reasons/evidence/timeline/audit" |
| 4.4 (optional) Underwriter console UI | Deferred — use Swagger UI/a REST client + a demo script instead | N/A — explicitly out of core scope; revisit after Milestone 6 if there's appetite |

→ Demo scenario 2 (referred + overridden) fully achievable.

### Milestone 5 — Lending Completion (4 sprints, depends on M4)

| Epic | Scope | Done when |
|---|---|---|
| 5.1 Offers | `offers` module, versioned `Offer` with expiration, auto-created from `APPROVED`/`CONDITIONAL_APPROVAL` decisions, idempotent `POST /offers/{id}/accept`, scheduled expiry job | Duplicate accept calls produce one acceptance; the expiry job correctly ages out untouched offers |
| 5.2 Mock payment provider & funding instruction | `funding` module, `FundingInstruction` with a provider idempotency key, internal delayed self-callback simulator | Accepting an offer → `FUNDING_PENDING`, then a signed self-triggered webhook arrives after a simulated delay |
| 5.3 Webhook handling + inbox dedupe | `POST /webhooks/mock-payments` (signature verification, `inbox_message` dedupe by delivery id); success → `Disbursement` succeeded → `FUNDED → ACTIVE` + `LoanAccount`; failure → `FUNDING_FAILED` → ops task (ties to M4's queue) | Duplicate webhook delivery causes exactly one state change; a simulated failure creates an ops task and a manual retry resolves without double disbursement — **this is demo scenario 3** |
| 5.4 Loan account & repayment schedule | Amortization schedule generator (fixed APR, term from `pricing_version`), read endpoint | A funded loan's schedule reconciles principal/interest to the original amount |

→ **All three blueprint demo scenarios are fully achievable at the end of Milestone 5.** This is the real "compelling demo" bar (§11) — reached here, not at the end of Milestone 6.

### Milestone 6 — Hardening & Portfolio Polish (4 sprints, depends on M5)

| Epic | Scope | Done when |
|---|---|---|
| 6.1 Observability | Micrometer metrics (queue age, provider error rate, decision latency), Actuator dashboards; tracing propagation through RabbitMQ headers as a stretch | Visible queue-depth/decision-latency metrics during a generator-driven load run |
| 6.2 Fault injection & resilience proofs | Chaos toggles on verification/credit-score/mock-provider adapters; automated proofs that retry/backoff/DLQ/manual-resolution hold under induced faults; bulk `--target=db` generator mode + load test as a stretch | A documented fault-injection run shows no double-disbursement/no lost application under chaos |
| 6.3 Authorization sweep + ArchUnit finalization | 6-roles × endpoints test matrix; module-boundary ArchUnit suite finalized | Both CI-enforced and green |
| 6.4 ADR index + demo script | Consolidate ADRs written since Milestone 1, polished demo runbook covering all 3 scenarios, README with architecture diagram | Fresh clone + `docker compose up` + demo script reliably reproduces all 3 scenarios |

### Explicitly optional / stretch (not core scope)

ML-based credit-score model variant (swappable via `model_version`), underwriter console UI, Keycloak/OIDC in place of self-issued JWT, presigned MinIO uploads, Debezium/CDC instead of the polling outbox relay, k8s/Helm deployment, real cloud S3/RDS, any collections/delinquency servicing beyond schedule generation (out of the blueprint's product boundary entirely, not just deferred).

---

## Risks and sequencing traps

1. **Outbox publish-confirm ordering** — marking `outbox_event` `PUBLISHED` before the RabbitMQ broker ack (rather than in the confirm callback) silently loses events on a crash between send and ack. Get this right in Milestone 1.3; a lot of later correctness depends on it.
2. **Manual ack before commit** — acking a message before the corresponding DB transaction commits loses effects on crash. `AcknowledgeMode.MANUAL`, ack only after commit.
3. **DLQ-to-ops-task must not slip to hardening** — it's a correctness NFR (blueprint §3), not polish; this roadmap deliberately pulls it into Milestone 2.
4. **Spring Statemachine learning-curve risk** — don't let it become the load-bearing state engine; hand-rolled tables are simpler and match the actually-small state model in §4.
5. **Module-boundary erosion** — direct cross-module JPA entity/repository access defeats §8's "extract later" goal. Fix early with `xxx.api` packages + ArchUnit; retrofitting boundaries later is expensive. **Closed as Milestone 2 prep, before Epic 2.1**: `ModuleBoundaryTest` now enforces the `xxx.api`-only rule for `infrastructure` and `origination` (extending to future bounded-context modules as they're added; `common`/`security` are exempt as targets — shared kernel and framework-wired respectively, see ADR 0001). Writing the rule surfaced one real pre-existing violation (`applicant-origination` reaching into `infrastructure.audit`/`infrastructure.idempotency` internals), fixed by relocating those 5 classes into `infrastructure.api` rather than grandfathering an exception.
6. **Module-count overreach** — an 11-module reactor from day one adds boilerplate disproportionate to value. Start at ~7, split only when a module's mixed concerns cause real pain, but keep internal package structure already organized along the eventual finer boundary so a later split is close to mechanical.
7. **Three dedupe mechanisms, kept distinct** — HTTP `Idempotency-Key`, AMQP `consumed_event`, webhook `inbox_message` solve different problems at different layers; don't collapse them into one concept/table even though the pattern rhymes.
8. **`credit_score_model_version` captured from the first decisioning sprint** (3.4), not retrofitted — full reproducibility is much harder to backfill onto already-recorded decisions.
9. **Synthetic-data-generator coupling to policy internals** — scenario flags should target input *shape* (verification mismatch, income/debt ratio), not hard-coded score thresholds, or the generator breaks every time a policy version changes.
10. **Skipped rigor, not slow progress, is the real pacing risk in an AI-assisted build** — accepting code that compiles without real tests, review, or a working demo per epic just because output is fast to generate. Each epic's done-criterion above is the guardrail against that.
