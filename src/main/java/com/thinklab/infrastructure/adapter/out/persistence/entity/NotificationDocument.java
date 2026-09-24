package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.Notification;
import com.thinklab.domain.model.Notification.NotificationChannel;
import com.thinklab.domain.model.Notification.NotificationStatus;
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.util.UUID;

/**
 * Infrastructure-specific representation of the Notification Aggregate for MongoDB. Ensures the pure
 * Domain Model remains untainted by persistence annotations. Uses native BSON annotations for
 * high-performance mapping without ORM overhead.
 */
@Introspected
public class NotificationDocument {

    @BsonId // Native MongoDB driver annotation for Sovereign Identity
    private UUID id;

    private UUID organisationId;
    private String recipient;
    private String subject;
    private String body;
    private String channel;
    private String status;
    private int attempts;
    private int maxAttempts;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;

    // Getters and Setters required by framework POJO codec
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Internal Persistence Mapper ensuring strict isolation between Document and Domain.
     */
    public static final class NotificationPersistenceMapper {

        private NotificationPersistenceMapper() { throw new UnsupportedOperationException(); }

        public static NotificationDocument toDocument(Notification notification) {
            NotificationDocument doc = new NotificationDocument();
            doc.setId(notification.getId());
            doc.setOrganisationId(notification.getOrganisationId());
            doc.setRecipient(notification.getRecipient());
            doc.setSubject(notification.getSubject());
            doc.setBody(notification.getBody());
            doc.setChannel(notification.getChannel().name());
            doc.setStatus(notification.getStatus().name());
            doc.setAttempts(notification.getAttempts());
            doc.setMaxAttempts(notification.getMaxAttempts());
            doc.setLastError(notification.getLastError());
            doc.setCreatedAt(notification.getCreatedAt());
            doc.setUpdatedAt(notification.getUpdatedAt());
            return doc;
        }

        public static Notification toDomain(NotificationDocument doc) {
            return Notification.reconstitute(
                    doc.getId(),
                    doc.getOrganisationId(),
                    doc.getRecipient(),
                    doc.getSubject(),
                    doc.getBody(),
                    NotificationChannel.valueOf(doc.getChannel()),
                    doc.getStatus() != null ? NotificationStatus.valueOf(doc.getStatus()) : NotificationStatus.PENDING,
                    doc.getAttempts(),
                    doc.getMaxAttempts(),
                    doc.getLastError(),
                    doc.getCreatedAt(),
                    doc.getUpdatedAt()
            );
        }
    }
}
