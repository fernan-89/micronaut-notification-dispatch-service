package com.thinklab.infrastructure.adapter.out.channel;

import com.thinklab.domain.model.Notification;
import com.thinklab.domain.port.NotificationChannelPort;
import io.micronaut.context.annotation.Requires;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Objects;
import java.util.UUID;

/**
 * Opt-in delivery channel: POSTs a JSON body to a single, globally-configured webhook URL
 * ({@code thinklab.notifications.webhook.url}) — not one URL per notification. {@code recipient} travels
 * inside the body (ADR-023).
 */
@Singleton
@Requires(property = "thinklab.notifications.webhook.enabled", value = "true")
public class WebhookNotificationChannel implements NotificationChannelPort {

    private final WebhookProperties properties;
    private final ObjectMapper objectMapper;
    private final WebhookTransport transport;

    @Inject
    public WebhookNotificationChannel(WebhookProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, new WebhookTransport.Default());
    }

    /** Test seam: the blocking transport. */
    WebhookNotificationChannel(WebhookProperties properties, ObjectMapper objectMapper, WebhookTransport transport) {
        this.properties = Objects.requireNonNull(properties, "Infrastructure constraint violated: WebhookProperties cannot be null.");
        this.objectMapper = Objects.requireNonNull(objectMapper, "Infrastructure constraint violated: ObjectMapper cannot be null.");
        this.transport = Objects.requireNonNull(transport, "Infrastructure constraint violated: WebhookTransport cannot be null.");
    }

    @Override
    public Notification.NotificationChannel type() {
        return Notification.NotificationChannel.WEBHOOK;
    }

    @Override
    public Mono<Void> send(Notification notification) {
        Objects.requireNonNull(notification, "Infrastructure constraint violated: Notification cannot be null.");
        return Mono.fromCallable(() -> {
                    String json = objectMapper.writeValueAsString(WebhookPayload.from(notification));
                    transport.postJson(properties.getUrl(), json);
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Serdeable
    record WebhookPayload(UUID id, UUID organisationId, String recipient, String subject, String body) {
        static WebhookPayload from(Notification notification) {
            return new WebhookPayload(notification.getId(), notification.getOrganisationId(),
                    notification.getRecipient(), notification.getSubject(), notification.getBody());
        }
    }
}
