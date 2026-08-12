# 0006. Server-mediated document upload with server-computed checksum

## Status

Accepted

## Context

`roadmap.md` (§A.9) already locks in proxy-upload (`multipart POST → API → MinIO`) over presigned direct-upload URLs for Milestone 1, listing presigned uploads as an explicit stretch goal — that choice isn't reopened here. What the roadmap doesn't specify, and what Epic 1.4 had to decide during implementation, is two things: who computes the document's integrity checksum, and in what order the MinIO write and the `document` row's DB write happen relative to each other.

## Decision

The server reads the full multipart file into memory, computes its SHA-256 itself (`DocumentService.upload`, via `platform-common`'s `Sha256`), and only then writes to MinIO — the checksum is never accepted from the client. This makes the stored checksum authoritative: it reflects what the server actually received and pushed to storage, not a claim the client could get wrong or falsify.

Write ordering is MinIO-first, then the `Document` row: `documentStorageService.put(...)` happens before `documentRepository.save(...)`. If the DB write then fails, the handler calls a compensating `documentStorageService.delete(storageKey)` before rethrowing, so a save failure can't leave an object in MinIO with no `Document` row ever pointing at it (`DocumentService.java:63-68`). The alternative ordering (DB row first, then MinIO write) was rejected because it can leave a `Document` row referencing a storage key that was never actually written — a broken reference future timeline/decision code would have to defensively check for, instead of being able to trust.

## Consequences

Every stored checksum is server-verified, so downstream consumers (verification, underwriting evidence) can treat `document.checksum` as trustworthy without re-hashing. The cost is holding the full file in memory before either write happens, and a transient MinIO failure now happens *before* any DB row exists, rather than the reverse — a failed upload never accumulates a dangling `Document` row (fine, the client just retries the whole call), but an application crash or process kill in the narrow window between the successful MinIO `put` and the DB commit can still orphan an object with no compensating delete run. That orphan is accepted as harmless for now (an unreferenced object costs storage, not correctness) rather than solved with a two-phase/saga mechanism disproportionate to a synthetic-data platform.