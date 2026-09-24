package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.exception.NotificationNotFoundException;
import com.thinklab.domain.model.Notification;
import com.thinklab.domain.model.Notification.NotificationStatus;
import com.thinklab.domain.repository.NotificationRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.NotificationDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.NotificationDocument.NotificationPersistenceMapper;
import jakarta.inject.Singleton;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MongoDB Reactive Repository Adapter.
 * Implements the pure Domain Port using the low-level Reactive Streams MongoDB Driver.
 */
@Singleton
public class NotificationMongoRepositoryAdapter implements NotificationRepository {

    private static final Logger log = LoggerFactory.getLogger(NotificationMongoRepositoryAdapter.class);

    static final String DATABASE_NAME = "thinklab_notification_db";
    static final String COLLECTION_NAME = "notifications";
    private static final String FIELD_ID = "_id";

    /**
     * The MongoDB driver's default codec registry has no codec for arbitrary POJOs such as
     * {@link NotificationDocument}. Without a {@link PojoCodecProvider} every read/write fails with
     * {@code CodecConfigurationException} (lesson learned from the Party Reference Data Directory
     * rollout, repeated in every Mongo adapter since).
     */
    private static final CodecRegistry POJO_CODEC_REGISTRY = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    private final MongoClient mongoClient;

    public NotificationMongoRepositoryAdapter(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    private MongoCollection<NotificationDocument> getCollection() {
        return mongoClient.getDatabase(DATABASE_NAME)
                .getCollection(COLLECTION_NAME, NotificationDocument.class)
                .withCodecRegistry(POJO_CODEC_REGISTRY);
    }

    @Override
    public Mono<Notification> create(Notification notification) {
        log.debug("[PERSISTENCE] Monolithic create for Notification Aggregate: {}", notification.getId());

        NotificationDocument document = NotificationPersistenceMapper.toDocument(notification);

        return Mono.from(getCollection().insertOne(document))
                .doOnSuccess(result -> log.debug("[PERSISTENCE] Aggregate successfully created in MongoDB"))
                .map(result -> notification);
    }

    @Override
    public Mono<Notification> findById(UUID id) {
        log.debug("[PERSISTENCE] Fetching Notification Aggregate by ID: {}", id);

        return Mono.from(getCollection().find(Filters.eq(FIELD_ID, id)).first())
                .map(NotificationPersistenceMapper::toDomain);
    }

    @Override
    public Flux<Notification> findAllByOrganisationId(UUID organisationId, NotificationStatus status) {
        log.debug("[PERSISTENCE] Fetching Notifications for organisation {} status {}", organisationId, status);

        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq("organisationId", organisationId));
        if (status != null) {
            filters.add(Filters.eq("status", status.name()));
        }

        return Flux.from(getCollection().find(Filters.and(filters)))
                .map(NotificationPersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> updateStatus(UUID id, NotificationStatus status, int attempts, String lastError) {
        log.debug("[PERSISTENCE] Partial Mutation: updateStatus for ID: {}", id);

        Bson update = Updates.combine(
                Updates.set("status", status.name()),
                Updates.set("attempts", attempts),
                Updates.set("lastError", lastError),
                Updates.set("updatedAt", Instant.now())
        );

        return Mono.from(getCollection().updateOne(Filters.eq(FIELD_ID, id), update))
                .flatMap(result -> {
                    if (result.getMatchedCount() == 0) {
                        return Mono.error(new NotificationNotFoundException(id));
                    }
                    return Mono.empty();
                });
    }
}
