package com.thinklab.infrastructure.adapter.in.event;

import com.thinklab.application.dto.event.UserInitiatedEventPayload;
import com.thinklab.application.dto.request.InitiateNotificationRequest;
import com.thinklab.application.usecase.DispatchNotificationUseCase;
import com.thinklab.application.usecase.InitiateNotificationUseCase;
import com.thinklab.domain.model.Notification.NotificationChannel;
import com.thinklab.domain.repository.ProcessedEventRepository;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Pure reaction to {@code thinklab.party-authentication.user.initiated} (kit ADR-003): creates a
 * welcome Notification and dispatches it immediately. Deliberately has zero NATS imports — the only
 * class touching {@code io.nats.client.*} is {@link JetStreamNotificationSubscriber}, which parses
 * nothing itself and only calls {@link #handle(String)}.
 */
@Singleton
public class UserInitiatedEventHandler {

    private static final Logger log = LoggerFactory.getLogger(UserInitiatedEventHandler.class);

    private final ObjectMapper objectMapper;
    private final InitiateNotificationUseCase initiateNotificationUseCase;
    private final DispatchNotificationUseCase dispatchNotificationUseCase;
    private final ProcessedEventRepository processedEvents;

    public UserInitiatedEventHandler(ObjectMapper objectMapper, InitiateNotificationUseCase initiateNotificationUseCase,
                                      DispatchNotificationUseCase dispatchNotificationUseCase,
                                      ProcessedEventRepository processedEvents) {
        this.objectMapper = objectMapper;
        this.initiateNotificationUseCase = initiateNotificationUseCase;
        this.dispatchNotificationUseCase = dispatchNotificationUseCase;
        this.processedEvents = Objects.requireNonNull(processedEvents, "Infrastructure constraint violated: ProcessedEventRepository cannot be null.");
    }

    public Mono<Void> handle(String payloadJson) {
        Objects.requireNonNull(payloadJson, "Infrastructure constraint violated: payloadJson cannot be null.");

        return Mono.fromCallable(() -> objectMapper.readValue(payloadJson, UserInitiatedEventPayload.class))
                .flatMap(this::handleOnce)
                .doOnError(e -> log.error("[EVENTS] Failed to handle user.initiated payload: {}", e.getMessage(), e));
    }

    /**
     * At-least-once delivery (kit ADR-003) means the same event can arrive again, e.g. when the ack of a
     * handled message is lost. The event is recorded only after the notification has been dispatched, so a
     * failure part-way still leaves it eligible for redelivery.
     */
    private Mono<Void> handleOnce(UserInitiatedEventPayload event) {
        String eventKey = "user.initiated:" + event.id();
        return processedEvents.isProcessed(eventKey)
                .flatMap(done -> {
                    if (done) {
                        log.info("[EVENTS] Duplicate delivery of [{}] ignored: already handled.", eventKey);
                        return Mono.<Void>empty();
                    }
                    return dispatchWelcomeNotification(event).then(Mono.defer(() -> processedEvents.markProcessed(eventKey)));
                });
    }

    private Mono<Void> dispatchWelcomeNotification(UserInitiatedEventPayload event) {
        InitiateNotificationRequest request = new InitiateNotificationRequest(
                event.email(),
                "Welcome to ThinkLab",
                String.format("Hello %s, your ThinkLab account has just been created.", event.fullName()),
                NotificationChannel.LOG
        );

        return initiateNotificationUseCase.execute(event.organisationId(), request)
                .flatMap(response -> dispatchNotificationUseCase.execute(response.id()));
    }
}
