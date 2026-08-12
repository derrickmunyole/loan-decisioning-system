# Loan Decisioning Platform

A portfolio-grade, fully synthetic-data platform for unsecured consumer installment loan origination and decisioning: application intake, async identity/income verification, versioned policy- and scorecard-driven underwriting, underwriter override workflows, offers, and mock loan funding with repayment schedule generation.

**This is not a production lender.** All data is synthetic, all verification and payment providers are mocked, and no decision produced by this system is suitable for real lending.

## Status

**Milestone 1 (Foundation) is done — Epics 1.1 through 1.6.** The Maven reactor, local infra, 6-role JWT auth, transactional-outbox/RabbitMQ plumbing, the applicant draft→submit flow, a synthetic data generator, and ADR discipline are all in place, with an ArchUnit suite enforcing module boundaries. See `docs/roadmap.md` for the full milestone/epic breakdown and current scope — Milestone 2 (Workflow & Verification) is next.

What exists today:
- Applicant/application intake: draft → submit, document upload, HTTP idempotency on create/submit
- 6-role JWT authentication and authorization (`applicant`, `underwriter`, `operations_analyst`, `policy_admin`, `auditor`, `system_service`)
- Transactional outbox → RabbitMQ, with DLX/retry, `consumed_event` dedupe, and AOP-based audit logging
- A Python CLI that seeds demo applications by driving the real REST API end-to-end

Not yet built: workflow/state machine, verification, decisioning, underwriter actions, offers, funding, loan servicing.

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
- **`platform-app`** — the executable Spring Boot application aggregating all of the above.
- **`synthetic-data-generator/`** — a `uv`-managed Python CLI that seeds demo applications by calling the real REST API.

**PostgreSQL**, **RabbitMQ**, and **MinIO** run locally via **Docker Compose**. A synchronous FastAPI credit-score service and further modules (`workflow`, `verification`, `decisioning`, `offers`, `funding`) are added as later milestones require them — see `docs/roadmap.md` for the full plan and rationale.

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
