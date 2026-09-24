package com.thinklab.application.dto.event;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/**
 * Local copy of the {@code thinklab.party-authentication.user.initiated} event contract (kit ADR-003,
 * party-authentication's own {@code UserInitiatedEvent}). Two services independently owning the same
 * shape is deliberate for now — there is no shared event-schema module yet, and one consumer does not
 * justify building one.
 */
@Serdeable
public record UserInitiatedEventPayload(UUID id, UUID organisationId, String email, String fullName, Instant occurredAt) {
}
