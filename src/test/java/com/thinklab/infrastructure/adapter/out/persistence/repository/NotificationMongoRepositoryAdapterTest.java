package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.exception.NotificationNotFoundException;
import com.thinklab.domain.model.Notification;
import com.thinklab.domain.model.Notification.NotificationChannel;
import com.thinklab.domain.model.Notification.NotificationStatus;
import com.thinklab.infrastructure.adapter.out.persistence.entity.NotificationDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.NotificationDocument.NotificationPersistenceMapper;
import org.bson.BsonObjectId;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class NotificationMongoRepositoryAdapterTest {

    @Mock private MongoClient mongoClient;
    @Mock private MongoDatabase mongoDatabase;
    @Mock private MongoCollection<NotificationDocument> mongoCollection;

    private NotificationMongoRepositoryAdapter adapter;
    private UUID organisationId;
    private UUID notificationId;
    private Notification notification;

    @BeforeEach
    void setUp() {
        when(mongoClient.getDatabase("thinklab_notification_db")).thenReturn(mongoDatabase);
        when(mongoDatabase.getCollection("notifications", NotificationDocument.class)).thenReturn(mongoCollection);
        when(mongoCollection.withCodecRegistry(any())).thenReturn(mongoCollection);
        adapter = new NotificationMongoRepositoryAdapter(mongoClient);

        organisationId = UUID.randomUUID();
        notificationId = UUID.randomUUID();
        notification = Notification.createNew(notificationId, organisationId, "ada@thinklab.com", "Welcome", "Hello", NotificationChannel.LOG);
    }

    @Test
    @DisplayName("create should insert the mapped document and emit the aggregate")
    void createSuccess() {
        when(mongoCollection.insertOne(any(NotificationDocument.class)))
                .thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));

        StepVerifier.create(adapter.create(notification))
                .expectNextMatches(saved -> saved.getId().equals(notificationId))
                .verifyComplete();

        ArgumentCaptor<NotificationDocument> captor = ArgumentCaptor.forClass(NotificationDocument.class);
        verify(mongoCollection).insertOne(captor.capture());
        assertEquals("PENDING", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("create should propagate a driver failure")
    void createFailure() {
        when(mongoCollection.insertOne(any(NotificationDocument.class))).thenReturn(Mono.error(new IllegalStateException("mongo down")));

        StepVerifier.create(adapter.create(notification)).expectErrorMessage("mongo down").verify();
    }

    @Test
    @DisplayName("findById should map the document back to the aggregate")
    void findByIdSuccess() {
        FindPublisher<NotificationDocument> publisher = mock(FindPublisher.class);
        when(mongoCollection.find(any(Bson.class))).thenReturn(publisher);
        when(publisher.first()).thenReturn(Mono.just(NotificationPersistenceMapper.toDocument(notification)));

        StepVerifier.create(adapter.findById(notificationId))
                .expectNextMatches(found -> found.getId().equals(notificationId) && found.getStatus() == NotificationStatus.PENDING)
                .verifyComplete();
    }

    @Test
    @DisplayName("findById should complete empty when the notification does not exist")
    void findByIdEmpty() {
        FindPublisher<NotificationDocument> publisher = mock(FindPublisher.class);
        when(mongoCollection.find(any(Bson.class))).thenReturn(publisher);
        when(publisher.first()).thenReturn(Mono.empty());

        StepVerifier.create(adapter.findById(notificationId)).verifyComplete();
    }

    @Test
    @DisplayName("findAllByOrganisationId should always filter by tenant and add status when given")
    void findAllFilters() {
        FindPublisher<NotificationDocument> publisher = mock(FindPublisher.class);
        when(mongoCollection.find(any(Bson.class))).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<NotificationDocument> subscriber = invocation.getArgument(0);
            Flux.just(NotificationPersistenceMapper.toDocument(notification)).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());

        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, NotificationStatus.PENDING))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Bson> captor = ArgumentCaptor.forClass(Bson.class);
        verify(mongoCollection).find(captor.capture());
        assertTrue(captor.getValue().toString().contains("organisationId"));
    }

    @Test
    @DisplayName("findAllByOrganisationId without a status filter should only constrain the tenant")
    void findAllTenantOnly() {
        FindPublisher<NotificationDocument> publisher = mock(FindPublisher.class);
        when(mongoCollection.find(any(Bson.class))).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<NotificationDocument> subscriber = invocation.getArgument(0);
            Flux.<NotificationDocument>empty().subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());

        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, null)).verifyComplete();
    }

    @Test
    @DisplayName("updateStatus should set status, attempts and lastError")
    void updateStatusSuccess() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        StepVerifier.create(adapter.updateStatus(notificationId, NotificationStatus.FAILED, 1, "boom")).verifyComplete();
    }

    @Test
    @DisplayName("updateStatus should map a zero-match result to NotificationNotFoundException")
    void updateStatusNotFound() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));

        StepVerifier.create(adapter.updateStatus(notificationId, NotificationStatus.FAILED, 1, "boom"))
                .expectError(NotificationNotFoundException.class)
                .verify();
    }
}
