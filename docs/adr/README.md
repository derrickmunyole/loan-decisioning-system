# Architecture Decision Records

One short ADR per major architectural decision, written contemporaneously with the change (not reconstructed after the fact) — starting with `roadmap.md` Milestone 1.6.

## Convention

- Filename: `NNNN-short-title.md`, zero-padded sequential number (`0001-module-topology.md`, `0002-hand-rolled-state-machine.md`, ...).
- Sections: **Status** (proposed / accepted / superseded), **Context** (what forced the decision), **Decision** (what was chosen), **Consequences** (what this makes easier/harder, what it forecloses).
- Keep it short — a few paragraphs, not a design doc. The blueprint and roadmap already hold the detailed rationale for decisions made before code existed; ADRs here are for decisions made *during* implementation.
