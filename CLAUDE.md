# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

Milestone 1, Epics 1.1–1.3 are done. 1.1: the Maven reactor exists with `platform-app` and `platform-common`, Docker Compose brings up Postgres/RabbitMQ/MinIO, and Flyway applies an empty baseline. 1.2: `platform-security` exists — 6-role enum, self-issued JWT auth (`POST /auth/login`), a `SecurityFilterChain` with one endpoint-level role gate and one method-level `@PreAuthorize` ownership example, an `app_user` table, and a synthetic-user seeder (one demo user per role, see Commands below). 1.3: `platform-infrastructure` exists — transactional outbox (`OutboxEventPublisher.enqueue(OutboxPayload)`, typed payloads only, see ADR 0004), `OutboxRelay` publishing with confirms, RabbitMQ topology + DLX/retry, `consumed_event` dedupe, `@Audited` AOP-based audit logging, correlation-ID propagation, and a `SYSTEM_SERVICE`-principal aspect wrapping every `@RabbitListener`. Two probe endpoints exist only to exercise wiring and should be removed once real endpoints make them redundant: `/internal/security-probe/**` (Epic 1.4) and `/internal/plumbing-probe/**` (whenever a real flow first publishes `notification.requested`). Everything past Epic 1.3 (applicant/application, workflow, the rest of the modules) is not yet built; check the roadmap's Milestone 1 table before assuming a module or table exists.

## Source of truth documents

Two documents in `docs/` define this project completely and must be read before making architectural decisions or starting a new milestone:

- `docs/blueprint.md` — the *what/why*. Product boundary, actors/permissions, functional requirements, the application state model, the full data model and entity relationships, API surface, async event catalog, architecture map, and non-functional requirements (correctness, idempotency, authorization, auditability, resilience, privacy, observability, testing).
- `docs/roadmap.md` — the *how/when*. Concrete technology choices for every part of the blueprint (Maven module layout, Spring Boot stack, outbox/RabbitMQ mechanics, state machine approach, security model, the two Python services) and the phased delivery plan (6 milestones broken into sprint-sized epics with explicit done-criteria), plus a risks/sequencing-traps section.
- `docs/adr/` — architecture decision records, one per major decision, written contemporaneously as the roadmap's Milestone 1.6 (and later epics) produce them.

Do not re-derive or re-decide anything already settled in these two files (e.g. hand-rolled state machine vs Spring Statemachine, module count, RabbitMQ topology, dedupe strategy) — treat them as locked unless the user explicitly reopens the decision. When a milestone's implementation surfaces a real gap or contradiction in these docs, update the docs rather than silently diverging from them in code.

## Architecture summary (see the roadmap for full detail)

- **Monorepo, polyglot.** One Java Maven multi-module reactor for the core app, sibling Python directories for two services, Docker Compose ties everything together.
- **Java/Spring Boot is the primary application** (Java 21, Maven, palantir-java-format — currently disabled in `.idea/palantir-java-format.xml`, expected to be enabled once the reactor exists). Modular monolith: one deployable (`platform-app`) hosting both REST controllers and RabbitMQ listeners, organized as ~7 Maven modules (`platform-app`, `platform-common`, `platform-infrastructure`, `platform-security`, `applicant-origination`, `workflow`, growing to include `verification`, `decisioning`, `offers`, `funding`, `notifications` as milestones require them). Modules must not import another bounded context's JPA entities/repositories directly — cross-module access goes through an `xxx.api` package, enforced by ArchUnit.
- **Two Python services, deliberately scoped**: a FastAPI `credit-score-service` (synchronous, deterministic weighted-formula scorer called by the Java decision engine — introduced at Milestone 3, not before) and a `synthetic-data-generator` CLI (offline, seeds demo data by calling the real REST API rather than faking terminal states — introduced at the end of Milestone 1).
- **Async backbone is RabbitMQ from the start**, implementing a transactional outbox: business writes and `outbox_event` rows share a DB transaction; a scheduled relay publishes with publisher confirms and marks rows published only inside the confirm callback; per-consumer DLX-backed queues feed dead-lettered messages into `workflow_task` ops items. Three separate dedupe mechanisms exist for three separate problems and must not be conflated: HTTP `Idempotency-Key` (client retries), `consumed_event` table (AMQP at-least-once delivery), `inbox_message` table (webhook redelivery).
- **State machine is hand-rolled** (enum + transition table + `WorkflowTransitionService`), not Spring Statemachine — see the roadmap's rationale before introducing that library.
- **Everything decision-related is immutable/append-only**: `ApplicationVersion`, `Decision`, `PolicyVersion`/`ScorecardVersion`/`PricingVersion`, `AuditEvent`, `UnderwritingSnapshot` are insert-only with no update path; a `Decision` always carries the exact snapshot/policy/scorecard/pricing/credit-score-model version IDs it was computed from.

## Delivery sequence

Work proceeds through the roadmap's 6 milestones in strict dependency order (Foundation → Workflow & Verification → Decisioning → Operations & Underwriter Actions → Lending Completion → Hardening). Within a milestone, its epics are the actual unit of work — each epic in the roadmap has an explicit done-criterion (a runnable/demoable/testable increment, not just code written); treat that criterion as the acceptance bar before considering an epic finished, even though this is an AI-assisted build where output is fast to produce.

## Commands

- Build the reactor: `./mvnw -DskipTests package` (drop `-DskipTests` once tests exist).
- Run a single test class: `./mvnw -pl platform-app test -Dtest=SomeClassNameTest`.
- Start local infra (Postgres, RabbitMQ, MinIO + bucket init): `cp .env.example .env` (first time only, then edit real values), `docker compose up -d`. RabbitMQ management UI is at `localhost:15672`; MinIO console at `localhost:9001`.
- Run the app against dockerized infra (IDE or CLI): `java -jar platform-app/target/platform-app.jar --spring.profiles.active=local`, with the same `.env` variables exported into the shell (`set -a; source .env; set +a`). Confirms up via `curl localhost:8080/actuator/health`.
- Log in as any of the 6 seeded synthetic users (`applicant`, `underwriter`, `operations_analyst`, `policy_admin`, `auditor`, `system_service`) via `POST /auth/login` with that username and the `SEED_USERS_PASSWORD` value from `.env`; the response's `token` is a bearer JWT.

This reactor deliberately does **not** inherit from `spring-boot-starter-parent` (it needs its own multi-module dependency management instead), which silently loses two defaults that parent normally sets — both already bitten us once, so re-check before assuming either is on:
- The `spring-boot-maven-plugin`'s `repackage` goal is **not** auto-bound to `package` — `platform-app/pom.xml` binds it explicitly via `<executions>`. A future module needing its own executable jar needs the same binding, or `mvn package` silently produces a jar with no `Main-Class`.
- The `-parameters` javac flag is **not** on by default — set via `maven.compiler.parameters=true` in the root POM's `<properties>`. Without it, reflection-based parameter-name lookups fail silently rather than erroring, which broke `@PreAuthorize("#username == ...")` in `platform-security` (SpEL couldn't resolve the parameter name, so the check silently denied everyone). Also remember Maven's compiler plugin only recompiles *stale* sources — a `pom.xml` property change like this doesn't force recompilation on its own; run `./mvnw clean` first if a compiler-flag change doesn't seem to take effect.

Any future `@RabbitListener` must follow the pattern in `NotificationRequestedListener`/`NotificationRequestedHandler` (see ADR 0004): the listener method calls a separate `@Transactional` handler method and only acks *after* that call returns. Acking from inside the `@Transactional` method itself acks before Spring's transaction interceptor actually commits (the interceptor commits after the method returns to its caller, not as the method's last statement), which is the exact "ack before commit" trap the roadmap warns against.
