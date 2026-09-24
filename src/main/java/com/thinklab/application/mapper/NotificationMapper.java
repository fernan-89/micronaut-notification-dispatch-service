package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiateNotificationRequest;
import com.thinklab.application.dto.response.NotificationResponse;
import com.thinklab.domain.model.Notification;

import java.util.UUID;

/**
 * Translates between the pure Domain Model and the DTOs crossing the HTTP boundary. Stateless, no
 * framework annotations — mirrors {@code AssetMapper} / {@code UserMapper}.
 */
public final class NotificationMapper {

    private NotificationMapper() {
        throw new UnsupportedOperationException("Static mapper class cannot be instantiated.");
    }

    public static Notification toDomain(InitiateNotificationRequest request, UUID sovereignId, UUID organisationId) {
        return Notification.createNew(sovereignId, organisationId, request.recipient(), request.subject(),
                request.body(), request.channel());
    }

    public static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getOrganisationId(),
                notification.getRecipient(),
                notification.getSubject(),
                notification.getBody(),
                notification.getChannel().name(),
                notification.getStatus().name(),
                notification.getAttempts(),
                notification.getMaxAttempts(),
                notification.getLastError(),
                notification.getCreatedAt(),
                notification.getUpdatedAt()
        );
    }
}
