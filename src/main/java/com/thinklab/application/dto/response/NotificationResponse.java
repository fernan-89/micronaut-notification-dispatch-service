package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for Notification output payload (notification-dispatch Control Record). Enforces the DTO
 * Isolation Pattern by preventing the pure Domain Model from bleeding out to the HTTP boundary.
 */
@Serdeable
public record NotificationResponse(
        UUID id,
        UUID organisationId,
        String recipient,
        String subject,
        String body,
        String channel,
        String status,
        int attempts,
        int maxAttempts,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {}
