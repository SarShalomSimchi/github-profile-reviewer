package com.hireme.reviewer.http;

import com.hireme.reviewer.testutil.StubHttpServer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpRequestExecutorTest {
    @Test
    void retriesTemporaryStatusAndReturnsSuccessfulResponse() {
        AtomicInteger calls = new AtomicInteger();
        try (var server = new StubHttpServer()) {
            server.handle("/retry", exchange -> {
                int call = calls.incrementAndGet();
                StubHttpServer.respond(exchange, call == 1 ? 503 : 200, "ok");
            });

            var executor = new HttpRequestExecutor(
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                    2,
                    1);
            HttpRequest request = HttpRequest.newBuilder(URI.create(server.baseUrl() + "/retry")).GET().build();

            HttpRequestExecutor.Response response = executor.execute(request);

            assertEquals(200, response.statusCode());
            assertEquals(2, response.attemptsUsed());
            assertEquals(2, calls.get());
        }
    }

    @Test
    void executeOnceDoesNotRetry() {
        AtomicInteger calls = new AtomicInteger();
        try (var server = new StubHttpServer()) {
            server.handle("/once", exchange -> {
                calls.incrementAndGet();
                StubHttpServer.respond(exchange, 503, "temporary");
            });

            var executor = new HttpRequestExecutor(HttpClient.newHttpClient(), 3, 1);
            HttpRequest request = HttpRequest.newBuilder(URI.create(server.baseUrl() + "/once")).GET().build();

            HttpRequestExecutor.Response response = executor.executeOnce(request);

            assertEquals(503, response.statusCode());
            assertEquals(1, calls.get());
        }
    }
}
