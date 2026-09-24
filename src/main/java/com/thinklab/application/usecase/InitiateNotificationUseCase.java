package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateNotificationRequest;
import com.thinklab.application.dto.response.NotificationResponse;
import com.thinklab.application.mapper.NotificationMapper;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.NotificationRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Orchestrates the business flow for Notification creation (BIAN Behavior Qualifier: {@code initiate}).
 * Only creates the PENDING record; dispatching it is a separate step — see
 * {@link DispatchNotificationUseCase}.
 */
@Singleton
public class InitiateNotificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateNotificationUseCase.class);

    private final HashServicePort hashServicePort;
    private final NotificationRepository notificationRepository;

    public InitiateNotificationUseCase(HashServicePort hashServicePort, NotificationRepository notificationRepository) {
        this.hashServicePort = hashServicePort;
        this.notificationRepository = notificationRepository;
    }

    public Mono<NotificationResponse> execute(UUID organisationId, InitiateNotificationRequest request) {
        log.info("[USE CASE] Initiating notification for organisation: {} recipient: {}", organisationId, request.recipient());

        return hashServicePort.generateSovereignId("notification-creation")
                .map(sovereignId -> NotificationMapper.toDomain(request, sovereignId, organisationId))
                .flatMap(notificationRepository::create)
                .map(NotificationMapper::toResponse);
    }
}
