# Loan Decisioning Platform

A portfolio-grade, fully synthetic-data platform for unsecured consumer installment loan origination and decisioning: application intake, async identity/income verification, versioned policy- and scorecard-driven underwriting, underwriter override workflows, offers, and mock loan funding with repayment schedule generation.

**This is not a production lender.** All data is synthetic, all verification and payment providers are mocked, and no decision produced by this system is suitable for real lending.

## Status

**Milestones 1 (Foundation), 2 (Workflow & Verification), 3 (Decisioning), and 4 (Operations & Underwriter Actions) are done — Epics 1.1 through 4.3 — and Milestone 5 (Lending Completion) is in progress with Epic 5.1 (Offers) done.** The Maven reactor, local infra, 6-role JWT auth, transactional-outbox/RabbitMQ plumbing, the applicant draft→submit flow, a synthetic data generator, and ADR discipline are all in place, with an ArchUnit suite enforcing module boundaries. Submitted applications now drive themselves through a hand-rolled state machine — DRAFT → SUBMITTED → VERIFYING → UNDERWRITING — via an async verification consumer with deterministic identity/income checks, a work queue surfaces failures that exhaust their retries instead of vanishing silently, and reaching UNDERWRITING now freezes an immutable snapshot of the application's facts and evidence. A policy admin can publish immutable, versioned policy/scorecard/pricing rules, a standalone credit-score service can score an applicant's affordability, and a decision engine ties all of it together: an automated, fully version-traced `Decision` drives every application out of UNDERWRITING into APPROVED, DECLINED, REFERRED, or CONDITIONAL_APPROVAL, with a credit-score provider outage handled as its own no-silent-decision path — backed by both a fast WireMock contract test and a Testcontainers test against the real service. An underwriter can now act on a `REFERRED` case directly — approve or decline it (recording a new, fully traceable override decision alongside the original automated one) or send it back for more evidence — and every `REFERRED` case now surfaces on the work queue instead of having no discovery path at all. The work queue is now role-scoped between underwriters and operations, an operations analyst can resolve or acknowledge an exception, and a credit-score provider outage is now retryable via API once the provider recovers — no case is left permanently stuck. `GET /applications/{id}/timeline` now gives every human role a role-appropriate view: the owning applicant sees their audit history, while underwriters, operations analysts, policy admins, and auditors all see a full aggregate — decision (with reasons and complete version traceability), verification evidence, and the event timeline — in one call. An `APPROVED`/`CONDITIONAL_APPROVAL` decision (automated or an underwriter's override, either way) now auto-creates a versioned `Offer` with real amortized terms, which the applicant can view and accept idempotently — untouched offers age out on their own via a scheduled sweep. See `docs/roadmap.md` for the full milestone/epic breakdown and current scope — Epic 4.4 (underwriter console UI) and Epics 5.2–5.4 (funding, webhooks, repayment schedule) are next/deferred.

What exists today:
- Applicant/application intake: draft → submit, document upload, HTTP idempotency on create/submit
- 6-role JWT authentication and authorization (`applicant`, `underwriter`, `operations_analyst`, `policy_admin`, `auditor`, `system_service`)
- Transactional outbox → RabbitMQ, with DLX/retry, `consumed_event` dedupe, and AOP-based audit logging
- A hand-rolled state machine (`workflow`) validating every application status transition, plus a role-scoped work queue (`GET /work-queue`) surfacing messages that dead-letter after exhausting retries, with an operations-only resolve action (`POST /work-queue/{id}/resolve`)
- Async synthetic verification (`verification`): identity and income checks that auto-progress a submitted application to UNDERWRITING, with synthetic input-shape signals for a simulated mismatch or transient failure
- An immutable underwriting snapshot (`decisioning`), created exactly once per application the moment it reaches UNDERWRITING
- Immutable, versioned policy/scorecard/pricing administration (`decisioning`), each independently publishable by a `policy_admin`
- A standalone credit-score FastAPI service (`credit-score-service/`) — deterministic, explainable scoring
- An automated decision engine (`decisioning`): calls the credit-score service, evaluates the result against a published policy/scorecard, and records an immutable, fully version-traced `Decision` that drives the application out of UNDERWRITING — with a dedicated, no-silent-decision path for a credit-score provider outage
- Underwriter case actions (`POST /cases/{id}/decision`): approve, decline, or send a `REFERRED` case back for more evidence, with the automated decision staying queryable alongside every override, and every `REFERRED` case now surfacing on the work queue
- Ops exception handling (`POST /cases/{id}/retry-decision`): retries a case referred by a credit-score provider outage once the provider recovers, resuming the pipeline instead of leaving it stuck
- Role-scoped application timeline (`GET /applications/{id}/timeline`): the owning applicant sees an audit-event chronology; underwriters, operations analysts, policy admins, and auditors all see a full aggregate — decision (reasons plus complete policy/scorecard/pricing/credit-score-model traceability), verification evidence, and the event timeline
- Versioned loan offers (`offers`): an `APPROVED`/`CONDITIONAL_APPROVAL` decision — automated or an underwriter's override — auto-creates an `Offer` with real amortized terms; the applicant can view (`GET /applications/{id}/offer`) and accept it idempotently (`POST /offers/{id}/accept`), and a scheduled sweep ages out untouched offers past their expiry
- A Python CLI that seeds demo applications by driving the real REST API end-to-end

Not yet built: mock payment funding, loan account and repayment schedule generation.

## Documentation

- [`docs/blueprint.md`](docs/blueprint.md) — the *what/why*: product boundary, actors and permissions, functional requirements, state model, data model, API surface, event catalog, architecture map, and non-functional requirements.
- [`docs/roadmap.md`](docs/roadmap.md) — the *how/when*: concrete technology choices and the phased milestone/epic delivery plan.
- [`docs/adr/`](docs/adr/) — architecture decision records written as implementation decisions are made.

## Architecture

A modular monolith: one deployable (`platform-app`) hosting both REST controllers and RabbitMQ listeners, split into Maven modules with cross-module access gated to each module's `xxx.api` package (enforced by an ArchUnit suite, not just convention).

- **`platform-common`** — shared value types, base entity types, exception hierarchy.
- **`platform-security`** — 6-role JWT auth, security config, synthetic-user seeding.
- **`platform-infrastructure`** — transactional outbox, RabbitMQ topology, audit logging, idempotency, correlation IDs.
- **`applicant-origination`** — applicant/application/document intake, draft→submit.
- **`workflow`** — the hand-rolled state machine (`WorkflowTransitionService`), `workflow_task` and the work queue.
- **`verification`** — async synthetic identity/income verification, consuming `application.submitted`.
- **`decisioning`** — the immutable underwriting snapshot, consuming `underwriting.requested`; policy/scorecard/pricing administration; and the automated decision engine.
- **`offers`** — versioned `Offer` creation, consuming `decision.created`; idempotent acceptance; a scheduled expiry sweep.
- **`platform-app`** — the executable Spring Boot application aggregating all of the above.
- **`synthetic-data-generator/`** — a `uv`-managed Python CLI that seeds demo applications by calling the real REST API.
- **`credit-score-service/`** — a standalone, stateless FastAPI service: a synchronous, deterministic, explainable weighted-formula scorer, played as an external credit bureau, called synchronously by `decisioning`'s decision engine.

**PostgreSQL**, **RabbitMQ**, and **MinIO** run locally via **Docker Compose**, alongside `credit-score-service`. A `funding` module is added as a later milestone requires it — see `docs/roadmap.md` for the full plan and rationale.

## Getting started

```bash
cp .env.example .env         # edit real values (or keep the defaults for local dev)
docker compose up -d         # Postgres, RabbitMQ, MinIO + bucket init

./mvnw -DskipTests package
set -a; source .env; set +a
java -jar platform-app/target/platform-app.jar --spring.profiles.active=local

curl localhost:8080/actuator/health
```

Log in as any of the 6 seeded users (`applicant`, `underwriter`, `operations_analyst`, `policy_admin`, `auditor`, `system_service`) via `POST /auth/login` with that username and `.env`'s `SEED_USERS_PASSWORD`; the response's `token` is a bearer JWT. RabbitMQ's management UI is at `localhost:15672`, MinIO's console at `localhost:9001`.

To seed demo applications against a running app:

```bash
cd synthetic-data-generator && uv sync
SEED_USERS_PASSWORD=... uv run synthetic-data-generator --count 10
```

Full command reference, including running a single test class and Python linting, is in `CLAUDE.md`.
