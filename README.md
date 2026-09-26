# Thinklab Notification Dispatch Service

**Version:** v1.0.0-BIAN

**Status:** Reference implementation (ThinkLab portfolio project)

## Overview

The Thinklab Notification Dispatch Service delivers notifications triggered by domain events from
other services on the platform's event backbone (`thinklab-service-kit` ADR-003), starting with a
welcome notification on `thinklab.party-authentication.user.initiated`. It implements the
BIAN-aligned `notification-dispatch` Service Domain (ADR-013): `Notification` is the Control Record
and every route follows the `/{control-record-id}/{behavior-qualifier}` convention.

Unlike `it-asset-registry`'s `Asset`, a Notification carries no forensic audit trail — it is
operational, transient dispatch data, not a compliance ledger (ADR-023). An inbound event is handled
in-process, not through the REST API: the JetStream subscriber creates the Notification and
dispatches it through a channel (`LOG`, always on; `WEBHOOK`, opt-in) in one automatic
`PENDING -> SENDING -> (DELIVERED | FAILED)` flow. The `control/*` REST endpoints exist for manual
ops/Postman/tests, not as the automatic path (ADR-023, ADR-024).

Built with Java 21 and Micronaut 4.4.2 on a strict Hexagonal Architecture and a fully reactive
stack (Project Reactor, reactive MongoDB driver, NATS JetStream).

## Technology Stack

* **Runtime:** Java 21 LTS
* **Framework:** Micronaut 4.4.2 (AOT optimized, reflection-free DI and Serde)
* **Reactive Engine:** Project Reactor (Mono / Flux)
* **Persistence:** Reactive MongoDB (`thinklab_notification_db`, collection `notifications`)
* **Event backbone:** NATS JetStream, via `thinklab-service-kit` (outbox/publish) + a local durable
  pull-style consumer (`JetStreamNotificationSubscriber`, ADR-024)
* **Observability:** W3C Trace Context, SLF4J/Logback, Reactor MDC bridge
* **Containerization:** Google Distroless (nonroot), read-only root filesystem
* **Testing:** JUnit 5, Mockito, Reactor Test (exhaustive FSM matrix, use cases, controller, adapter,
  channels, event handler/subscriber, handler)
* **Documentation:** OpenAPI 3.0 / Swagger generated at compile time

## Domain Model

```text
Notification {
  id, organisationId, recipient, subject, body, channel,
  status, attempts, maxAttempts (default 5), lastError?, createdAt, updatedAt
}
channel: LOG | WEBHOOK
```

### Lifecycle (ADR-023)

```text
PENDING -> SENDING -> DELIVERED (terminal)
              |
              +------> FAILED
PENDING -> CANCELLED (terminal)
FAILED  --retry (attempts < maxAttempts)--> SENDING
SENDING --retry (crash mitigation)--------> SENDING
```

* `retry` is deliberately outside the generic transition table: it is the one action allowed from two
  source states (`FAILED`, the normal case, and `SENDING`, a cheap mitigation for a notification stuck
  mid-dispatch after a crash — there is no automatic sweep/requeue in v1). Every retry consumes one
  attempt; once `attempts >= maxAttempts` retry is rejected with `ERR-NTF-00409`.
* There is no `DELETE` — a Notification is either `DELIVERED` or `CANCELLED`.

## BIAN Behavior Qualifier Contract (`/notification-dispatch/v1`)

`X-Tenant-Id` (Organisation UUID) is mandatory on `initiate` and the collection `retrieve`;
`X-Executor` is mandatory on every `control/*` action.

| Behavior Qualifier | Method & Path |
|---|---|
| initiate | `POST /notification-dispatch/v1/initiate` |
| retrieve (single) | `GET /notification-dispatch/v1/{id}/retrieve` |
| retrieve (collection, filter `status`) | `GET /notification-dispatch/v1/retrieve` |
| control/send, deliver, retry, cancel | `PUT /notification-dispatch/v1/{id}/control/{action}` |
| control/fail (body: `errorMessage`) | `PUT /notification-dispatch/v1/{id}/control/fail` |

### Error catalog (RFC 7807, `error_code` field)

| error_code | HTTP | Meaning |
|---|---|---|
| `ERR-NTF-00404` | 404 | Notification not found |
| `ERR-NTF-00409` | 409 | Illegal lifecycle transition or retry exhausted (state conflict) |
| `ERR-VALIDATION-00400` | 400 | Payload/header/identifier validation failure |
| `ERR-INTERNAL-00500` | 500 | Unexpected technical failure |

Example:

```bash
curl -X POST http://localhost:8089/notification-dispatch/v1/initiate \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 6f1c7a52-3d0b-4a44-9c3e-0a7d1f6e2b10" \
  -d '{"recipient":"ada@thinklab.com","subject":"Welcome","body":"Hello Ada","channel":"LOG"}'
```

## Operational Procedures

```bash
# Build, run AOT optimizations and test
./gradlew clean build

# Start the service (default port 8089)
./gradlew run

# Container image
docker build -t thinklab-notification-dispatch-service:latest .
```

* **Health:** `http://localhost:8089/health`
* **Swagger UI:** `http://localhost:8089/swagger-ui`
* **Postman suite:** `docs/postman/` (lifecycle + negative scenarios)

### Configuration

| Variable | Default | Purpose |
|---|---|---|
| `MICRONAUT_SERVER_PORT` | `8089` | HTTP port |
| `MONGODB_URI` | `mongodb://localhost:27017/thinklab_notification_db` | MongoDB connection |
| `HASH_SERVICE_URL` | `http://localhost:8080` | Hash Token Registry base URL |
| `THINKLAB_EVENTS_ENABLED` | `false` | Turns on the JetStream subscriber and the outbox relay |
| `THINKLAB_EVENTS_NATS_URL` | `nats://localhost:4222` | NATS JetStream connection |
| `THINKLAB_WEBHOOK_ENABLED` | `false` | Turns on the opt-in `WEBHOOK` channel |
| `THINKLAB_WEBHOOK_URL` | `http://localhost:9999/webhook` | The single, globally-configured webhook target |
| `THINKLAB_SECURITY_ENABLED` | `false` | Requires a bearer token (verify-only; see ADR-021 of party-authentication) |

## Architecture Decision Records

`docs/adr/`: 001 hexagonal reactive stack · 005 UUID identity sovereignty · 013 BIAN service domain
conventions · 019 HTTP 409 for state conflicts · 023 Notification FSM and channels (no audit trail,
`retry` from `FAILED`/`SENDING`, webhook-not-configured treated as a channel failure) · 024 JetStream
subscriber and idempotent consumption (durable pull consumer, at-least-once delivery, no dead-letter
subject in v1).

## License

Licensed under the [PolyForm Strict License 1.0.0](LICENSE): you may read and use this software for noncommercial purposes only. Modifying it, creating derivative works, redistributing it and any commercial use are not permitted without a separate written license. This software is not open source.
