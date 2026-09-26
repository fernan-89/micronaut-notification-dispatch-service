package com.thinklab.infrastructure.adapter.in.event;

import com.thinklab.Infrastructure;
import com.thinklab.domain.model.Notification;
import com.thinklab.domain.model.Notification.NotificationStatus;
import com.thinklab.domain.repository.NotificationRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import io.nats.client.Connection;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The event-driven path end to end, against a fresh NATS JetStream and a real MongoDB: a
 * {@code user.initiated} event published to the stream becomes a delivered welcome notification.
 *
 * <p>Fresh broker on purpose: the stream does not exist until the kit's {@code NatsStreamInitializer}
 * creates it at startup, which is exactly the situation a first deployment starts from.
 */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserInitiatedConsumptionIT implements TestPropertyProvider {

    @Override
    public Map<String, String> getProperties() {
        return Map.of(
                "mongodb.uri", Infrastructure.mongoUri("notification_consumption_it"),
                "thinklab.events.enabled", "true",
                "thinklab.events.nats-url", Infrastructure.natsUrl());
    }

    @Inject
    Connection connection;

    @Inject
    NotificationRepository notifications;

    private List<Notification> awaitNotifications(UUID organisationId, int expected) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        List<Notification> found = List.of();
        while (Instant.now().isBefore(deadline)) {
            found = notifications.findAllByOrganisationId(organisationId, null).collectList().block();
            if (found.size() >= expected && found.stream().allMatch(n -> n.getStatus() == NotificationStatus.DELIVERED)) {
                return found;
            }
            Thread.sleep(200);
        }
        return found;
    }

    private void publishUserInitiated(UUID userId, UUID organisationId, String email) throws Exception {
        String payload = String.format(
                "{\"id\":\"%s\",\"organisationId\":\"%s\",\"email\":\"%s\",\"fullName\":\"Ada Lovelace\",\"occurredAt\":\"%s\"}",
                userId, organisationId, email, Instant.now());
        connection.jetStream().publish("thinklab.party-authentication.user.initiated", payload.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a user.initiated event becomes one delivered welcome notification for that user")
    void userInitiatedBecomesAWelcomeNotification() throws Exception {
        UUID organisation = UUID.randomUUID();

        publishUserInitiated(UUID.randomUUID(), organisation, "ada@thinklab.com");

        List<Notification> delivered = awaitNotifications(organisation, 1);
        assertEquals(1, delivered.size(), () -> "notifications: " + delivered);
        Notification welcome = delivered.get(0);
        assertEquals("ada@thinklab.com", welcome.getRecipient());
        assertEquals("Welcome to ThinkLab", welcome.getSubject());
        assertEquals(NotificationStatus.DELIVERED, welcome.getStatus());
    }

    @Test
    @DisplayName("a redelivered user.initiated event does not produce a second welcome notification (at-least-once, ADR-003)")
    void redeliveryIsIdempotent() throws Exception {
        UUID organisation = UUID.randomUUID();
        UUID user = UUID.randomUUID();

        publishUserInitiated(user, organisation, "grace@thinklab.com");
        List<Notification> first = awaitNotifications(organisation, 1);
        publishUserInitiated(user, organisation, "grace@thinklab.com");
        Thread.sleep(3000);

        assertEquals(1, first.size());
        assertEquals(1L, notifications.findAllByOrganisationId(organisation, null).count().block());
    }
}
