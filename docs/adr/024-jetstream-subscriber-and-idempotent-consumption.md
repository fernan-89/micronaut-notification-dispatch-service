# ADR-024: JetStream subscriber and idempotent consumption

## Status
Accepted

## Context
Catalog task **NTF-02** (originally scoped as "Kafka Reactive Event Consumers" in the master catalog;
re-scoped to NATS JetStream per the platform's broker decision, kit ADR-003). This service is the only
consumer of `thinklab.party-authentication.user.initiated` today — the subscribing side of the event
backbone stays local to this service rather than living in the kit, since generalizing a
single-consumer abstraction would be speculative (kit ADR-003 makes the same call explicitly).

## Decision

### One class touches NATS; everything else is plain logic
`JetStreamNotificationSubscriber` is the only class in this service importing `io.nats.client.*`. On
startup it resolves the kit-configured `Connection`/stream (`thinklab.events.stream-name`) and creates a
durable pull-style consumer (`notification-dispatch-worker`, `AckPolicy.Explicit`, `MaxDeliver=5`,
filtered to the `user.initiated` subject) via the Simplification API
(`Connection.getStreamContext` -> `StreamContext.createOrUpdateConsumer` -> `ConsumerContext.consume`).
Every message is handed, as a raw string, to `UserInitiatedEventHandler.handle(String)` — a class with
zero NATS imports, fully unit-testable with plain strings. This mirrors kit ADR-003's own separation
between `NatsConnectionFactory`/`NatsEventPublisher` (NATS-aware) and `OutboxStore`/`OutboxRelay`
(broker-agnostic).

### At-least-once delivery; the handler must be idempotent
`AckPolicy.Explicit` plus `msg.ack()` only after `UserInitiatedEventHandler.handle` completes
successfully means a crash between processing and acking redelivers the same message. Creating a
duplicate welcome Notification on redelivery is an accepted risk in v1 — no deduplication key exists
yet on the `user.initiated` subject. This is the same at-least-once trade-off kit ADR-003 already
accepts for the outbox-to-broker hop; it now also applies to the broker-to-consumer hop.

### No dead-letter subject in v1
A message that fails processing below `MaxDeliver` (5) attempts is `nak()`'d for redelivery. A message
that still fails at the delivery-count ceiling is `term()`'d — logged at `ERROR`, permanently dropped,
never redelivered, never parked anywhere for later inspection. A real dead-letter subject (publish the
terminated message's payload to e.g. `thinklab.notification-dispatch.dead-letter`) is deferred: nothing
in v1 consumes one yet, and building the publish side without a consumer would be speculative
infrastructure.

## Consequences
- Positive: `UserInitiatedEventHandler` is trivially testable (no live broker needed for its own test
  suite); the subscriber wiring itself is fully unit-tested by mocking the public
  `Connection`/`StreamContext`/`ConsumerContext`/`Message` interfaces, the same technique kit ADR-003
  uses for the publish side — no live-broker test fixture was needed anywhere in this service's suite.
- Negative: a permanently poison message is silently lost after 5 attempts (logged, not surfaced
  anywhere an operator would see without reading logs); a duplicate welcome notification can occur on
  redelivery. Both are accepted, documented limitations, not implementation oversights.
