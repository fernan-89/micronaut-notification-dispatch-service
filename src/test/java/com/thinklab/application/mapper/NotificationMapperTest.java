package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiateNotificationRequest;
import com.thinklab.application.dto.response.NotificationResponse;
import com.thinklab.domain.model.Notification;
import com.thinklab.domain.model.Notification.NotificationChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationMapperTest {

    @Test
    @DisplayName("toDomain builds a PENDING aggregate from the request and sovereign ID")
    void toDomain() {
        UUID id = UUID.randomUUID();
        UUID organisationId = UUID.randomUUID();
        InitiateNotificationRequest request = new InitiateNotificationRequest("ada@thinklab.com", "Welcome", "Hello", NotificationChannel.LOG);

        Notification notification = NotificationMapper.toDomain(request, id, organisationId);

        assertEquals(id, notification.getId());
        assertEquals(organisationId, notification.getOrganisationId());
        assertEquals("ada@thinklab.com", notification.getRecipient());
    }

    @Test
    @DisplayName("toResponse exposes every field as a DTO")
    void toResponse() {
        UUID id = UUID.randomUUID();
        UUID organisationId = UUID.randomUUID();
        Notification notification = Notification.createNew(id, organisationId, "ada@thinklab.com", "Welcome", "Hello", NotificationChannel.LOG);

        NotificationResponse response = NotificationMapper.toResponse(notification);

        assertEquals(id, response.id());
        assertEquals(organisationId, response.organisationId());
        assertEquals("ada@thinklab.com", response.recipient());
        assertEquals("Welcome", response.subject());
        assertEquals("Hello", response.body());
        assertEquals("LOG", response.channel());
        assertEquals("PENDING", response.status());
        assertEquals(0, response.attempts());
        assertEquals(Notification.DEFAULT_MAX_ATTEMPTS, response.maxAttempts());
    }

    @Test
    @DisplayName("Static mapper class cannot be instantiated")
    void cannotInstantiate() throws Exception {
        var constructor = NotificationMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var exception = assertThrows(java.lang.reflect.InvocationTargetException.class, constructor::newInstance);
        assertEquals(UnsupportedOperationException.class, exception.getCause().getClass());
    }
}
