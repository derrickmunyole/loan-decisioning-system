# 0003. MinIO for local object storage

## Status

Accepted

## Context

The blueprint (§5) is explicit that document bytes must never live in the relational database — only object key, type, status, and checksum. Milestone 1 needed an object-storage service available locally for that boundary to be real from the start, even though nothing writes to it until Epic 1.4's document-upload endpoint.

## Decision

Run MinIO (S3-compatible) in Docker Compose, with a one-shot `minio/mc` init container that creates the `loan-documents` bucket on startup. The application uses MinIO's native Java SDK (`io.minio:minio`) against it rather than the AWS SDK v2 S3 client originally planned here — MinIO's client maps more directly onto the small set of operations actually needed (`putObject`/`removeObject`) without pulling in the AWS SDK's broader surface area for a single self-hosted target. Swapping to real AWS S3 later would mean changing the client library, not just endpoint/credential configuration — a cost accepted in exchange for the simpler dependency now.

## Consequences

Local dev/demo gets a real object-storage boundary from Milestone 1 onward instead of the document-bytes-in-Postgres shortcut being available to accidentally take later. Epic 1.4 built on this: `MinioConfig`/`DocumentStorageService` in `applicant-origination` use the MinIO SDK for server-mediated upload (see ADR 0006) and best-effort delete-on-failure cleanup.