package com.thinklab.infrastructure.adapter.out.persistence;

import com.thinklab.Infrastructure;
import com.thinklab.application.usecase.ControlNotificationUseCase;
import com.thinklab.application.usecase.DispatchNotificationUseCase;
import com.thinklab.domain.exception.InvalidNotificationStatusException;
import com.thinklab.domain.model.Notification;
import com.thinklab.domain.model.Notification.NotificationChannel;
import com.thinklab.domain.model.Notification.NotificationStatus;
import com.thinklab.domain.repository.NotificationRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The notification transition races against a real MongoDB (platform race audit, ACTION-PLAN 0.5): a notification read by two requests at once must
 * have exactly one of them win, and the stored status must be the winner's.
 */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransitionRaceIT implements TestPropertyProvider {

    private static final String DATABASE = "notification_dispatch_race_it";
    private static final String EXECUTOR = "op-1";

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", Infrastructure.mongoUri(DATABASE));
    }

    @Inject NotificationRepository notifications;
    @Inject ControlNotificationUseCase control;
    @Inject DispatchNotificationUseCase dispatch;

    private Notification pending() {
        return notifications.create(Notification.createNew(UUID.randomUUID(), UUID.randomUUID(), "ada@thinklab.com", "Hi", "Hello", NotificationChannel.LOG)).block();
    }

    @Test
    @DisplayName("ten cancellations of one notification at once: exactly one wins, the others are refused with a conflict")
    void tenCancellationsAtOnce() {
        Notification notification = pending();

        List<String> results = Flux.range(0, 10)
                .flatMap(i -> control.execute(notification.getId(), notification.getOrganisationId(), ControlNotificationUseCase.Action.CANCEL, EXECUTOR).thenReturn("OK")
                        .onErrorResume(e -> Mono.just(e.getClass().getSimpleName())), 10)
                .collectList().block(Duration.ofSeconds(30));

        assertEquals(1, results.stream().filter("OK"::equals).count(), results.toString());
        assertEquals(9, results.stream().filter(InvalidNotificationStatusException.class.getSimpleName()::equals).count(), results.toString());
        assertEquals(NotificationStatus.CANCELLED, notifications.findById(notification.getId(), notification.getOrganisationId()).block().getStatus());
    }

    @Test
    @DisplayName("ten retries of one failed notification at once: whoever wins, each one consumed exactly one attempt")
    void tenRetriesConsumeOneAttemptEach() {
        Notification notification = pending();
        notifications.updateStatus(notification.getId(), NotificationStatus.PENDING, 0, NotificationStatus.FAILED, 1, "smtp down").block();

        List<String> results = Flux.range(0, 10)
                .flatMap(i -> control.execute(notification.getId(), notification.getOrganisationId(), ControlNotificationUseCase.Action.RETRY, EXECUTOR).thenReturn("OK")
                        .onErrorResume(e -> Mono.just(e.getClass().getSimpleName())), 10)
                .collectList().block(Duration.ofSeconds(30));

        long winners = results.stream().filter("OK"::equals).count();
        Notification stored = notifications.findById(notification.getId(), notification.getOrganisationId()).block();
        assertEquals(1 + winners, stored.getAttempts(), "every winning retry consumes one attempt, and a refused one none: " + results);
        assertEquals(NotificationStatus.SENDING, stored.getStatus());
    }

    @RepeatedTest(5)
    @DisplayName("a dispatch and a cancel at once: one wins, and the stored status is the winner's")
    void dispatchAgainstCancel() {
        Notification notification = pending();

        List<String> results = Flux.just("DISPATCH", "CANCEL")
                .flatMap(action -> ("DISPATCH".equals(action)
                        ? dispatch.execute(notification.getId(), notification.getOrganisationId())
                        : control.execute(notification.getId(), notification.getOrganisationId(), ControlNotificationUseCase.Action.CANCEL, EXECUTOR))
                        .thenReturn(action).onErrorResume(e -> Mono.just("REFUSED")), 2)
                .collectList().block(Duration.ofSeconds(30));

        List<String> winners = results.stream().filter(r -> !"REFUSED".equals(r)).toList();
        assertEquals(1, winners.size(), results.toString());
        NotificationStatus expected = "DISPATCH".equals(winners.get(0)) ? NotificationStatus.DELIVERED : NotificationStatus.CANCELLED;
        assertEquals(expected, notifications.findById(notification.getId(), notification.getOrganisationId()).block().getStatus());
    }
}
