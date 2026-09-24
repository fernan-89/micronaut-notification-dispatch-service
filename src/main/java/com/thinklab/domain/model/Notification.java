package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidNotificationStatusException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Core Domain Model representing the Notification Aggregate Root.
 *
 * <p><b>BIAN Alignment (ADR-013):</b> This is the Control Record of the {@code notification-dispatch}
 * Service Domain — an operational, transient dispatch record, not a compliance ledger like
 * {@code it-asset-registry}'s Asset: a Notification carries no forensic audit trail (kit ADR-003 /
 * ADR-023), because it is not itself the system of record for anything; the event that triggered it
 * already is.
 *
 * <p>Strictly pure Java. Agnostic of frameworks, databases, or web layers.
 */
public class Notification {

    public static final int DEFAULT_MAX_ATTEMPTS = 5;

    private final UUID id;
    private final UUID organisationId;
    private final String recipient;
    private final String subject;
    private final String body;
    private final NotificationChannel channel;
    private NotificationStatus status;
    private int attempts;
    private final int maxAttempts;
    private String lastError;
    private final Instant createdAt;
    private Instant updatedAt;

    private Notification(UUID id, UUID organisationId, String recipient, String subject, String body,
                          NotificationChannel channel) {
        this.id = id;
        this.organisationId = organisationId;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.channel = channel;
        this.status = NotificationStatus.PENDING;
        this.attempts = 0;
        this.maxAttempts = DEFAULT_MAX_ATTEMPTS;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    private Notification(UUID id, UUID organisationId, String recipient, String subject, String body,
                          NotificationChannel channel, NotificationStatus status, int attempts, int maxAttempts,
                          String lastError, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organisationId = organisationId;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.channel = channel;
        this.status = status != null ? status : NotificationStatus.PENDING;
        this.attempts = attempts;
        this.maxAttempts = maxAttempts > 0 ? maxAttempts : DEFAULT_MAX_ATTEMPTS;
        this.lastError = lastError;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    /**
     * Static factory for aggregate creation (BIAN Behavior Qualifier: {@code initiate}). The UUID must
     * be provided by the orchestration layer after calling the Hash Token Registry.
     */
    public static Notification createNew(UUID id, UUID organisationId, String recipient, String subject,
                                          String body, NotificationChannel channel) {
        if (id == null || organisationId == null || channel == null) {
            throw new IllegalArgumentException("ID, Organisation ID, and Channel are mandatory for Notification creation.");
        }
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Recipient is mandatory for Notification creation.");
        }
        if (subject == null || subject.isBlank() || body == null || body.isBlank()) {
            throw new IllegalArgumentException("Subject and Body are mandatory for Notification creation.");
        }
        return new Notification(id, organisationId, recipient, subject, body, channel);
    }

    /**
     * Reconstitutes an existing Notification aggregate from the persistence layer.
     */
    public static Notification reconstitute(UUID id, UUID organisationId, String recipient, String subject,
                                             String body, NotificationChannel channel, NotificationStatus status,
                                             int attempts, int maxAttempts, String lastError, Instant createdAt,
                                             Instant updatedAt) {
        if (id == null || organisationId == null || recipient == null || channel == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Recipient, and Channel are mandatory to reconstitute a Notification.");
        }
        return new Notification(id, organisationId, recipient, subject, body, channel, status, attempts, maxAttempts,
                lastError, createdAt, updatedAt);
    }

    // --- Domain Behaviors (State Mutations) ---

    /** Behavior Qualifier: {@code control/send}. Marks an attempt at delivery as underway. */
    public void send(String executor) {
        changeStatus(NotificationStatus.SENDING, executor);
    }

    /** Behavior Qualifier: {@code control/deliver}. Records a successful delivery. */
    public void deliver(String executor) {
        changeStatus(NotificationStatus.DELIVERED, executor);
        this.lastError = null;
    }

    /** Behavior Qualifier: {@code control/fail}. Records a failed delivery attempt. */
    public void fail(String executor, String errorMessage) {
        changeStatus(NotificationStatus.FAILED, executor);
        this.lastError = errorMessage;
    }

    /** Behavior Qualifier: {@code control/cancel}. Withdraws a Notification before it was ever sent. */
    public void cancel(String executor) {
        changeStatus(NotificationStatus.CANCELLED, executor);
    }

    /**
     * Behavior Qualifier: {@code control/retry}. Unlike every other transition, retry is allowed from
     * two source states: {@link NotificationStatus#FAILED} (the normal case) and
     * {@link NotificationStatus#SENDING} (a cheap mitigation for a notification stuck mid-dispatch after
     * a crash — kit ADR-003 documents that there is no automatic sweep/requeue in v1). Every retry
     * consumes one attempt regardless of which source state it came from.
     */
    public void retry(String executor) {
        requireExecutor(executor);
        if (status != NotificationStatus.FAILED && status != NotificationStatus.SENDING) {
            throw new InvalidNotificationStatusException(String.format(
                    "Compliance Violation: retry is only allowed from FAILED or SENDING, not [%s].", status));
        }
        if (attempts >= maxAttempts) {
            throw new InvalidNotificationStatusException(String.format(
                    "Retry Exhausted: Notification has reached the maximum of [%d] delivery attempts.", maxAttempts));
        }
        this.status = NotificationStatus.SENDING;
        this.attempts = this.attempts + 1;
        this.updatedAt = Instant.now();
    }

    // --- Internal helpers ---

    private void changeStatus(NotificationStatus newStatus, String executor) {
        Objects.requireNonNull(newStatus, "Status cannot be null.");
        requireExecutor(executor);
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    private static void requireExecutor(String executor) {
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory for auditable Notification mutations.");
        }
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public NotificationChannel getChannel() { return channel; }
    public NotificationStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public int getMaxAttempts() { return maxAttempts; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // --- Nested Value Objects ---

    public enum NotificationChannel {
        LOG, WEBHOOK
    }

    /**
     * Formal lifecycle state machine for the Notification Control Record, mirroring the
     * {@code AssetStatus} pattern (ADR-013). {@code retry} is deliberately outside this table — see
     * {@link Notification#retry(String)}.
     *
     * <pre>
     * PENDING -> SENDING -> DELIVERED (terminal)
     *               |
     *               +------> FAILED
     * PENDING -> CANCELLED (terminal)
     * </pre>
     */
    public enum NotificationStatus {
        PENDING, SENDING, DELIVERED, FAILED, CANCELLED;

        /**
         * Validates if the transition from the current state to the target state is legally permitted.
         *
         * @throws InvalidNotificationStatusException (HTTP 409) if the transition violates business
         *                                             compliance rules or is unnecessarily idempotent.
         */
        public void validateTransitionTo(NotificationStatus targetStatus) {
            Objects.requireNonNull(targetStatus, "Target NotificationStatus must not be null for transition validation.");

            if (this == targetStatus) {
                throw new InvalidNotificationStatusException(String.format(
                        "Idempotency Violation: The Notification is already in the [%s] state.", this));
            }
            if (!canTransitionTo(targetStatus)) {
                throw new InvalidNotificationStatusException(String.format(
                        "Compliance Violation: Illegal state transition from [%s] to [%s].", this, targetStatus));
            }
        }

        public boolean canTransitionTo(NotificationStatus targetStatus) {
            if (targetStatus == null) {
                return false;
            }
            return switch (this) {
                case PENDING -> targetStatus == SENDING || targetStatus == CANCELLED;
                case SENDING -> targetStatus == DELIVERED || targetStatus == FAILED;
                case FAILED, DELIVERED, CANCELLED -> false;
            };
        }
    }
}
