package com.thinklab.infrastructure.adapter.out.channel;

import com.thinklab.domain.model.Notification;
import com.thinklab.domain.model.Notification.NotificationChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogNotificationChannelTest {

    private final LogNotificationChannel channel = new LogNotificationChannel();

    @Test
    @DisplayName("type is LOG")
    void type() {
        assertEquals(NotificationChannel.LOG, channel.type());
    }

    @Test
    @DisplayName("send completes without touching any external infrastructure")
    void send() {
        Notification notification = Notification.createNew(UUID.randomUUID(), UUID.randomUUID(), "ada@thinklab.com", "Welcome", "Hello", NotificationChannel.LOG);

        StepVerifier.create(channel.send(notification)).verifyComplete();
    }
}
