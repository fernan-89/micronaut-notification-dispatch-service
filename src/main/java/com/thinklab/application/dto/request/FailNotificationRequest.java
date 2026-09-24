package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTO for {@code control/fail} — the only control action that carries a body. */
@Serdeable
public record FailNotificationRequest(

        @NotBlank(message = "errorMessage is required")
        @Size(max = 1000, message = "errorMessage must not exceed 1000 characters")
        String errorMessage
) {}
