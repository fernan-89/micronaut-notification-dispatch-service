package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.FailNotificationRequest;
import com.thinklab.application.dto.request.InitiateNotificationRequest;
import com.thinklab.application.dto.response.NotificationResponse;
import com.thinklab.application.usecase.ControlNotificationUseCase;
import com.thinklab.application.usecase.FailNotificationUseCase;
import com.thinklab.application.usecase.InitiateNotificationUseCase;
import com.thinklab.application.usecase.RetrieveNotificationUseCase;
import com.thinklab.application.usecase.RetrieveNotificationsUseCase;
import com.thinklab.domain.exception.InvalidNotificationStatusException;
import com.thinklab.domain.exception.NotificationNotFoundException;
import com.thinklab.domain.model.Notification.NotificationChannel;
import com.thinklab.domain.model.Notification.NotificationStatus;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private static final String EXECUTOR = "ops-admin";

    @Mock private InitiateNotificationUseCase initiateNotificationUseCase;
    @Mock private RetrieveNotificationUseCase retrieveNotificationUseCase;
    @Mock private RetrieveNotificationsUseCase retrieveNotificationsUseCase;
    @Mock private ControlNotificationUseCase controlNotificationUseCase;
    @Mock private FailNotificationUseCase failNotificationUseCase;

    @InjectMocks private NotificationController controller;

    private UUID organisationId;
    private UUID notificationId;
    private NotificationResponse sample;

    @BeforeEach
    void setUp() {
        organisationId = UUID.randomUUID();
        notificationId = UUID.randomUUID();
        sample = new NotificationResponse(notificationId, organisationId, "ada@thinklab.com", "Welcome", "Hello",
                "LOG", "PENDING", 0, 5, null, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("initiate returns 201 Created and scopes the notification to the tenant header")
    void initiate() {
        InitiateNotificationRequest request = new InitiateNotificationRequest("ada@thinklab.com", "Welcome", "Hello", NotificationChannel.LOG);
        when(initiateNotificationUseCase.execute(organisationId, request)).thenReturn(Mono.just(sample));

        StepVerifier.create(controller.initiate(organisationId.toString(), request))
                .assertNext(response -> {
                    assertEquals(HttpStatus.CREATED, response.getStatus());
                    assertEquals(sample, response.body());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("retrieveById returns 200 OK")
    void retrieveById() {
        when(retrieveNotificationUseCase.execute(notificationId)).thenReturn(Mono.just(sample));

        StepVerifier.create(controller.retrieveById(notificationId))
                .assertNext(response -> assertEquals(HttpStatus.OK, response.getStatus()))
                .verifyComplete();
    }

    @Test
    @DisplayName("retrieveById propagates a 404 from the use case")
    void retrieveByIdNotFound() {
        when(retrieveNotificationUseCase.execute(notificationId)).thenReturn(Mono.error(new NotificationNotFoundException(notificationId)));

        StepVerifier.create(controller.retrieveById(notificationId))
                .expectError(NotificationNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("retrieveAll forwards the status filter")
    void retrieveAll() {
        when(retrieveNotificationsUseCase.execute(organisationId, NotificationStatus.DELIVERED)).thenReturn(Flux.just(sample));

        StepVerifier.create(controller.retrieveAll(organisationId.toString(), NotificationStatus.DELIVERED))
                .assertNext(list -> assertEquals(List.of(sample), list))
                .verifyComplete();
    }

    @Test
    @DisplayName("each control endpoint dispatches its action and returns 204")
    void controlEndpoints() {
        when(controlNotificationUseCase.execute(eq(notificationId), any(ControlNotificationUseCase.Action.class), eq(EXECUTOR)))
                .thenReturn(Mono.empty());

        StepVerifier.create(controller.controlSend(notificationId, EXECUTOR))
                .assertNext(r -> assertEquals(HttpStatus.NO_CONTENT, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.controlDeliver(notificationId, EXECUTOR))
                .assertNext(r -> assertEquals(HttpStatus.NO_CONTENT, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.controlRetry(notificationId, EXECUTOR))
                .assertNext(r -> assertEquals(HttpStatus.NO_CONTENT, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.controlCancel(notificationId, EXECUTOR))
                .assertNext(r -> assertEquals(HttpStatus.NO_CONTENT, r.getStatus())).verifyComplete();

        verify(controlNotificationUseCase).execute(notificationId, ControlNotificationUseCase.Action.SEND, EXECUTOR);
        verify(controlNotificationUseCase).execute(notificationId, ControlNotificationUseCase.Action.DELIVER, EXECUTOR);
        verify(controlNotificationUseCase).execute(notificationId, ControlNotificationUseCase.Action.RETRY, EXECUTOR);
        verify(controlNotificationUseCase).execute(notificationId, ControlNotificationUseCase.Action.CANCEL, EXECUTOR);
    }

    @Test
    @DisplayName("a control endpoint surfaces an illegal transition")
    void controlIllegalTransition() {
        when(controlNotificationUseCase.execute(notificationId, ControlNotificationUseCase.Action.DELIVER, EXECUTOR))
                .thenReturn(Mono.error(new InvalidNotificationStatusException("illegal")));

        StepVerifier.create(controller.controlDeliver(notificationId, EXECUTOR))
                .expectError(InvalidNotificationStatusException.class)
                .verify();
    }

    @Test
    @DisplayName("controlFail returns 204 No Content")
    void controlFail() {
        FailNotificationRequest request = new FailNotificationRequest("smtp down");
        when(failNotificationUseCase.execute(notificationId, EXECUTOR, "smtp down")).thenReturn(Mono.empty());

        StepVerifier.create(controller.controlFail(notificationId, EXECUTOR, request))
                .assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus()))
                .verifyComplete();
    }
}
