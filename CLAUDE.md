# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

This repository is **pre-implementation**: it currently contains only planning documents, no source code. There is no `pom.xml`, no Maven reactor, and no application modules yet — do not assume any of the structure described below already exists on disk without checking first.

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

No build, lint, or test commands exist yet — Milestone 1, Epic 1.1 ("Project skeleton & infra bootstrap" in the roadmap) is what creates the Maven reactor and Docker Compose setup. Once that scaffolding exists, this section must be updated with the real `mvn`/Docker Compose commands (build, run a single test, start local infra, etc.) rather than left as a placeholder.
