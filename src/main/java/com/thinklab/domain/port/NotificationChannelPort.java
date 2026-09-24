package com.thinklab.domain.port;

import com.thinklab.domain.model.Notification;
import reactor.core.publisher.Mono;

/**
 * Outbound port for actually delivering a {@link Notification} through one concrete channel.
 * Implementations register the {@link Notification.NotificationChannel} they handle; see
 * {@code LogNotificationChannel} (always on) and {@code WebhookNotificationChannel} (opt-in) for the two
 * v1 adapters (ADR-023).
 */
public interface NotificationChannelPort {

    Notification.NotificationChannel type();

    Mono<Void> send(Notification notification);
}
