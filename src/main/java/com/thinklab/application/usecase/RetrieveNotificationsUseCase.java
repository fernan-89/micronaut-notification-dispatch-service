package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.NotificationResponse;
import com.thinklab.application.mapper.NotificationMapper;
import com.thinklab.domain.model.Notification.NotificationStatus;
import com.thinklab.domain.repository.NotificationRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

import java.util.UUID;

/** BIAN Behavior Qualifier: {@code retrieve} (collection), tenant-scoped with an optional status filter. */
@Singleton
public class RetrieveNotificationsUseCase {

    private final NotificationRepository notificationRepository;

    public RetrieveNotificationsUseCase(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Flux<NotificationResponse> execute(UUID organisationId, NotificationStatus status) {
        return notificationRepository.findAllByOrganisationId(organisationId, status)
                .map(NotificationMapper::toResponse);
    }
}
