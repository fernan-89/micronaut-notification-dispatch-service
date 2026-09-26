package com.thinklab.infrastructure.adapter.in.event;

import com.thinklab.application.dto.event.UserInitiatedEventPayload;
import com.thinklab.application.dto.request.InitiateNotificationRequest;
import com.thinklab.application.dto.response.NotificationResponse;
import com.thinklab.application.usecase.DispatchNotificationUseCase;
import com.thinklab.application.usecase.InitiateNotificationUseCase;
import com.thinklab.domain.repository.ProcessedEventRepository;
import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserInitiatedEventHandlerTest {

    @Mock private ObjectMapper objectMapper;
    @Mock private InitiateNotificationUseCase initiateNotificationUseCase;
    @Mock private DispatchNotificationUseCase dispatchNotificationUseCase;
    @Mock private ProcessedEventRepository processedEvents;

    private UserInitiatedEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UserInitiatedEventHandler(objectMapper, initiateNotificationUseCase, dispatchNotificationUseCase, processedEvents);
    }

    @Test
    @DisplayName("a valid payload creates a welcome notification and dispatches it")
    void handle() throws Exception {
        UUID organisationId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        UserInitiatedEventPayload event = new UserInitiatedEventPayload(UUID.randomUUID(), organisationId, "ada@thinklab.com", "Ada Lovelace", Instant.now());
        NotificationResponse response = new NotificationResponse(notificationId, organisationId, "ada@thinklab.com", "Welcome to ThinkLab",
                "Hello Ada Lovelace, your ThinkLab account has just been created.", "LOG", "PENDING", 0, 5, null, Instant.now(), Instant.now());

        when(objectMapper.readValue(eq("payload"), eq(UserInitiatedEventPayload.class))).thenReturn(event);
        when(initiateNotificationUseCase.execute(eq(organisationId), any(InitiateNotificationRequest.class))).thenReturn(Mono.just(response));
        when(dispatchNotificationUseCase.execute(notificationId)).thenReturn(Mono.empty());
        when(processedEvents.isProcessed("user.initiated:" + event.id())).thenReturn(Mono.just(false));
        when(processedEvents.markProcessed("user.initiated:" + event.id())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handle("payload")).verifyComplete();

        verify(dispatchNotificationUseCase).execute(notificationId);
        verify(processedEvents).markProcessed("user.initiated:" + event.id());
    }

    @Test
    @DisplayName("a redelivered event that was already handled is acknowledged without a second notification")
    void handleDuplicate() throws Exception {
        UserInitiatedEventPayload event = new UserInitiatedEventPayload(UUID.randomUUID(), UUID.randomUUID(), "ada@thinklab.com", "Ada", Instant.now());
        when(objectMapper.readValue(eq("payload"), eq(UserInitiatedEventPayload.class))).thenReturn(event);
        when(processedEvents.isProcessed("user.initiated:" + event.id())).thenReturn(Mono.just(true));

        StepVerifier.create(handler.handle("payload")).verifyComplete();

        verifyNoInteractions(initiateNotificationUseCase, dispatchNotificationUseCase);
        verify(processedEvents, never()).markProcessed(any());
    }

    @Test
    @DisplayName("a malformed payload is propagated as an error, never dispatched")
    void handleMalformedPayload() throws Exception {
        when(objectMapper.readValue(eq("garbage"), eq(UserInitiatedEventPayload.class))).thenThrow(new java.io.IOException("bad json"));

        StepVerifier.create(handler.handle("garbage")).expectError(java.io.IOException.class).verify();
    }

    @Test
    @DisplayName("an initiate failure is propagated and never reaches dispatch")
    void handleInitiateFailure() throws Exception {
        UserInitiatedEventPayload event = new UserInitiatedEventPayload(UUID.randomUUID(), UUID.randomUUID(), "ada@thinklab.com", "Ada", Instant.now());
        when(objectMapper.readValue(eq("payload"), eq(UserInitiatedEventPayload.class))).thenReturn(event);
        when(initiateNotificationUseCase.execute(any(), any())).thenReturn(Mono.error(new IllegalStateException("hash down")));
        when(processedEvents.isProcessed(any())).thenReturn(Mono.just(false));

        StepVerifier.create(handler.handle("payload")).expectError(IllegalStateException.class).verify();
        // Not recorded: the redelivery must get another chance.
        verify(processedEvents, never()).markProcessed(any());
    }

    @Test
    @DisplayName("payloadJson and the inbox are null-checked")
    void nullGuard() {
        assertThrows(NullPointerException.class,
                () -> new UserInitiatedEventHandler(objectMapper, initiateNotificationUseCase, dispatchNotificationUseCase, null));
        assertThrows(NullPointerException.class, () -> handler.handle(null));
    }
}
