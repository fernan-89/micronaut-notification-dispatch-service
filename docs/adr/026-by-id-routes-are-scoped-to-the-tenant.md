# ADR 026: The by-id routes are scoped to the tenant

## Status
Accepted (2026-11-18). Part of the platform tenant audit (`thinklab-platform/docs/TENANT-AUDIT-2026-11.md`); same rule as workflow-approval ADR-037, change management ADR-038, asset registry ADR-029, hardware maintenance ADR-036, operation window ADR-022, discovery ADR-034, the site directory ADR-033, ci-type-catalog ADR-034 and topology graph ADR-034.

## Context
Every by-id route of a notification (retrieve and the control actions send, deliver, fail, retry, cancel) found the notification by id alone. The cross-tenant probe (`scripts/e2e/tenant-probe.sh` of the platform) showed a notification of tenant X answering 200 to tenant Y; worse, tenant Y could cancel it.

## Decision
- Every by-id route takes `X-Tenant-Id` (required, 400 if blank) and finds the notification only in that tenant: another tenant's is the same as none (404 `NotificationNotFoundException`), nothing is read or written. `NotificationRepository` has no unscoped `findById`.
- The welcome notification triggered by the `user.initiated` event is dispatched with the tenant of that event (the same one it was created with).
- The guarded writes of ADR-025 keep working on the id: they run after a scoped load.

## Consequences
- Positive: a UUID is no longer enough to read or drive another tenant's notification.
- Negative: a client that sends no tenant on a by-id route now gets a 400.
