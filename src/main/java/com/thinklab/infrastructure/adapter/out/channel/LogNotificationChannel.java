package com.thinklab.infrastructure.adapter.out.channel;

import com.thinklab.domain.model.Notification;
import com.thinklab.domain.port.NotificationChannelPort;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Always-on delivery channel: writes a structured log line. This is the channel used by the v1 pilot
 * (welcome notification on {@code user.initiated}) since it needs no external infrastructure to be
 * real and testable end to end (ADR-023).
 */
@Singleton
public class LogNotificationChannel implements NotificationChannelPort {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationChannel.class);

    @Override
    public Notification.NotificationChannel type() {
        return Notification.NotificationChannel.LOG;
    }

    @Override
    public Mono<Void> send(Notification notification) {
        return Mono.fromRunnable(() -> log.info("[NOTIFICATION] to={} subject={} body={}",
                notification.getRecipient(), notification.getSubject(), notification.getBody()));
    }
}
