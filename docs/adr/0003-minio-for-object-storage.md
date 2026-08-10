# 0003. MinIO for local object storage

## Status

Accepted

## Context

The blueprint (§5) is explicit that document bytes must never live in the relational database — only object key, type, status, and checksum. Milestone 1 needed an object-storage service available locally for that boundary to be real from the start, even though nothing writes to it until Epic 1.4's document-upload endpoint.

## Decision

Run MinIO (S3-compatible) in Docker Compose, with a one-shot `minio/mc` init container that creates the `loan-documents` bucket on startup. The application will use the AWS SDK v2 S3 client against it, so the same client code will work against real AWS S3 later without a rewrite — only endpoint/credential configuration changes.

## Consequences

Local dev/demo gets a real object-storage boundary from Milestone 1 onward instead of the document-bytes-in-Postgres shortcut being available to accidentally take later. No application code depends on this yet — the bucket exists and is reachable (verified via the `minio-init` container logs during Epic 1.1), but the actual S3 client and upload endpoint are Epic 1.4's work, not this one.