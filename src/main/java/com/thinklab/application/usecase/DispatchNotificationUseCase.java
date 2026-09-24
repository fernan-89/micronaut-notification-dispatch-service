package com.thinklab.application.usecase;

import com.thinklab.domain.exception.NotificationNotFoundException;
import com.thinklab.domain.model.Notification;
import com.thinklab.domain.port.NotificationChannelPort;
import com.thinklab.domain.repository.NotificationRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Automatically drives a Notification through PENDING -&gt; SENDING -&gt; (DELIVERED | FAILED) in one
 * reactive chain. This is the only place that path runs automatically — the {@code control/*} REST
 * endpoints ({@link ControlNotificationUseCase}, {@link FailNotificationUseCase}) exist for manual
 * ops/Postman/tests, not as the internal dispatch mechanism (ADR-023).
 */
@Singleton
public class DispatchNotificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(DispatchNotificationUseCase.class);
    private static final String SYSTEM_EXECUTOR = "system:notification-dispatch";

    private final NotificationRepository notificationRepository;
    private final List<NotificationChannelPort> channels;

    public DispatchNotificationUseCase(NotificationRepository notificationRepository, List<NotificationChannelPort> channels) {
        this.notificationRepository = notificationRepository;
        this.channels = channels;
    }

    public Mono<Void> execute(UUID id) {
        log.info("[USE CASE] Dispatching notification ID: {}", id);

        return notificationRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotificationNotFoundException(id)))
                .flatMap(notification -> {
                    notification.send(SYSTEM_EXECUTOR);
                    return notificationRepository.updateStatus(id, notification.getStatus(),
                                    notification.getAttempts(), notification.getLastError())
                            .then(Mono.defer(() -> resolveChannel(notification.getChannel()).send(notification)))
                            .then(Mono.defer(() -> markDelivered(id, notification)))
                            .onErrorResume(e -> markFailed(id, notification, e.getMessage()));
                });
    }

    private Mono<Void> markDelivered(UUID id, Notification notification) {
        notification.deliver(SYSTEM_EXECUTOR);
        return notificationRepository.updateStatus(id, notification.getStatus(), notification.getAttempts(), notification.getLastError());
    }

    private Mono<Void> markFailed(UUID id, Notification notification, String errorMessage) {
        notification.fail(SYSTEM_EXECUTOR, errorMessage);
        return notificationRepository.updateStatus(id, notification.getStatus(), notification.getAttempts(), notification.getLastError());
    }

    private NotificationChannelPort resolveChannel(Notification.NotificationChannel type) {
        return channels.stream()
                .filter(channel -> channel.type() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No channel registered for [" + type + "]."));
    }
}
