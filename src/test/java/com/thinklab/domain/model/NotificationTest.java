package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidNotificationStatusException;
import com.thinklab.domain.model.Notification.NotificationChannel;
import com.thinklab.domain.model.Notification.NotificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationTest {

    private static final String EXECUTOR = "system:notification-dispatch";

    private UUID id;
    private UUID organisationId;
    private Notification notification;

    @BeforeEach
    void setUp() {
        id = UUID.randomUUID();
        organisationId = UUID.randomUUID();
        notification = Notification.createNew(id, organisationId, "ada@thinklab.com", "Welcome",
                "Hello Ada", NotificationChannel.LOG);
    }

    // ---------------------------------------------------------------- creation

    @Test
    @DisplayName("Should create a new Notification in PENDING state with zero attempts")
    void shouldCreateInPendingState() {
        assertEquals(id, notification.getId());
        assertEquals(organisationId, notification.getOrganisationId());
        assertEquals("ada@thinklab.com", notification.getRecipient());
        assertEquals("Welcome", notification.getSubject());
        assertEquals("Hello Ada", notification.getBody());
        assertEquals(NotificationChannel.LOG, notification.getChannel());
        assertEquals(NotificationStatus.PENDING, notification.getStatus());
        assertEquals(0, notification.getAttempts());
        assertEquals(Notification.DEFAULT_MAX_ATTEMPTS, notification.getMaxAttempts());
        assertNull(notification.getLastError());
        assertNotNull(notification.getCreatedAt());
        assertEquals(notification.getCreatedAt(), notification.getUpdatedAt());
    }

    @Test
    @DisplayName("Should reject creation when any mandatory field is missing")
    void shouldRejectInvalidCreation() {
        assertThrows(IllegalArgumentException.class, () -> Notification.createNew(null, organisationId, "r", "s", "b", NotificationChannel.LOG));
        assertThrows(IllegalArgumentException.class, () -> Notification.createNew(id, null, "r", "s", "b", NotificationChannel.LOG));
        assertThrows(IllegalArgumentException.class, () -> Notification.createNew(id, organisationId, "r", "s", "b", null));
        assertThrows(IllegalArgumentException.class, () -> Notification.createNew(id, organisationId, null, "s", "b", NotificationChannel.LOG));
        assertThrows(IllegalArgumentException.class, () -> Notification.createNew(id, organisationId, " ", "s", "b", NotificationChannel.LOG));
        assertThrows(IllegalArgumentException.class, () -> Notification.createNew(id, organisationId, "r", null, "b", NotificationChannel.LOG));
        assertThrows(IllegalArgumentException.class, () -> Notification.createNew(id, organisationId, "r", " ", "b", NotificationChannel.LOG));
        assertThrows(IllegalArgumentException.class, () -> Notification.createNew(id, organisationId, "r", "s", null, NotificationChannel.LOG));
        assertThrows(IllegalArgumentException.class, () -> Notification.createNew(id, organisationId, "r", "s", " ", NotificationChannel.LOG));
    }

    @Test
    @DisplayName("Should expose both channels")
    void shouldExposeChannels() {
        assertEquals(2, NotificationChannel.values().length);
        assertEquals(NotificationChannel.WEBHOOK, NotificationChannel.valueOf("WEBHOOK"));
    }

    // ---------------------------------------------------------------- reconstitution

    @Test
    @DisplayName("Should reconstitute a Notification from persisted state, defaulting missing status/timestamps/maxAttempts")
    void shouldReconstitute() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant updated = Instant.parse("2026-02-01T00:00:00Z");

        Notification restored = Notification.reconstitute(id, organisationId, "r", "s", "b", NotificationChannel.WEBHOOK,
                NotificationStatus.FAILED, 2, 7, "boom", created, updated);

        assertEquals(NotificationStatus.FAILED, restored.getStatus());
        assertEquals(2, restored.getAttempts());
        assertEquals(7, restored.getMaxAttempts());
        assertEquals("boom", restored.getLastError());
        assertEquals(created, restored.getCreatedAt());
        assertEquals(updated, restored.getUpdatedAt());

        Notification defaults = Notification.reconstitute(id, organisationId, "r", "s", "b", NotificationChannel.LOG,
                null, 0, 0, null, null, null);
        assertEquals(NotificationStatus.PENDING, defaults.getStatus());
        assertEquals(Notification.DEFAULT_MAX_ATTEMPTS, defaults.getMaxAttempts());
        assertNotNull(defaults.getCreatedAt());
        assertEquals(defaults.getCreatedAt(), defaults.getUpdatedAt());
    }

    @Test
    @DisplayName("Should reject reconstitution without mandatory persisted fields")
    void shouldRejectInvalidReconstitution() {
        assertThrows(IllegalArgumentException.class, () -> Notification.reconstitute(null, organisationId, "r", "s", "b", NotificationChannel.LOG, null, 0, 0, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Notification.reconstitute(id, null, "r", "s", "b", NotificationChannel.LOG, null, 0, 0, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Notification.reconstitute(id, organisationId, null, "s", "b", NotificationChannel.LOG, null, 0, 0, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Notification.reconstitute(id, organisationId, "r", "s", "b", null, null, 0, 0, null, null, null));
    }

    // ---------------------------------------------------------------- lifecycle

    @Test
    @DisplayName("Should follow the legal path PENDING -> SENDING -> DELIVERED")
    void shouldFollowLegalPath() {
        notification.send(EXECUTOR);
        assertEquals(NotificationStatus.SENDING, notification.getStatus());

        notification.deliver(EXECUTOR);
        assertEquals(NotificationStatus.DELIVERED, notification.getStatus());
        assertNull(notification.getLastError());
    }

    @Test
    @DisplayName("Should follow PENDING -> SENDING -> FAILED and record the error")
    void shouldFollowFailurePath() {
        notification.send(EXECUTOR);

        notification.fail(EXECUTOR, "smtp down");

        assertEquals(NotificationStatus.FAILED, notification.getStatus());
        assertEquals("smtp down", notification.getLastError());
    }

    @Test
    @DisplayName("Should cancel a PENDING notification")
    void shouldCancel() {
        notification.cancel(EXECUTOR);

        assertEquals(NotificationStatus.CANCELLED, notification.getStatus());
    }

    @Test
    @DisplayName("Should retry a FAILED notification back into SENDING, consuming one attempt")
    void shouldRetryFromFailed() {
        notification.send(EXECUTOR);
        notification.fail(EXECUTOR, "boom");

        notification.retry(EXECUTOR);

        assertEquals(NotificationStatus.SENDING, notification.getStatus());
        assertEquals(1, notification.getAttempts());
    }

    @Test
    @DisplayName("Should retry a notification stuck in SENDING (crash mitigation), consuming one attempt")
    void shouldRetryFromStuckSending() {
        notification.send(EXECUTOR);

        notification.retry(EXECUTOR);

        assertEquals(NotificationStatus.SENDING, notification.getStatus());
        assertEquals(1, notification.getAttempts());
    }

    @Test
    @DisplayName("Should reject retry once attempts reach maxAttempts")
    void shouldRejectRetryWhenExhausted() {
        Notification exhausted = Notification.reconstitute(id, organisationId, "r", "s", "b", NotificationChannel.LOG,
                NotificationStatus.FAILED, 5, 5, "still failing", null, null);

        InvalidNotificationStatusException ex = assertThrows(InvalidNotificationStatusException.class,
                () -> exhausted.retry(EXECUTOR));

        assertEquals("ERR-NTF-00409", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Retry Exhausted"));
        assertEquals(5, exhausted.getAttempts());
    }

    @Test
    @DisplayName("Should reject retry from any state other than FAILED or SENDING")
    void shouldRejectRetryFromWrongState() {
        InvalidNotificationStatusException ex = assertThrows(InvalidNotificationStatusException.class,
                () -> notification.retry(EXECUTOR));

        assertTrue(ex.getMessage().contains("Compliance Violation"));
    }

    @Test
    @DisplayName("Should reject retry without an executor")
    void shouldRejectRetryWithoutExecutor() {
        notification.send(EXECUTOR);
        notification.fail(EXECUTOR, "boom");

        assertThrows(IllegalArgumentException.class, () -> notification.retry(null));
        assertThrows(IllegalArgumentException.class, () -> notification.retry(" "));
        assertEquals(0, notification.getAttempts());
    }

    @Test
    @DisplayName("Should reject redundant self-transitions as an idempotency violation")
    void shouldRejectSelfTransition() {
        notification.send(EXECUTOR);

        InvalidNotificationStatusException ex = assertThrows(InvalidNotificationStatusException.class,
                () -> notification.send(EXECUTOR));

        assertTrue(ex.getMessage().contains("Idempotency Violation"));
    }

    @Test
    @DisplayName("Should reject PENDING -> DELIVERED and PENDING -> FAILED as illegal jumps")
    void shouldRejectIllegalJumps() {
        InvalidNotificationStatusException ex = assertThrows(InvalidNotificationStatusException.class, () -> notification.deliver(EXECUTOR));
        assertTrue(ex.getMessage().contains("Compliance Violation"));
        assertThrows(InvalidNotificationStatusException.class, () -> notification.fail(EXECUTOR, "x"));
        assertEquals(NotificationStatus.PENDING, notification.getStatus());
    }

    @Test
    @DisplayName("Should treat DELIVERED and CANCELLED as terminal for every mutation")
    void shouldBlockEverythingAfterTerminal() {
        notification.send(EXECUTOR);
        notification.deliver(EXECUTOR);

        assertThrows(InvalidNotificationStatusException.class, () -> notification.send(EXECUTOR));
        assertThrows(InvalidNotificationStatusException.class, () -> notification.deliver(EXECUTOR));
        assertThrows(InvalidNotificationStatusException.class, () -> notification.fail(EXECUTOR, "x"));
        assertThrows(InvalidNotificationStatusException.class, () -> notification.cancel(EXECUTOR));
        assertThrows(InvalidNotificationStatusException.class, () -> notification.retry(EXECUTOR));
    }

    @Test
    @DisplayName("Should require a non-blank executor on every mutation")
    void shouldRequireExecutor() {
        assertThrows(IllegalArgumentException.class, () -> notification.send(null));
        assertThrows(IllegalArgumentException.class, () -> notification.send(" "));
        assertThrows(IllegalArgumentException.class, () -> notification.cancel(null));
        assertEquals(NotificationStatus.PENDING, notification.getStatus());
    }

    // ---------------------------------------------------------------- exhaustive FSM matrix

    @TestFactory
    @DisplayName("NotificationStatus transition matrix is exhaustive and matches the documented FSM")
    Stream<DynamicTest> transitionMatrix() {
        Map<NotificationStatus, Set<NotificationStatus>> allowed = Map.of(
                NotificationStatus.PENDING, EnumSet.of(NotificationStatus.SENDING, NotificationStatus.CANCELLED),
                NotificationStatus.SENDING, EnumSet.of(NotificationStatus.DELIVERED, NotificationStatus.FAILED),
                NotificationStatus.DELIVERED, EnumSet.noneOf(NotificationStatus.class),
                NotificationStatus.FAILED, EnumSet.noneOf(NotificationStatus.class),
                NotificationStatus.CANCELLED, EnumSet.noneOf(NotificationStatus.class)
        );

        return Stream.of(NotificationStatus.values()).flatMap(from -> Stream.of(NotificationStatus.values()).map(to ->
                DynamicTest.dynamicTest(from + " -> " + to, () -> {
                    boolean expected = allowed.get(from).contains(to);
                    assertEquals(expected, from.canTransitionTo(to));
                    if (expected) {
                        from.validateTransitionTo(to);
                    } else {
                        assertThrows(InvalidNotificationStatusException.class, () -> from.validateTransitionTo(to));
                    }
                })));
    }

    @Test
    @DisplayName("canTransitionTo(null) is false and validateTransitionTo(null) is a programming error")
    void shouldHandleNullTargets() {
        assertFalse(NotificationStatus.PENDING.canTransitionTo(null));
        assertThrows(NullPointerException.class, () -> NotificationStatus.PENDING.validateTransitionTo(null));
    }
}
