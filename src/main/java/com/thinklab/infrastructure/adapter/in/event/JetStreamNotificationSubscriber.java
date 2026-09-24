package com.thinklab.infrastructure.adapter.in.event;

import com.thinklab.kit.events.EventsProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.nats.client.Connection;
import io.nats.client.ConsumerContext;
import io.nats.client.Message;
import io.nats.client.MessageConsumer;
import io.nats.client.StreamContext;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * The only class in this service that touches {@code io.nats.client.*} directly. Subscribes to
 * {@code thinklab.party-authentication.user.initiated} with a durable pull consumer
 * ({@code notification-dispatch-worker}) and hands every message straight to
 * {@link UserInitiatedEventHandler}, which has no NATS imports of its own (ADR-024).
 *
 * <p><b>No dead-letter subject in v1 (kit ADR-003, ADR-024):</b> a message that keeps failing past
 * {@link #MAX_DELIVER} attempts is terminated with {@link Message#term()} — logged, never redelivered,
 * never parked anywhere for later inspection.
 */
@Singleton
@Requires(property = "thinklab.events.enabled", value = "true")
public class JetStreamNotificationSubscriber implements ApplicationEventListener<StartupEvent> {

    private static final Logger log = LoggerFactory.getLogger(JetStreamNotificationSubscriber.class);
    private static final String CONSUMER_NAME = "notification-dispatch-worker";
    private static final long MAX_DELIVER = 5;

    private final Connection connection;
    private final EventsProperties properties;
    private final UserInitiatedEventHandler eventHandler;
    private MessageConsumer messageConsumer;

    @Inject
    public JetStreamNotificationSubscriber(Connection connection, EventsProperties properties, UserInitiatedEventHandler eventHandler) {
        this.connection = Objects.requireNonNull(connection, "Infrastructure constraint violated: Connection cannot be null.");
        this.properties = Objects.requireNonNull(properties, "Infrastructure constraint violated: EventsProperties cannot be null.");
        this.eventHandler = Objects.requireNonNull(eventHandler, "Infrastructure constraint violated: UserInitiatedEventHandler cannot be null.");
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        Objects.requireNonNull(event, "Application constraint violated: StartupEvent cannot be null.");
        try {
            String subject = properties.getSubjectPrefix() + ".party-authentication.user.initiated";
            StreamContext streamContext = connection.getStreamContext(properties.getStreamName());
            ConsumerContext consumerContext = streamContext.createOrUpdateConsumer(ConsumerConfiguration.builder()
                    .durable(CONSUMER_NAME)
                    .filterSubject(subject)
                    .ackPolicy(AckPolicy.Explicit)
                    .maxDeliver(MAX_DELIVER)
                    .build());
            this.messageConsumer = consumerContext.consume(this::onMessage);
            log.info("[EVENTS] Subscribed durable consumer [{}] to subject [{}]", CONSUMER_NAME, subject);
        } catch (Exception e) {
            log.error("[EVENTS] Could not subscribe to the event backbone; notifications triggered by events stay degraded until it recovers. Reason: {}", e.getMessage());
        }
    }

    private void onMessage(Message message) {
        String payload = new String(message.getData(), java.nio.charset.StandardCharsets.UTF_8);
        try {
            eventHandler.handle(payload).block();
            message.ack();
        } catch (Exception e) {
            if (message.metaData().deliveredCount() >= MAX_DELIVER) {
                log.error("[EVENTS] Message on subject [{}] exhausted {} delivery attempts, terminating it: {}",
                        message.getSubject(), MAX_DELIVER, e.getMessage());
                message.term();
            } else {
                log.warn("[EVENTS] Message handling failed, requesting redelivery: {}", e.getMessage());
                message.nak();
            }
        }
    }

    @PreDestroy
    void shutdown() {
        if (messageConsumer != null) {
            messageConsumer.stop();
        }
    }
}
