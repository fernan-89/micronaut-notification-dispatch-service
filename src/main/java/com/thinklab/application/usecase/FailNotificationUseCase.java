package com.thinklab.application.usecase;

import com.thinklab.domain.exception.NotificationNotFoundException;
import com.thinklab.domain.repository.NotificationRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** BIAN Behavior Qualifier: {@code control/fail} — the only control action carrying an error message. */
@Singleton
public class FailNotificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(FailNotificationUseCase.class);

    private final NotificationRepository notificationRepository;

    public FailNotificationUseCase(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Mono<Void> execute(UUID id, String executor, String errorMessage) {
        log.info("[USE CASE] Failing notification ID: {} reason: {}", id, errorMessage);

        return notificationRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotificationNotFoundException(id)))
                .flatMap(notification -> {
                    notification.fail(executor, errorMessage);
                    return notificationRepository.updateStatus(id, notification.getStatus(),
                            notification.getAttempts(), notification.getLastError());
                });
    }
}
