# Loan Decisioning Platform

A portfolio-grade, fully synthetic-data platform for unsecured consumer installment loan origination and decisioning: application intake, async identity/income verification, versioned policy- and scorecard-driven underwriting, underwriter override workflows, offers, and mock loan funding with repayment schedule generation.

**This is not a production lender.** All data is synthetic, all verification and payment providers are mocked, and no decision produced by this system is suitable for real lending.

## Status

Pre-implementation — the repository currently holds planning documents only, no application code yet. See `docs/roadmap.md` for the milestone-by-milestone delivery plan and current scope.

## Documentation

- [`docs/blueprint.md`](docs/blueprint.md) — the *what/why*: product boundary, actors and permissions, functional requirements, state model, data model, API surface, event catalog, architecture map, and non-functional requirements.
- [`docs/roadmap.md`](docs/roadmap.md) — the *how/when*: concrete technology choices and the phased milestone/epic delivery plan.
- [`docs/adr/`](docs/adr/) — architecture decision records written as implementation decisions are made.

## Technology

- **Java 21 + Spring Boot** (Maven multi-module modular monolith) — the primary application: application intake, workflow/state machine, decisioning, offers, funding.
- **Python (FastAPI)** — a standalone credit-score service, called synchronously by the Java decision engine.
- **Python (CLI)** — an offline synthetic data generator for seeding demo/test data through the real API.
- **PostgreSQL**, **RabbitMQ**, **MinIO**, orchestrated locally via **Docker Compose**.

Full rationale for each choice is in `docs/roadmap.md`.
