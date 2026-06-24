package com.hireme.reviewer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireme.reviewer.config.AppConfig;
import com.hireme.reviewer.http.HttpRequestExecutor;
import com.hireme.reviewer.model.Repository;
import com.hireme.reviewer.testutil.StubHttpServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiReviewClientTest {
	@Test
	void sendsFullReadmeAndParsesStructuredReview() throws Exception {
	    AtomicReference<String> requestBody = new AtomicReference<>();

		try (var server = new StubHttpServer()) {
			server.handle("/models/gemini-3.1-flash-lite:generateContent", exchange -> {
				assertEquals("test-key", exchange.getRequestHeaders().getFirst("x-goog-api-key"));

				requestBody.set(StubHttpServer.body(exchange));

				StubHttpServer.respond(exchange, 200, geminiResponse("""
						{"reviews":[{
						  "repositoryId":"octocat/demo",
						  "level":"INTERMEDIATE",
						  "summary":"A documented backend service.",
						  "readmeQuality":"Clear and concise.",
						  "experienceSignal":"Shows practical API experience.",
						  "evidence":["Uses persistence","Documents tests"],
						  "confidence":0.82
						}]}
						"""));
			});

			GeminiReviewClient client = client(server.baseUrl());
			Repository repository = repository("octocat/demo", "README-START\nimportant middle\nREADME-END");
			AiReviewClient.BatchResult result = client.review(List.of(repository));
			AiReviewClient.ReviewDetails review = result.reviews().get("octocat/demo");
			assertEquals(AiReviewClient.ReviewLevel.INTERMEDIATE, review.level());
			assertEquals(List.of("Uses persistence", "Documents tests"), review.evidence());
			assertTrue(requestBody.get().contains("README-START"));
			assertTrue(requestBody.get().contains("README-END"));
			JsonNode requestJson = new ObjectMapper().readTree(requestBody.get());
			JsonNode config = requestJson.path("generationConfig");
			assertEquals("application/json", config.path("responseMimeType").asText());
			assertEquals("MINIMAL", config.path("thinkingConfig").path("thinkingLevel").asText());
			assertFalse(config.has("temperature"));
			assertTrue(config.has("responseJsonSchema"));
		}
	}

    @Test
    void returnsItemErrorWhenOneRepositoryIsMissing() {
        try (var server = new StubHttpServer()) {
            server.handle("/models/gemini-3.1-flash-lite:generateContent", exchange ->
                    StubHttpServer.respond(exchange, 200, geminiResponse("""
                            {"reviews":[{
                              "repositoryId":"octocat/one",
                              "level":"BASIC",
                              "summary":"Small project.",
                              "readmeQuality":"Clear.",
                              "experienceSignal":"Shows basic skills.",
                              "evidence":["Simple documented scope"],
                              "confidence":0.70
                            }]}
                            """)));

            AiReviewClient.BatchResult result = client(server.baseUrl()).review(List.of(
                    repository("octocat/one", "one"),
                    repository("octocat/two", "two")));

            assertTrue(result.reviews().containsKey("octocat/one"));
            assertTrue(result.errors().containsKey("octocat/two"));
            assertTrue(result.errors().get("octocat/two").retryable());
        }
    }

    @Test
    void keepsImageAltTextButRemovesImageUrl() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        try (var server = new StubHttpServer()) {
            server.handle("/models/gemini-3.1-flash-lite:generateContent", exchange -> {
                requestBody.set(StubHttpServer.body(exchange));
                StubHttpServer.respond(exchange, 200, geminiResponse("""
                        {"reviews":[{
                          "repositoryId":"octocat/demo",
                          "level":"BASIC",
                          "summary":"Small project.",
                          "readmeQuality":"Clear.",
                          "experienceSignal":"Shows basics.",
                          "evidence":[],
                          "confidence":0.60
                        }]}
                        """));
            });

            client(server.baseUrl()).review(List.of(repository(
                    "octocat/demo",
                    "![Build passing](https://example.com/a/very/long/image.svg)")));

            assertTrue(requestBody.get().contains("Build passing"));
            assertFalse(requestBody.get().contains("https://example.com/a/very/long/image.svg"));
        }
    }

    private GeminiReviewClient client(String baseUrl) {
    	var ai = new AppConfig.AiSettings(
    	        "gemini",
    	        baseUrl,
    	        "test-key",
    	        "gemini-3.1-flash-lite",
    	        "Review every repository independently and return JSON only.",
    	        Duration.ofSeconds(2),
    	        5,
    	        120000,
    	        220,
    	        2,
    	        "minimal",
    	        1);
        var http = new HttpRequestExecutor(new AppConfig.HttpSettings(Duration.ofSeconds(1), 2, 1));
        return new GeminiReviewClient(http, ai, new ObjectMapper());
    }

    private Repository repository(String fullName, String readme) {
        String name = fullName.substring(fullName.indexOf('/') + 1);
        return new Repository(name, fullName, "Description", "https://github.com/" + fullName, "Java", false, false, readme);
    }

    private String geminiResponse(String jsonText) {
        try {
            return new ObjectMapper().writeValueAsString(java.util.Map.of(
                    "candidates", List.of(java.util.Map.of(
                            "content", java.util.Map.of(
                                    "parts", List.of(java.util.Map.of("text", jsonText)))))));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
