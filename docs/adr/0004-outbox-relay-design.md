# 0004. Transactional outbox, relay, and cross-cutting plumbing design

## Status

Accepted

## Context

Milestone 1, Epic 1.3 needed a working transactional outbox (business writes and `outbox_event` rows sharing a DB transaction), a RabbitMQ topology with retry/DLX, consumer-side dedupe, an audit mechanism satisfying the blueprint's "all privileged actions are audited" NFR, and a way to attribute system-driven effects to a non-null actor. The roadmap locked in the mechanics (publisher confirms, manual ack, `consumed_event` dedupe) but not the concrete API shape callers use to participate in any of it.

## Decision

**Typed outbox payloads.** Every event implements `OutboxPayload` (in `platform-common`: `eventType()`, `aggregateType()`, `aggregateId()`), evaluated against alternatives in conversation before building: a stringly-typed `enqueue(String eventType, ...)` call, `@TransactionalEventListener`-mediated domain events, and Hibernate-lifecycle auto-publish. Rejected the string-typed call for the typo risk it invites across producer/consumer boundaries; rejected `@TransactionalEventListener` for its `BEFORE_COMMIT`-silently-no-ops-outside-a-transaction footgun; rejected lifecycle auto-publish as too opaque for events spanning multiple aggregates. `OutboxEventPublisher.enqueue(OutboxPayload)` (exposed via `platform-infrastructure.api`) is the one call every future business service makes, inside its own `@Transactional` method.

**Publish-confirm ordering.** `OutboxRelay` polls `PENDING` rows (`SELECT ... FOR UPDATE SKIP LOCKED`) and sends via `RabbitTemplate` with `publisher-confirm-type: correlated`. A row is marked `PUBLISHED` only inside the confirm callback (`OutboxPublishConfirmationService`, its own transaction, since the confirm arrives asynchronously on a different thread after the relay's own transaction already committed) — never right after `convertAndSend`.

**Manual ack, ack-after-commit.** `NotificationRequestedListener` (the actual `@RabbitListener` method) and `NotificationRequestedHandler` (the `@Transactional` business logic + `consumed_event` dedupe) are deliberately two separate classes/methods, not one. Acking from inside the `@Transactional` method would ack *before* Spring's transaction interceptor actually commits, since the interceptor's commit only happens after the method returns to its caller — exactly the crash-loses-effects-silently trap the roadmap warned about. The listener calls `handler.process(message)` and only acks after that call returns, which is genuinely post-commit.

**`SYSTEM_SERVICE` via AOP, not per-listener boilerplate.** `SystemServicePrincipalAspect` wraps every `@RabbitListener`-annotated method (`@Around("@annotation(...RabbitListener)")`) with a synthetic `SYSTEM_SERVICE` `SecurityContext`, so `@Audited` calls made from a listener attribute to a real actor instead of "anonymous," without every future consumer needing to remember to set it.

**Demo vehicle.** No throwaway JPA table. `/internal/plumbing-probe/demo-event` (mirroring Epic 1.2's `security-probe` pattern) is `@Audited` and enqueues a real `notification.requested` event, so the notification-stub consumer being exercised is the actual one future epics will use.

## Consequences

**Real bug caught by live verification, not the automated test.** `Notification`'s `id` was originally `@UuidGenerator`-assigned — a fresh random UUID on every insert, ignoring the `notificationId` already decided when the event was constructed (which the caller receives as the API response and which is the event's `aggregateId`). The integration test only asserted `notificationRepository.count() == 1`, which passed regardless, so it didn't catch the mismatch; a manual `curl` + direct DB query did. Fixed by having `Notification` accept that ID explicitly rather than generating its own, and the test was strengthened to assert `findById(returnedId)` resolves to the right row, not just that a row count is right. Same lesson as Epic 1.1/1.2: passing tests are not proof of correctness for anything the test didn't specifically assert.

**Known, accepted gap: `@Audited` is not proven atomic with its `@Transactional` method.** Both annotations sit on the same method (`PlumbingProbeService.createDemoEvent`), and Spring AOP's advisor ordering between a custom `@Aspect` and the built-in transactional advisor is not deterministic without explicit `@Order`/`@EnableTransactionManagement(order=...)` configuration — neither is set. In practice, `@AfterReturning` firing after the method returns means the audit write happens only when the business transaction is known to have succeeded (a thrown exception skips the audit entirely), which is a reasonable AOP-auditing tradeoff, but it is *not* guaranteed to be the same DB transaction — a crash in the narrow window between the two could produce a successful business change with no audit row. Not fixed now: no current use case exercises a rollback scenario where this matters, and forcing explicit ordering adds real complexity (hooking `@EnableTransactionManagement`'s order) disproportionate to that risk today. Revisit when a real mutating flow (Epic 1.4+) needs the atomicity guarantee to actually hold under rollback.

**DLQ→ops-task is deliberately absent.** The DLX/retry topology exists (`spring.rabbitmq.listener.simple.retry.*`, per-queue DLQ bindings), but nothing consumes the DLQ yet — `workflow_task` doesn't exist until Epic 2.2, per the roadmap's own stated sequencing. A message that exhausts retries currently just sits in the DLQ unread.
