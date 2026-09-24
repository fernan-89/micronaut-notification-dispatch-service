package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.Notification;
import com.thinklab.domain.model.Notification.NotificationChannel;
import com.thinklab.domain.model.Notification.NotificationStatus;
import com.thinklab.infrastructure.adapter.out.persistence.entity.NotificationDocument.NotificationPersistenceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationDocumentTest {

    @Test
    @DisplayName("toDocument / toDomain round-trips the aggregate")
    void roundTrip() {
        UUID id = UUID.randomUUID();
        UUID organisationId = UUID.randomUUID();
        Notification notification = Notification.reconstitute(id, organisationId, "ada@thinklab.com", "Welcome", "Hello",
                NotificationChannel.WEBHOOK, NotificationStatus.FAILED, 2, 7, "boom",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z"));

        NotificationDocument document = NotificationPersistenceMapper.toDocument(notification);
        Notification restored = NotificationPersistenceMapper.toDomain(document);

        assertEquals(id, document.getId());
        assertEquals(organisationId, document.getOrganisationId());
        assertEquals("ada@thinklab.com", document.getRecipient());
        assertEquals("Welcome", document.getSubject());
        assertEquals("Hello", document.getBody());
        assertEquals("WEBHOOK", document.getChannel());
        assertEquals("FAILED", document.getStatus());
        assertEquals(2, document.getAttempts());
        assertEquals(7, document.getMaxAttempts());
        assertEquals("boom", document.getLastError());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), document.getCreatedAt());
        assertEquals(Instant.parse("2026-02-01T00:00:00Z"), document.getUpdatedAt());

        assertEquals(id, restored.getId());
        assertEquals(NotificationStatus.FAILED, restored.getStatus());
        assertEquals(2, restored.getAttempts());
        assertEquals("boom", restored.getLastError());
    }

    @Test
    @DisplayName("toDomain defaults a missing status to PENDING")
    void toDomainDefaultsMissingStatus() {
        NotificationDocument document = new NotificationDocument();
        document.setId(UUID.randomUUID());
        document.setOrganisationId(UUID.randomUUID());
        document.setRecipient("ada@thinklab.com");
        document.setChannel("LOG");

        Notification restored = NotificationPersistenceMapper.toDomain(document);

        assertEquals(NotificationStatus.PENDING, restored.getStatus());
    }

    @Test
    @DisplayName("Static mapper class cannot be instantiated")
    void cannotInstantiate() throws NoSuchMethodException {
        Constructor<NotificationPersistenceMapper> constructor = NotificationPersistenceMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));

        InvocationTargetException exception = org.junit.jupiter.api.Assertions.assertThrows(
                InvocationTargetException.class, constructor::newInstance);
        assertEquals(UnsupportedOperationException.class, exception.getCause().getClass());
    }
}
