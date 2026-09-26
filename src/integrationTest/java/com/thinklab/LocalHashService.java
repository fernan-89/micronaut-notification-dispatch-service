package com.thinklab;

import com.thinklab.domain.port.HashServicePort;
import com.thinklab.infrastructure.adapter.out.integration.hashservice.HashServiceAdapter;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Stands in for the Hash Token Registry in the integration suite: ids are random UUIDs. */
@Singleton
@Replaces(HashServiceAdapter.class)
class LocalHashService implements HashServicePort {

    @Override
    public Mono<UUID> generateSovereignId(String context) {
        return Mono.just(UUID.randomUUID());
    }

    @Override
    public Mono<String> hashSensitiveData(String rawData) {
        return Mono.just("hashed:" + rawData);
    }
}
