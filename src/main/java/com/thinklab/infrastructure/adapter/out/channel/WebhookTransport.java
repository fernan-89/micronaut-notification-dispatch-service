package com.thinklab.infrastructure.adapter.out.channel;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Minimal blocking HTTP seam for {@link WebhookNotificationChannel}, purely so a broker/network failure
 * is unit-testable without a real server — same shape as the kit's {@code HttpTransport}, but local to
 * this service since it is single-consumer business logic, not a cross-cutting concern (kit ADR-001).
 */
public interface WebhookTransport {

    void postJson(String url, String jsonBody) throws IOException, InterruptedException;

    class Default implements WebhookTransport {

        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

        @Override
        public void postJson(String url, String jsonBody) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("HTTP " + response.statusCode() + " from " + url);
            }
        }
    }
}
