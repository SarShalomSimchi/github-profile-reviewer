package com.hireme.reviewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireme.reviewer.ai.AiReviewClient;
import com.hireme.reviewer.config.AppConfig;
import com.hireme.reviewer.github.GitHubClient;
import com.hireme.reviewer.http.HttpRequestExecutor;
import com.hireme.reviewer.model.ProfileReview;
import com.hireme.reviewer.model.RepositoryReview;
import com.hireme.reviewer.testutil.StubHttpServer;


class ReviewServiceTest {
    @Test
    void retriesOnlyTheMissingRepository() {
        try (var server = githubServer()) {
            RecordingAiClient ai = new RecordingAiClient();
            ReviewService service = service(server.baseUrl(), ai, 120000);

            ProfileReview profile = service.review("octocat");

            assertEquals(List.of(2, 1), ai.batchSizes);
            assertEquals(List.of(
                    RepositoryReview.Status.SUCCESS,
                    RepositoryReview.Status.SUCCESS,
                    RepositoryReview.Status.NO_README),
                    profile.repositories().stream().map(RepositoryReview::status).toList());
            assertEquals(1, profile.repositories().get(0).attempts());
            assertEquals(2, profile.repositories().get(1).attempts());
            assertTrue(ai.calledOnVirtualThread.get());
        }
    }

    @Test
    void marksOnlyFailedRepositoryAsErrorAndContinues() {
        try (var server = githubServer()) {
            AiReviewClient ai = repositories -> {
                Map<String, AiReviewClient.ReviewDetails> reviews = new LinkedHashMap<>();
                Map<String, AiReviewClient.ItemError> errors = new LinkedHashMap<>();
                for (var repository : repositories) {
                    if (repository.fullName().endsWith("one")) {
                        reviews.put(repository.fullName(), success(repository.fullName()));
                    } else {
                        errors.put(repository.fullName(), new AiReviewClient.ItemError(
                                repository.fullName(), "Provider could not review this README.", false));
                    }
                }
                return new AiReviewClient.BatchResult(reviews, errors);
            };

            ProfileReview profile = service(server.baseUrl(), ai, 120000).review("octocat");

            assertEquals(RepositoryReview.Status.SUCCESS, profile.repositories().get(0).status());
            assertEquals(RepositoryReview.Status.ERROR, profile.repositories().get(1).status());
            assertTrue(profile.repositories().get(1).errorMessage().contains("Provider could not review"));
            assertEquals(RepositoryReview.Status.NO_README, profile.repositories().get(2).status());
        }
    }

    @Test
    void doesNotTruncateReadmeWhenCreatingSmallBatches() {
        try (var server = githubServer()) {
            AtomicReference<String> receivedReadme = new AtomicReference<>();

            AiReviewClient ai = repositories -> {
                Map<String, AiReviewClient.ReviewDetails> reviews =
                        new LinkedHashMap<>();

                repositories.forEach(repository -> {
                    if (repository.readme().contains("TAIL-OF-README")) {
                        receivedReadme.set(repository.readme());
                    }

                    reviews.put(
                            repository.fullName(),
                            success(repository.fullName()));
                });

                return new AiReviewClient.BatchResult(
                        reviews,
                        Map.of());
            };

            service(server.baseUrl(), ai, 5)
                    .review("octocat");

            assertNotNull(
                    receivedReadme.get(),
                    "The README containing the expected tail was not sent to the AI client.");

            assertTrue(
                    receivedReadme.get().endsWith("TAIL-OF-README"),
                    "The README was truncated before its final content.");
        }
    }

    private ReviewService service(String baseUrl, AiReviewClient ai, int maxBatchCharacters) {
        var githubSettings = new AppConfig.GitHubSettings(baseUrl, "", Duration.ofSeconds(2));
        var httpSettings = new AppConfig.HttpSettings(Duration.ofSeconds(1), 2, 1);
        var github = new GitHubClient(githubSettings, new HttpRequestExecutor(httpSettings), new ObjectMapper());
        var aiSettings = new AppConfig.AiSettings(
                "gemini",
                baseUrl,
                "key",
                "model",
                "Review every repository independently and return JSON only.",
                Duration.ofSeconds(2),
                5,
                maxBatchCharacters,
                220,
                2,
                "minimal",
                2);
        return new ReviewService(github, ai, aiSettings, new AppConfig.ProcessingSettings(4));
    }

    private StubHttpServer githubServer() {
        StubHttpServer server = new StubHttpServer();
        server.handle("/users/octocat/repos", exchange -> StubHttpServer.respond(exchange, 200, """
                [
                  {"name":"one","full_name":"octocat/one","description":"one","html_url":"https://github.com/octocat/one","language":"Java","fork":false,"archived":false},
                  {"name":"two","full_name":"octocat/two","description":"two","html_url":"https://github.com/octocat/two","language":"Java","fork":false,"archived":false},
                  {"name":"three","full_name":"octocat/three","description":"three","html_url":"https://github.com/octocat/three","language":"Java","fork":false,"archived":false}
                ]
                """));
        server.handle("/repos/octocat/one/readme", exchange -> StubHttpServer.respond(exchange, 200, "README one TAIL-OF-README"));
        server.handle("/repos/octocat/two/readme", exchange -> StubHttpServer.respond(exchange, 200, "README two"));
        server.handle("/repos/octocat/three/readme", exchange -> StubHttpServer.respond(exchange, 404, "{}"));
        return server;
    }

    private static AiReviewClient.ReviewDetails success(String repositoryId) {
        return new AiReviewClient.ReviewDetails(
                repositoryId,
                AiReviewClient.ReviewLevel.INTERMEDIATE,
                "Summary",
                "Clear",
                "Practical experience",
                List.of("Evidence"),
                0.8);
    }

    private static final class RecordingAiClient implements AiReviewClient {
        private final List<Integer> batchSizes = new ArrayList<>();
        private final AtomicBoolean calledOnVirtualThread = new AtomicBoolean();
        private int call;

        @Override
        public BatchResult review(List<com.hireme.reviewer.model.Repository> repositories) {
            calledOnVirtualThread.set(calledOnVirtualThread.get() || Thread.currentThread().isVirtual());
            batchSizes.add(repositories.size());
            call++;

            if (call == 1) {
                String first = repositories.getFirst().fullName();
                String second = repositories.get(1).fullName();
                return new BatchResult(
                        Map.of(first, success(first)),
                        Map.of(second, new ItemError(second, "Missing result", true)));
            }

            String id = repositories.getFirst().fullName();
            return new BatchResult(Map.of(id, success(id)), Map.of());
        }
    }
}
