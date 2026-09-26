package com.thinklab.infrastructure.adapter.out.persistence;

import com.mongodb.client.model.Filters;
import com.mongodb.reactivestreams.client.MongoClient;
import com.thinklab.Infrastructure;
import com.thinklab.domain.exception.NotificationNotFoundException;
import com.thinklab.domain.model.Notification;
import com.thinklab.domain.model.Notification.NotificationChannel;
import com.thinklab.domain.model.Notification.NotificationStatus;
import com.thinklab.domain.repository.NotificationRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Notifications through {@link NotificationRepository} against a real MongoDB. */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationPersistenceIT implements TestPropertyProvider {

    private static final String DATABASE = "notification_persistence_it";

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", Infrastructure.mongoUri(DATABASE));
    }

    @Inject
    NotificationRepository notifications;

    @Inject
    MongoClient mongoClient;

    private Notification newNotification(UUID organisationId) {
        return notifications.create(Notification.createNew(UUID.randomUUID(), organisationId, "ada@thinklab.com",
                "Welcome", "Hello", NotificationChannel.LOG)).block();
    }

    @Test
    @DisplayName("a created notification is read back, in the database named by mongodb.uri")
    void createAndFind() {
        Notification created = newNotification(UUID.randomUUID());

        Notification found = notifications.findById(created.getId()).block();

        assertEquals("ada@thinklab.com", found.getRecipient());
        assertEquals(NotificationStatus.PENDING, found.getStatus());
        assertEquals(NotificationChannel.LOG, found.getChannel());
        Document stored = Mono.from(mongoClient.getDatabase(DATABASE).getCollection("notifications")
                .find(Filters.eq("_id", created.getId())).first()).block();
        assertNotNull(stored, "notification not found in " + DATABASE);
    }

    @Test
    @DisplayName("a status update records attempts and the last error, and the status filters the tenant listing")
    void statusAndListing() {
        UUID organisation = UUID.randomUUID();
        Notification failed = newNotification(organisation);
        Notification pending = newNotification(organisation);
        newNotification(UUID.randomUUID());

        notifications.updateStatus(failed.getId(), NotificationStatus.FAILED, 3, "smtp timeout").block();

        Notification found = notifications.findById(failed.getId()).block();
        assertEquals(NotificationStatus.FAILED, found.getStatus());
        assertEquals(3, found.getAttempts());
        assertEquals("smtp timeout", found.getLastError());
        assertEquals(Set.of(failed.getId(), pending.getId()),
                notifications.findAllByOrganisationId(organisation, null).map(Notification::getId).collect(Collectors.toSet()).block());
        assertEquals(Set.of(pending.getId()),
                notifications.findAllByOrganisationId(organisation, NotificationStatus.PENDING).map(Notification::getId).collect(Collectors.toSet()).block());
    }

    @Test
    @DisplayName("an unknown notification is empty on read and NotificationNotFoundException on update")
    void notFound() {
        UUID unknown = UUID.randomUUID();

        assertNull(notifications.findById(unknown).block());
        assertThrows(NotificationNotFoundException.class,
                () -> notifications.updateStatus(unknown, NotificationStatus.CANCELLED, 0, null).block());
    }
}
