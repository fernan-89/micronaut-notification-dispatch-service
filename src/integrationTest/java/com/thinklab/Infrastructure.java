package com.thinklab;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The real infrastructure for the integration suite, started once per JVM (Testcontainers' Ryuk sidecar
 * removes it on exit): MongoDB as a single-node replica set and NATS JetStream.
 */
public final class Infrastructure {

    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0")).withReplicaSet();

    static final GenericContainer<?> NATS = new GenericContainer<>(DockerImageName.parse("nats:2.10-alpine"))
            .withCommand("-js")
            .withExposedPorts(4222)
            .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1));

    static {
        MONGO.start();
        NATS.start();
    }

    private Infrastructure() {
    }

    public static String mongoUri(String database) {
        return MONGO.getReplicaSetUrl(database);
    }

    public static String natsUrl() {
        return "nats://" + NATS.getHost() + ":" + NATS.getMappedPort(4222);
    }
}
