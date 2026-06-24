package com.hireme.reviewer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hireme.reviewer.config.AppConfig;
import com.hireme.reviewer.exception.AiReviewException;
import com.hireme.reviewer.http.HttpRequestExecutor;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hireme.reviewer.exception.AiReviewException.Reason.INVALID_RESPONSE;

public final class GeminiReviewClient extends AbstractAiReviewClient {
    private static final String CONTENT_TYPE = "application/json";
    private static final String API_KEY_HEADER = "x-goog-api-key";
    private static final int OUTPUT_OVERHEAD_TOKENS = 128;
	private static final int MAX_EVIDENCE_ITEMS = 3;
	private static final double MIN_CONFIDENCE = 0.0;
	private static final double MAX_CONFIDENCE = 1.0;
	private static final double MISSING_CONFIDENCE = -1.0;
	private static final String FIELD_REVIEWS = "reviews";
	private static final String FIELD_REPOSITORY_ID = "repositoryId";
	private static final String FIELD_LEVEL = "level";
	private static final String FIELD_SUMMARY = "summary";
	private static final String FIELD_README_QUALITY = "readmeQuality";
	private static final String FIELD_EXPERIENCE_SIGNAL = "experienceSignal";
	private static final String FIELD_EVIDENCE = "evidence";
	private static final String FIELD_CONFIDENCE = "confidence";

    public GeminiReviewClient(
            HttpRequestExecutor http,
            AppConfig.AiSettings settings,
            ObjectMapper json) {
        super(http, settings, json);
    }

    @Override
    protected HttpRequest createRequest(String systemPrompt, String input, int repositoryCount) {
        try {
            ObjectNode body = json.createObjectNode();
            body.set("systemInstruction", content(systemPrompt));

            ArrayNode contents = body.putArray("contents");
            ObjectNode userContent = contents.addObject();
            userContent.put("role", "user");
            userContent.putArray("parts").addObject().put("text", input);

            ObjectNode generationConfig = body.putObject("generationConfig");
            generationConfig.put("responseMimeType", CONTENT_TYPE);
            generationConfig.set("responseJsonSchema", responseSchema());
            generationConfig.put("maxOutputTokens", outputTokenLimit(repositoryCount));
            generationConfig.putObject("thinkingConfig")
                    .put("thinkingLevel", settings.thinkingLevel().toUpperCase());

            return HttpRequest.newBuilder(endpoint())
                    .timeout(settings.requestTimeout())
                    .header("Content-Type", CONTENT_TYPE)
                    .header(API_KEY_HEADER, settings.apiKey())
                    .POST(BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
        } catch (Exception exception) {
            throw new AiReviewException(
                    AiReviewException.Reason.INVALID_REQUEST,
                    "Could not build Gemini request.",
                    exception,
                    false);
        }
    }

    @Override
    protected BatchResult parseResponse(String responseBody, Set<String> expectedIds) {
        try {
            String resultJson = extractText(responseBody);
            JsonNode root = json.readTree(resultJson);
            JsonNode reviewsNode = root.path(FIELD_REVIEWS);
            if (!reviewsNode.isArray()) {
                throw invalidResponse("Gemini response does not contain a reviews array.");
            }
            return parseReviews(reviewsNode, expectedIds);
        } catch (AiReviewException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiReviewException(
                    INVALID_RESPONSE,
                    "Could not parse Gemini response.",
                    exception,
                    true);
        }
    }

    @Override
    protected String readProviderError(String responseBody) {
        try {
            return json.readTree(responseBody).path("error").path("message").asText("");
        } catch (Exception ignored) {
            return "";
        }
    }

    private ObjectNode content(String text) {
        ObjectNode content = json.createObjectNode();
        content.putArray("parts").addObject().put("text", text);
        return content;
    }

    private URI endpoint() {
        String baseUrl = trimTrailingSlash(settings.apiBaseUrl());
        String model = URLEncoder.encode(settings.model(), StandardCharsets.UTF_8).replace("+", "%20");
        return URI.create(baseUrl + "/models/" + model + ":generateContent");
    }

    private int outputTokenLimit(int repositoryCount) {
        return Math.max(512, repositoryCount * settings.maxOutputTokensPerRepository() + OUTPUT_OVERHEAD_TOKENS);
    }

    private ObjectNode responseSchema() {
        ObjectNode review = objectSchema();
        ObjectNode properties = (ObjectNode) review.get("properties");
        properties.set(FIELD_REPOSITORY_ID, stringSchema("The exact repositoryId from the input."));
        properties.set(FIELD_LEVEL, enumSchema("BASIC", "INTERMEDIATE", "ADVANCED", "INSUFFICIENT_EVIDENCE"));
        properties.set(FIELD_SUMMARY, stringSchema("Concise overall project review."));
        properties.set(FIELD_README_QUALITY, stringSchema("Concise review of README clarity and completeness."));
        properties.set(FIELD_EXPERIENCE_SIGNAL, stringSchema("Concise statement about demonstrated developer experience."));
        properties.set(FIELD_EVIDENCE, arraySchema(stringSchema("Short evidence grounded in the supplied content."), 3));
        properties.set(FIELD_CONFIDENCE, numberSchema(MIN_CONFIDENCE, MAX_CONFIDENCE));
        review.putArray("required")
                .add(FIELD_REPOSITORY_ID)
                .add(FIELD_LEVEL)
                .add(FIELD_SUMMARY)
                .add(FIELD_README_QUALITY)
                .add(FIELD_EXPERIENCE_SIGNAL)
                .add(FIELD_EVIDENCE)
                .add(FIELD_CONFIDENCE);

        ObjectNode root = objectSchema();
        ((ObjectNode) root.get("properties")).set(FIELD_REVIEWS, arraySchema(review, null));
        root.putArray("required").add(FIELD_REVIEWS);
        return root;
    }

    private ObjectNode objectSchema() {
        ObjectNode schema = json.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode stringSchema(String description) {
        ObjectNode schema = json.createObjectNode();
        schema.put("type", "string");
        schema.put("description", description);
        return schema;
    }

    private ObjectNode enumSchema(String... values) {
        ObjectNode schema = stringSchema("One allowed project level.");
        ArrayNode allowed = schema.putArray("enum");
        for (String value : values) {
            allowed.add(value);
        }
        return schema;
    }

    private ObjectNode arraySchema(JsonNode itemSchema, Integer maxItems) {
        ObjectNode schema = json.createObjectNode();
        schema.put("type", "array");
        schema.set("items", itemSchema);
        if (maxItems != null) {
            schema.put("maxItems", maxItems);
        }
        return schema;
    }

    private ObjectNode numberSchema(double minimum, double maximum) {
        ObjectNode schema = json.createObjectNode();
        schema.put("type", "number");
        schema.put("minimum", minimum);
        schema.put("maximum", maximum);
        return schema;
    }

    private String extractText(String responseBody) throws Exception {
        JsonNode root = json.readTree(responseBody);
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray()) {
            throw invalidResponse("Gemini response does not contain generated content.");
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode part : parts) {
            if (part.hasNonNull("text")) {
                text.append(part.get("text").asText());
            }
        }
        if (text.isEmpty()) {
            throw invalidResponse("Gemini returned an empty response.");
        }
        return text.toString();
    }

    private BatchResult parseReviews(JsonNode reviewsNode, Set<String> expectedIds) {
        Map<String, ReviewDetails> reviews = new LinkedHashMap<>();
        Map<String, ItemError> errors = new LinkedHashMap<>();

        for (JsonNode node : reviewsNode) {
            String repositoryId = node.path(FIELD_REPOSITORY_ID).asText("");
            if (!expectedIds.contains(repositoryId) 
            		|| reviews.containsKey(repositoryId) 
            		|| errors.containsKey(repositoryId)) {
            	continue; 
            }
            
            try {
                reviews.put(repositoryId, parseReview(node, repositoryId));
            } catch (RuntimeException exception) {
                errors.put(repositoryId, new ItemError(repositoryId, 
                		"Invalid AI result: " + exception.getMessage(), 
                		true));
            }
        }

        for (String repositoryId : expectedIds) {
            if (!reviews.containsKey(repositoryId) && !errors.containsKey(repositoryId)) {
                errors.put(repositoryId, new ItemError(repositoryId, "Gemini did not return a result.", true));
        	}
        }
        return new BatchResult(reviews, errors);
    }

    private ReviewDetails parseReview(JsonNode node, String repositoryId) {
        ReviewLevel level = ReviewLevel.valueOf(requiredText(node, FIELD_LEVEL));
        List<String> evidence = new ArrayList<>();
        JsonNode evidenceNode = node.path(FIELD_EVIDENCE);
        if (!evidenceNode.isArray()) {
            throw new IllegalArgumentException("evidence must be an array");
        }
        for (JsonNode item : evidenceNode) {
            evidence.add(item.asText(""));
        }
        if (evidence.size() > MAX_EVIDENCE_ITEMS) {
            evidence = new ArrayList<>(evidence.subList(0, MAX_EVIDENCE_ITEMS));
        }

        double confidence = node.path(FIELD_CONFIDENCE).asDouble(MISSING_CONFIDENCE);
        if (confidence < MIN_CONFIDENCE
                || confidence > MAX_CONFIDENCE) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }

        return new ReviewDetails(
                repositoryId,
                level,
                requiredText(node, FIELD_SUMMARY),
                requiredText(node, FIELD_README_QUALITY),
                requiredText(node, FIELD_EXPERIENCE_SIGNAL),
                evidence,
                confidence);
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " is missing");
        }
        return value;
    }

    private AiReviewException invalidResponse(String message) {
        return new AiReviewException(INVALID_RESPONSE, message, true);
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
