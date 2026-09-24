package com.thinklab.application.usecase;

import com.thinklab.domain.exception.NotificationNotFoundException;
import com.thinklab.domain.model.Notification;
import com.thinklab.domain.repository.NotificationRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case governing the Notification lifecycle for every control action that needs nothing beyond an
 * executor (BIAN Behavior Qualifier: {@code control/send|deliver|cancel|retry}). {@code control/fail} is
 * a separate use case ({@link FailNotificationUseCase}) because it carries an error message.
 *
 * <p><b>State Machine Enforcement:</b> loads the aggregate first, delegates the transition to the domain
 * model (which throws {@link com.thinklab.domain.exception.InvalidNotificationStatusException} — HTTP
 * 409 — on an illegal move) and only then issues the granular persistence update.
 */
@Singleton
public class ControlNotificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(ControlNotificationUseCase.class);

    private final NotificationRepository notificationRepository;

    public ControlNotificationUseCase(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Mono<Void> execute(UUID id, Action action, String executor) {
        log.info("[USE CASE] Controlling notification lifecycle: {} for ID: {}", action, id);

        return notificationRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotificationNotFoundException(id)))
                .flatMap(notification -> {
                    action.apply(notification, executor);
                    return notificationRepository.updateStatus(id, notification.getStatus(),
                            notification.getAttempts(), notification.getLastError());
                });
    }

    public enum Action {
        SEND {
            @Override void apply(Notification notification, String executor) { notification.send(executor); }
        },
        DELIVER {
            @Override void apply(Notification notification, String executor) { notification.deliver(executor); }
        },
        CANCEL {
            @Override void apply(Notification notification, String executor) { notification.cancel(executor); }
        },
        RETRY {
            @Override void apply(Notification notification, String executor) { notification.retry(executor); }
        };

        abstract void apply(Notification notification, String executor);
    }
}
