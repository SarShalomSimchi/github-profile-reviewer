package com.hireme.reviewer.http;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;

import com.hireme.reviewer.config.AppConfig;
import com.hireme.reviewer.exception.HttpRequestException;

public final class HttpRequestExecutor {
    private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(429, 500, 502, 503, 504);
    private static final long MAX_RETRY_DELAY_MILLIS = 30_000;

    private final HttpClient httpClient;
    private final int maxAttempts;
    private final long initialRetryDelayMillis;

    public HttpRequestExecutor(AppConfig.HttpSettings settings) {
        this(HttpClient.newBuilder()
                .connectTimeout(settings.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_2)
                .build(), settings.maxAttempts(), settings.initialRetryDelayMillis());
    }

    HttpRequestExecutor(HttpClient httpClient, int maxAttempts, long initialRetryDelayMillis) {
        this.httpClient = httpClient;
        this.maxAttempts = maxAttempts;
        this.initialRetryDelayMillis = initialRetryDelayMillis;
    }

    public Response execute(HttpRequest request) {
        return execute(request, maxAttempts);
    }

    public Response executeOnce(HttpRequest request) {
        return execute(request, 1);
    }

    private Response execute(HttpRequest request, int allowedAttempts) {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= allowedAttempts; attempt++) {
            try {
                Response response = send(request, attempt);
                if (!shouldRetry(response.statusCode(), attempt, allowedAttempts)) {
                    return response;
                }
                waitBeforeRetry(response.headers(), attempt);
            } catch (IOException exception) {
                lastFailure = exception;
                if (attempt == allowedAttempts) {
                    break;
                }
                waitBeforeRetry(HttpHeaders.of(java.util.Map.of(), (a, b) -> true), attempt);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new HttpRequestException("HTTP request was interrupted.", exception, false);
            }
        }
        throw new HttpRequestException("HTTP request failed after " + allowedAttempts + " attempt(s).", lastFailure, true);
    }

    private Response send(HttpRequest request, int attempt) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body(), response.headers(), attempt);
    }

    private boolean shouldRetry(int statusCode, int attempt, int allowedAttempts) {
        return attempt < allowedAttempts && RETRYABLE_STATUS_CODES.contains(statusCode);
    }

    private void waitBeforeRetry(HttpHeaders headers, int attempt) {
        long delay = retryAfterMillis(headers).orElseGet(() -> exponentialDelay(attempt));
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new HttpRequestException("Retry delay was interrupted.", exception, false);
        }
    }

    private java.util.OptionalLong retryAfterMillis(HttpHeaders headers) {
        return headers.firstValue("Retry-After")
                .flatMap(this::parseSeconds)
                .stream()
                .mapToLong(Long::longValue)
                .map(seconds -> Math.min(seconds * 1_000, MAX_RETRY_DELAY_MILLIS))
                .findFirst();
    }

    private java.util.Optional<Long> parseSeconds(String value) {
        try {
            return java.util.Optional.of(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
        }
    }

    private long exponentialDelay(int attempt) {
        long multiplier = 1L << Math.min(attempt - 1, 5);
        return Math.min(initialRetryDelayMillis * multiplier, MAX_RETRY_DELAY_MILLIS);
    }

    public record Response(int statusCode, String body, HttpHeaders headers, int attemptsUsed) {
        public boolean successful() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
