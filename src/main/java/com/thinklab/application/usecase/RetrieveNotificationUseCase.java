package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.NotificationResponse;
import com.thinklab.application.mapper.NotificationMapper;
import com.thinklab.domain.exception.NotificationNotFoundException;
import com.thinklab.domain.repository.NotificationRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** BIAN Behavior Qualifier: {@code retrieve}. */
@Singleton
public class RetrieveNotificationUseCase {

    private final NotificationRepository notificationRepository;

    public RetrieveNotificationUseCase(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Mono<NotificationResponse> execute(UUID id) {
        return notificationRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotificationNotFoundException(id)))
                .map(NotificationMapper::toResponse);
    }
}
