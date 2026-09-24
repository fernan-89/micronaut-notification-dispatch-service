package com.thinklab.infrastructure.adapter.out.channel;

import com.thinklab.domain.model.Notification;
import com.thinklab.domain.model.Notification.NotificationChannel;
import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookNotificationChannelTest {

    @Mock private WebhookTransport transport;
    @Mock private ObjectMapper objectMapper;

    private final WebhookProperties properties = new WebhookProperties();
    private final Notification notification = Notification.createNew(UUID.randomUUID(), UUID.randomUUID(),
            "ada@thinklab.com", "Welcome", "Hello", NotificationChannel.WEBHOOK);

    @Test
    @DisplayName("type is WEBHOOK")
    void type() {
        WebhookNotificationChannel channel = new WebhookNotificationChannel(properties, objectMapper, transport);

        assertEquals(NotificationChannel.WEBHOOK, channel.type());
    }

    @Test
    @DisplayName("send serializes the notification and posts it to the configured URL")
    void send() throws Exception {
        properties.setUrl("http://localhost:9999/webhook");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        WebhookNotificationChannel channel = new WebhookNotificationChannel(properties, objectMapper, transport);

        StepVerifier.create(channel.send(notification)).verifyComplete();

        verify(transport).postJson(org.mockito.ArgumentMatchers.eq("http://localhost:9999/webhook"), any(String.class));
    }

    @Test
    @DisplayName("a transport failure is propagated as an error")
    void sendFailure() throws Exception {
        properties.setUrl("http://localhost:9999/webhook");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        org.mockito.Mockito.doThrow(new java.io.IOException("down")).when(transport).postJson(any(), any());
        WebhookNotificationChannel channel = new WebhookNotificationChannel(properties, objectMapper, transport);

        StepVerifier.create(channel.send(notification)).expectError(java.io.IOException.class).verify();
    }

    @Test
    @DisplayName("a payload serialization failure is propagated as an error")
    void serializationFailure() throws Exception {
        properties.setUrl("http://localhost:9999/webhook");
        when(objectMapper.writeValueAsString(any())).thenThrow(new java.io.IOException("bad payload"));
        WebhookNotificationChannel channel = new WebhookNotificationChannel(properties, objectMapper, transport);

        StepVerifier.create(channel.send(notification)).expectError(java.io.IOException.class).verify();
    }

    @Test
    @DisplayName("the public constructor wires the real HTTP transport")
    void publicConstructorWiresRealTransport() throws Exception {
        properties.setUrl("http://127.0.0.1:1/webhook");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        WebhookNotificationChannel channel = new WebhookNotificationChannel(properties, objectMapper);

        StepVerifier.create(channel.send(notification)).expectError().verify();
    }

    @Test
    @DisplayName("mandatory collaborators and the notification are null-checked")
    void nullGuards() {
        WebhookNotificationChannel channel = new WebhookNotificationChannel(properties, objectMapper, transport);

        assertThrows(NullPointerException.class, () -> new WebhookNotificationChannel(null, objectMapper, transport));
        assertThrows(NullPointerException.class, () -> new WebhookNotificationChannel(properties, null, transport));
        assertThrows(NullPointerException.class, () -> new WebhookNotificationChannel(properties, objectMapper, null));
        assertThrows(NullPointerException.class, () -> channel.send(null));
    }
}
