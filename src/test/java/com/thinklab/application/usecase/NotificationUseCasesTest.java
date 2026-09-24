package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateNotificationRequest;
import com.thinklab.domain.exception.InvalidNotificationStatusException;
import com.thinklab.domain.exception.NotificationNotFoundException;
import com.thinklab.domain.model.Notification;
import com.thinklab.domain.model.Notification.NotificationChannel;
import com.thinklab.domain.model.Notification.NotificationStatus;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationUseCasesTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private HashServicePort hashServicePort;

    private UUID id;
    private UUID organisationId;
    private Notification notification;

    @BeforeEach
    void setUp() {
        id = UUID.randomUUID();
        organisationId = UUID.randomUUID();
        notification = Notification.createNew(id, organisationId, "ada@thinklab.com", "Welcome", "Hello", NotificationChannel.LOG);
    }

    // ---------------------------------------------------------------- InitiateNotificationUseCase

    @Test
    @DisplayName("Initiate: requests a sovereign ID and persists a PENDING aggregate")
    void initiate() {
        UUID sovereign = UUID.randomUUID();
        when(hashServicePort.generateSovereignId("notification-creation")).thenReturn(Mono.just(sovereign));
        when(notificationRepository.create(any(Notification.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        InitiateNotificationUseCase useCase = new InitiateNotificationUseCase(hashServicePort, notificationRepository);
        InitiateNotificationRequest request = new InitiateNotificationRequest("ada@thinklab.com", "Welcome", "Hello", NotificationChannel.LOG);

        StepVerifier.create(useCase.execute(organisationId, request))
                .assertNext(res -> {
                    assertEquals(sovereign, res.id());
                    assertEquals("PENDING", res.status());
                })
                .verifyComplete();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).create(captor.capture());
        assertEquals("ada@thinklab.com", captor.getValue().getRecipient());
    }

    // ---------------------------------------------------------------- RetrieveNotificationUseCase

    @Test
    @DisplayName("Retrieve: maps the found aggregate to a response")
    void retrieveFound() {
        when(notificationRepository.findById(id)).thenReturn(Mono.just(notification));

        StepVerifier.create(new RetrieveNotificationUseCase(notificationRepository).execute(id))
                .assertNext(res -> assertEquals(id, res.id()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Retrieve: an unknown ID is 404")
    void retrieveNotFound() {
        when(notificationRepository.findById(id)).thenReturn(Mono.empty());

        StepVerifier.create(new RetrieveNotificationUseCase(notificationRepository).execute(id))
                .expectError(NotificationNotFoundException.class)
                .verify();
    }

    // ---------------------------------------------------------------- RetrieveNotificationsUseCase

    @Test
    @DisplayName("Retrieve collection: forwards the status filter")
    void retrieveCollection() {
        when(notificationRepository.findAllByOrganisationId(organisationId, NotificationStatus.DELIVERED))
                .thenReturn(Flux.just(notification));

        StepVerifier.create(new RetrieveNotificationsUseCase(notificationRepository).execute(organisationId, NotificationStatus.DELIVERED))
                .expectNextCount(1)
                .verifyComplete();
    }

    // ---------------------------------------------------------------- ControlNotificationUseCase

    @Test
    @DisplayName("Control: SEND drives the matching persistence update")
    void controlSend() {
        when(notificationRepository.findById(id)).thenReturn(Mono.just(notification));
        when(notificationRepository.updateStatus(eq(id), eq(NotificationStatus.SENDING), eq(0), eq(null))).thenReturn(Mono.empty());

        StepVerifier.create(new ControlNotificationUseCase(notificationRepository).execute(id, ControlNotificationUseCase.Action.SEND, "exec"))
                .verifyComplete();

        verify(notificationRepository).updateStatus(id, NotificationStatus.SENDING, 0, null);
    }

    @Test
    @DisplayName("Control: DELIVER, CANCEL and RETRY each map to their target status")
    void controlEveryAction() {
        // DELIVER
        Notification sending = Notification.createNew(id, organisationId, "r", "s", "b", NotificationChannel.LOG);
        sending.send("exec");
        when(notificationRepository.findById(id)).thenReturn(Mono.just(sending));
        when(notificationRepository.updateStatus(eq(id), eq(NotificationStatus.DELIVERED), eq(0), eq(null))).thenReturn(Mono.empty());
        StepVerifier.create(new ControlNotificationUseCase(notificationRepository).execute(id, ControlNotificationUseCase.Action.DELIVER, "exec"))
                .verifyComplete();

        // CANCEL
        Notification pending = Notification.createNew(id, organisationId, "r", "s", "b", NotificationChannel.LOG);
        when(notificationRepository.findById(id)).thenReturn(Mono.just(pending));
        when(notificationRepository.updateStatus(eq(id), eq(NotificationStatus.CANCELLED), eq(0), eq(null))).thenReturn(Mono.empty());
        StepVerifier.create(new ControlNotificationUseCase(notificationRepository).execute(id, ControlNotificationUseCase.Action.CANCEL, "exec"))
                .verifyComplete();

        // RETRY
        Notification failed = Notification.createNew(id, organisationId, "r", "s", "b", NotificationChannel.LOG);
        failed.send("exec");
        failed.fail("exec", "boom");
        when(notificationRepository.findById(id)).thenReturn(Mono.just(failed));
        when(notificationRepository.updateStatus(eq(id), eq(NotificationStatus.SENDING), eq(1), eq("boom"))).thenReturn(Mono.empty());
        StepVerifier.create(new ControlNotificationUseCase(notificationRepository).execute(id, ControlNotificationUseCase.Action.RETRY, "exec"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Control: 404 when the notification does not exist")
    void controlNotFound() {
        when(notificationRepository.findById(id)).thenReturn(Mono.empty());

        StepVerifier.create(new ControlNotificationUseCase(notificationRepository).execute(id, ControlNotificationUseCase.Action.SEND, "exec"))
                .expectError(NotificationNotFoundException.class)
                .verify();

        verify(notificationRepository, never()).updateStatus(any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("Control: an illegal move never reaches the repository update")
    void controlIllegalMove() {
        when(notificationRepository.findById(id)).thenReturn(Mono.just(notification));

        StepVerifier.create(new ControlNotificationUseCase(notificationRepository).execute(id, ControlNotificationUseCase.Action.DELIVER, "exec"))
                .expectError(InvalidNotificationStatusException.class)
                .verify();

        verify(notificationRepository, never()).updateStatus(any(), any(), anyInt(), any());
    }

    // ---------------------------------------------------------------- FailNotificationUseCase

    @Test
    @DisplayName("Fail: records the error message and persists it")
    void fail() {
        notification.send("exec");
        when(notificationRepository.findById(id)).thenReturn(Mono.just(notification));
        when(notificationRepository.updateStatus(id, NotificationStatus.FAILED, 0, "smtp down")).thenReturn(Mono.empty());

        StepVerifier.create(new FailNotificationUseCase(notificationRepository).execute(id, "exec", "smtp down"))
                .verifyComplete();

        verify(notificationRepository).updateStatus(id, NotificationStatus.FAILED, 0, "smtp down");
    }

    @Test
    @DisplayName("Fail: 404 when the notification does not exist")
    void failNotFound() {
        when(notificationRepository.findById(id)).thenReturn(Mono.empty());

        StepVerifier.create(new FailNotificationUseCase(notificationRepository).execute(id, "exec", "boom"))
                .expectError(NotificationNotFoundException.class)
                .verify();
    }
}
