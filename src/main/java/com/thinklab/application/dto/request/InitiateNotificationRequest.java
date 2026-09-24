package com.thinklab.application.dto.request;

import com.thinklab.domain.model.Notification.NotificationChannel;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO for Notification creation (BIAN Behavior Qualifier: {@code initiate}). Protective barrier to the
 * Domain Layer. organisationId travels via the {@code X-Tenant-Id} header, not the body.
 */
@Serdeable
public record InitiateNotificationRequest(

        @NotBlank(message = "Recipient is required")
        @Size(max = 320, message = "Recipient must not exceed 320 characters")
        String recipient,

        @NotBlank(message = "Subject is required")
        @Size(max = 200, message = "Subject must not exceed 200 characters")
        String subject,

        @NotBlank(message = "Body is required")
        @Size(max = 4000, message = "Body must not exceed 4000 characters")
        String body,

        @NotNull(message = "Channel is required")
        NotificationChannel channel
) {}
