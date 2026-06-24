package com.hireme.reviewer.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireme.reviewer.config.AppConfig;
import com.hireme.reviewer.exception.AiReviewException;
import com.hireme.reviewer.exception.HttpRequestException;
import com.hireme.reviewer.http.HttpRequestExecutor;
import com.hireme.reviewer.model.Repository;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hireme.reviewer.exception.AiReviewException.Reason.API_ERROR;
import static com.hireme.reviewer.exception.AiReviewException.Reason.AUTHENTICATION_FAILED;
import static com.hireme.reviewer.exception.AiReviewException.Reason.INVALID_REQUEST;
import static com.hireme.reviewer.exception.AiReviewException.Reason.NETWORK_ERROR;
import static com.hireme.reviewer.exception.AiReviewException.Reason.RATE_LIMITED;

public abstract class AbstractAiReviewClient implements AiReviewClient {
	
    private final HttpRequestExecutor http;
    protected final AppConfig.AiSettings settings;
    protected final ObjectMapper json;

    protected AbstractAiReviewClient(
            HttpRequestExecutor http,
            AppConfig.AiSettings settings,
            ObjectMapper json) {
        this.http = http;
        this.settings = settings;
        this.json = json;
    }

    @Override
    public final BatchResult review(List<Repository> repositories) {
        validate(repositories);
        String input = buildInput(repositories);
        HttpRequest request = createRequest(settings.systemPrompt(), input, repositories.size());
        HttpRequestExecutor.Response response = execute(request);
        ensureSuccess(response);
        Set<String> expectedIds = repositories.stream()
                .map(Repository::fullName)
                .collect(Collectors.toUnmodifiableSet());
        return parseResponse(response.body(), expectedIds);
    }

    protected abstract HttpRequest createRequest(String systemPrompt, String input, int repositoryCount);

    protected abstract BatchResult parseResponse(String responseBody, Set<String> expectedIds);

    protected abstract String readProviderError(String responseBody);

    private void validate(List<Repository> repositories) {
        if (repositories == null || repositories.isEmpty()) {
            throw new IllegalArgumentException("At least one repository is required.");
        }
        if (repositories.stream().anyMatch(repository -> !repository.hasReadme())) {
            throw new IllegalArgumentException("Every repository sent to AI must have a README.");
        }
    }

    private String buildInput(List<Repository> repositories) {
        StringBuilder input = new StringBuilder("Review the following repositories:\n\n");
        for (Repository repository : repositories) {
            appendRepository(input, repository);
        }
        return input.toString();
    }

    private void appendRepository(StringBuilder input, Repository repository) {
        input.append("repositoryId: ").append(repository.fullName()).append('\n')
                .append("name: ").append(repository.name()).append('\n')
                .append("description: ").append(emptyAsUnknown(repository.description())).append('\n')
                .append("primaryLanguage: ").append(emptyAsUnknown(repository.primaryLanguage())).append('\n')
                .append("fork: ").append(repository.fork()).append('\n')
                .append("archived: ").append(repository.archived()).append('\n')
                .append("readme:\n<<<README\n")
                .append(normalize(repository.readme()))
                .append("\nREADME>>>\n\n");
    }

    private String normalize(String readme) {
        return readme.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("(?s)<!--.*?-->", "")
                .replaceAll("!\\[([^]]*)]\\([^)]*\\)", "$1")
                .replaceAll("(?m)[ \\t]+$", "")
                .replaceAll("\\n{4,}", "\n\n\n")
                .trim();
    }

    private String emptyAsUnknown(String value) {
        return value == null || value.isBlank() ? "Not provided" : value;
    }

    private HttpRequestExecutor.Response execute(HttpRequest request) {
        try {
            return http.executeOnce(request);
        } catch (HttpRequestException exception) {
            throw new AiReviewException(
                    NETWORK_ERROR,
                    "AI request failed: " + exception.getMessage(),
                    exception,
                    exception.retryable());
        }
    }

    private void ensureSuccess(HttpRequestExecutor.Response response) {
        if (response.successful()) {
            return;
        }

        String providerMessage = readProviderError(response.body());
        String message = providerMessage.isBlank()
                ? "AI provider returned HTTP " + response.statusCode() + "."
                : providerMessage;

        throw switch (response.statusCode()) {
            case 401, 403 -> new AiReviewException(AUTHENTICATION_FAILED, message, false);
            case 429 -> new AiReviewException(RATE_LIMITED, message, true);
            case 400, 404 -> new AiReviewException(INVALID_REQUEST, message, false);
            case 500, 502, 503, 504 -> new AiReviewException(API_ERROR, message, true);
            default -> new AiReviewException(API_ERROR, message, false);
        };
    }
}
