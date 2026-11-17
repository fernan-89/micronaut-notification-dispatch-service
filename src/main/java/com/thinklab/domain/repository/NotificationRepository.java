package com.thinklab.domain.repository;

import com.thinklab.domain.model.Notification;
import com.thinklab.domain.model.Notification.NotificationStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound Port for Notification persistence operations (notification-dispatch Service Domain).
 * Part of the pure Domain Layer.
 *
 * <p>Unlike {@code it-asset-registry}'s Asset, a Notification carries no forensic audit trail (ADR-023)
 * — every domain behavior ({@code send}/{@code deliver}/{@code fail}/{@code cancel}/{@code retry})
 * only ever changes status, attempts and lastError together, so a single granular
 * {@link #updateStatus(UUID, NotificationStatus, int, NotificationStatus, int, String)} covers all of them.
 *
 * <p>There is no {@code deleteById} — a Notification is either {@code DELIVERED} or {@code CANCELLED},
 * never physically removed (ADR-013).
 */
public interface NotificationRepository {

    Mono<Notification> create(Notification notification);

    Mono<Notification> findById(UUID id);

    /**
     * Tenant-scoped listing of Notifications belonging to a given Organisation.
     *
     * @param organisationId the tenant boundary
     * @param status         optional status filter ({@code null} = any)
     */
    Flux<Notification> findAllByOrganisationId(UUID organisationId, NotificationStatus status);

    /**
     * Guarded write (ADR-033 of the platform): applies the change only if the stored notification is still in
     * {@code expectedStatus} with {@code expectedAttempts} attempts, i.e. still the one the caller read.
     *
     * @throws com.thinklab.domain.exception.NotificationNotFoundException if there is no such notification
     * @throws com.thinklab.domain.exception.InvalidNotificationStatusException (409) if it changed since it was read
     */
    Mono<Void> updateStatus(UUID id, NotificationStatus expectedStatus, int expectedAttempts,
                            NotificationStatus status, int attempts, String lastError);
}
