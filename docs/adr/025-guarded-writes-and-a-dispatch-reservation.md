# ADR-025: Status changes are guarded writes; a dispatch reserves the notification before it sends

## Status
Accepted

## Context
The platform race audit (`thinklab-platform/docs/race-audit.md`, ACTION-PLAN 0.5) found that `updateStatus` wrote by `_id` only.
Every use case reads the notification, applies the transition in memory (where the state machine of ADR-023 is checked) and writes the result,
so two requests that read the same notification both passed the check and both wrote:

- a dispatch and a cancel of one PENDING notification were both answered "done"; the stored status was whichever wrote last
  (a cancelled notification could end up DELIVERED, mail already sent);
- two dispatches of one notification both sent it;
- ten retries of one FAILED notification each wrote `attempts + 1` from the same value, so ten retries consumed one attempt.

## Decision
`NotificationRepository.updateStatus(id, expectedStatus, expectedAttempts, status, attempts, lastError)` writes with the filter
`{_id, status: expectedStatus, attempts: expectedAttempts}`. A miss is a 404 (`NotificationNotFoundException`) when the document does not exist
and a 409 (`ERR-NTF-00409`) when it exists but is no longer what the caller read (ADR-019).
The use cases pass the status and attempts they read, taken before the transition is applied.

Attempts are part of the guard because retry is allowed from SENDING as well as FAILED (ADR-023): the status alone would let two retries
of a stuck SENDING notification both win while consuming a single attempt.

`DispatchNotificationUseCase` now reserves the notification (PENDING -> SENDING, guarded) **before** it calls the channel, and the reservation is
outside the error handler that marks the notification FAILED: the loser of the race sends nothing and marks nothing; it gets the 409.
Only the winner reaches the channel, so one notification is sent once per reservation.

## Consequences
- A stale write is refused instead of silently overwriting the winner. Callers that get 409 read the notification again.
- The channel call is still outside any transaction: a crash between reservation and delivery leaves the notification SENDING, which is the case
  `retry` from SENDING already covers (ADR-023). Nothing new to clean up.
- `TransitionRaceIT` proves the three cases above against a real MongoDB; each failed on the previous code.
