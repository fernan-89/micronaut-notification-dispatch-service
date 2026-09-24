# ADR-023: Notification FSM and delivery channels

## Status
Accepted

## Context
Catalog task **NTF-01 — Dispatcher Scaffold & Channels Setup**: this service is the first real
consumer of the platform's event backbone (`thinklab-service-kit` ADR-003), starting with a welcome
notification on `thinklab.party-authentication.user.initiated`. It needs a lifecycle for a Notification
Control Record and at least one real, testable-without-external-infrastructure delivery channel.

## Decision

### No forensic audit trail
Unlike `it-asset-registry`'s `Asset` (ADR-003 of that service), `Notification` carries no audit ledger.
A Notification is operational/transient dispatch data — it is not itself a system of record for
anything, the event that triggered it already is. Every mutating domain method changes only
`status`/`attempts`/`lastError`/`updatedAt` together, so a single granular
`NotificationRepository.updateStatus(id, status, attempts, lastError)` covers all of them, instead of
one persistence method per transition.

### `retry` is outside the generic transition table
Every other transition (`send`, `deliver`, `fail`, `cancel`) goes through the standard
`NotificationStatus.validateTransitionTo` table, mirroring `AssetStatus`. `retry` does not: it is
deliberately allowed from **two** source states — `FAILED` (the normal case) and `SENDING` (a cheap
mitigation for a notification stuck mid-dispatch after a crash; there is no automatic sweep/requeue in
v1, kit ADR-003's "at-least-once, no dead-letter" limitation applies the same way here). Every retry
consumes one attempt regardless of source state; once `attempts >= maxAttempts` it is rejected as a 409
("Retry Exhausted"), the same `ERR-NTF-00409` family as an illegal transition.

### Channels: a small port, two v1 adapters
`NotificationChannelPort` (`type()`, `send(Notification)`) is the outbound port. Two implementations
ship in v1:
- `LogNotificationChannel` — always on, a structured log line. This is the channel the welcome-email
  pilot uses, since it needs no external infrastructure to be real and testable end to end.
- `WebhookNotificationChannel` — opt-in (`thinklab.notifications.webhook.enabled`), a plain HTTP POST
  of a small JSON payload to **one, globally-configured** URL (`thinklab.notifications.webhook.url`),
  not one URL per notification; `recipient` travels inside the JSON body. It does not reuse the kit's
  `HttpTransport` — that class lives in the kit's security package for a different purpose, and this is
  single-consumer business logic that does not belong in a shared library (kit ADR-001). A local
  `WebhookTransport` interface plays the same testability role.

### A missing channel is a delivery failure, not a distinct error
If a Notification requests `WEBHOOK` but the webhook channel bean does not exist (property off), or any
future channel is requested with no matching adapter registered,
`DispatchNotificationUseCase` treats "no channel found" identically to the channel's own `send` throwing
— the Notification is marked `FAILED` with a descriptive `lastError`. No new exception or HTTP status
was introduced for this case.

## Consequences
- Positive: adding a third channel is a new `NotificationChannelPort` bean, nothing else changes; the
  FSM stays simple because `retry`'s two-source-state behavior is isolated in one domain method instead
  of complicating the general transition table.
- Negative: no audit trail means there is no forensic history of *who* triggered a given control action
  (`X-Executor` is required for the domain guard but is not persisted anywhere) — acceptable because a
  Notification is not a compliance record, but a real limitation if that ever needs to change later.
