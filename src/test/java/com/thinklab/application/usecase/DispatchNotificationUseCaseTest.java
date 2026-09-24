package com.thinklab.application.usecase;

import com.thinklab.domain.exception.NotificationNotFoundException;
import com.thinklab.domain.model.Notification;
import com.thinklab.domain.model.Notification.NotificationChannel;
import com.thinklab.domain.model.Notification.NotificationStatus;
import com.thinklab.domain.port.NotificationChannelPort;
import com.thinklab.domain.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispatchNotificationUseCaseTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationChannelPort logChannel;
    @Mock private NotificationChannelPort webhookChannel;

    private UUID id;
    private UUID organisationId;
    private Notification notification;

    @BeforeEach
    void setUp() {
        id = UUID.randomUUID();
        organisationId = UUID.randomUUID();
        notification = Notification.createNew(id, organisationId, "ada@thinklab.com", "Welcome", "Hello", NotificationChannel.LOG);
    }

    @Test
    @DisplayName("A successful send transitions PENDING -> SENDING -> DELIVERED and persists both hops")
    void successfulDispatch() {
        when(logChannel.type()).thenReturn(NotificationChannel.LOG);
        when(notificationRepository.findById(id)).thenReturn(Mono.just(notification));
        when(notificationRepository.updateStatus(eq(id), any(), anyInt(), any())).thenReturn(Mono.empty());
        when(logChannel.send(notification)).thenReturn(Mono.empty());

        DispatchNotificationUseCase useCase = new DispatchNotificationUseCase(notificationRepository, List.of(logChannel));

        StepVerifier.create(useCase.execute(id)).verifyComplete();

        assertEquals(NotificationStatus.DELIVERED, notification.getStatus());
    }

    @Test
    @DisplayName("A channel failure marks the notification FAILED with the channel's error message, never propagating the error")
    void channelFailureMarksFailed() {
        when(logChannel.type()).thenReturn(NotificationChannel.LOG);
        when(notificationRepository.findById(id)).thenReturn(Mono.just(notification));
        when(notificationRepository.updateStatus(eq(id), any(), anyInt(), any())).thenReturn(Mono.empty());
        when(logChannel.send(notification)).thenReturn(Mono.error(new IllegalStateException("smtp down")));

        DispatchNotificationUseCase useCase = new DispatchNotificationUseCase(notificationRepository, List.of(logChannel));

        StepVerifier.create(useCase.execute(id)).verifyComplete();

        assertEquals(NotificationStatus.FAILED, notification.getStatus());
        assertEquals("smtp down", notification.getLastError());
    }

    @Test
    @DisplayName("No channel registered for the requested type is treated the same as a channel failure")
    void noChannelRegisteredMarksFailed() {
        when(webhookChannel.type()).thenReturn(NotificationChannel.WEBHOOK);
        when(notificationRepository.findById(id)).thenReturn(Mono.just(notification));
        when(notificationRepository.updateStatus(eq(id), any(), anyInt(), any())).thenReturn(Mono.empty());

        // notification requests LOG but only a WEBHOOK channel bean exists
        DispatchNotificationUseCase useCase = new DispatchNotificationUseCase(notificationRepository, List.of(webhookChannel));

        StepVerifier.create(useCase.execute(id)).verifyComplete();

        assertEquals(NotificationStatus.FAILED, notification.getStatus());
        assertEquals("No channel registered for [LOG].", notification.getLastError());
    }

    @Test
    @DisplayName("404 when the notification does not exist")
    void notFound() {
        when(notificationRepository.findById(id)).thenReturn(Mono.empty());

        DispatchNotificationUseCase useCase = new DispatchNotificationUseCase(notificationRepository, List.of(logChannel));

        StepVerifier.create(useCase.execute(id))
                .expectError(NotificationNotFoundException.class)
                .verify();
    }
}
