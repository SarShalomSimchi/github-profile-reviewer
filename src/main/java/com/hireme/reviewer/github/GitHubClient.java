package com.hireme.reviewer.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireme.reviewer.config.AppConfig;
import com.hireme.reviewer.exception.GitHubException;
import com.hireme.reviewer.exception.HttpRequestException;
import com.hireme.reviewer.http.HttpRequestExecutor;
import com.hireme.reviewer.model.Repository;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.hireme.reviewer.exception.GitHubException.Reason.API_ERROR;
import static com.hireme.reviewer.exception.GitHubException.Reason.INVALID_RESPONSE;
import static com.hireme.reviewer.exception.GitHubException.Reason.RATE_LIMITED;
import static com.hireme.reviewer.exception.GitHubException.Reason.USER_NOT_FOUND;

public final class GitHubClient {
    private static final int REPOSITORIES_PER_PAGE = 100;
    private static final String API_VERSION = "2022-11-28";
    private static final String JSON_ACCEPT = "application/vnd.github+json";
    private static final String RAW_ACCEPT = "application/vnd.github.raw+json";
    private static final String USER_AGENT = "github-profile-reviewer";

    private static final int HTTP_FORBIDDEN = 403;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private final AppConfig.GitHubSettings settings;
    private final HttpRequestExecutor http;
    private final ObjectMapper json;

    public GitHubClient(
            AppConfig.GitHubSettings settings,
            HttpRequestExecutor http,
            ObjectMapper json) {
        this.settings = settings;
        this.http = http;
        this.json = json;
    }

    public List<Repository> listRepositories(String username) {
        var repositories = new ArrayList<Repository>();
        for (int page = 1; ; page++) {
            List<Repository> currentPage = loadRepositoryPage(username, page);
            repositories.addAll(currentPage);
            if (currentPage.size() < REPOSITORIES_PER_PAGE) {
                return List.copyOf(repositories);
            }
        }
    }
    
    private List<Repository> loadRepositoryPage(String username, int page) {
    	HttpRequest request = request(repositoriesUri(username, page), JSON_ACCEPT).GET().build();
    	HttpRequestExecutor.Response response = execute(request, "Could not fetch repositories for " + username);
    	
    	if (response.statusCode() == HTTP_NOT_FOUND) {
    		throw new GitHubException(USER_NOT_FOUND, "GitHub user was not found: " + username);
    	}
    	ensureSuccess(response, "Could not fetch repositories for " + username);
    	return parseRepositories(response.body());
    }
    
    private HttpRequest.Builder request(URI uri, String accept) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(settings.requestTimeout())
                .header("Accept", accept)
                .header("X-GitHub-Api-Version", API_VERSION)
                .header("User-Agent", USER_AGENT);
        if (!settings.token().isBlank()) {
            builder.header("Authorization", "Bearer " + settings.token());
        }
        return builder;
    }
    
    private URI repositoriesUri(String username, int page) {
        String path = "/users/" + encode(username) + "/repos"
                + "?type=owner&sort=updated&direction=desc&per_page=" + REPOSITORIES_PER_PAGE
                + "&page=" + page;
        return URI.create(trimTrailingSlash(settings.apiBaseUrl()) + path);
    }
    
    private HttpRequestExecutor.Response execute(HttpRequest request, String message) {
        try {
            return http.execute(request);
        } catch (HttpRequestException exception) {
            throw new GitHubException(API_ERROR, message + ": " + exception.getMessage(), exception);
        }
    }

    private List<Repository> parseRepositories(String body) {
        try {
            JsonNode root = json.readTree(body);
            if (!root.isArray()) {
                throw new GitHubException(INVALID_RESPONSE, "GitHub returned an unexpected repositories response.");
            }

            var repositories = new ArrayList<Repository>();
            for (JsonNode node : root) {
                repositories.add(new Repository(
                        requiredText(node, "name"),
                        requiredText(node, "full_name"),
                        text(node, "description"),
                        requiredText(node, "html_url"),
                        text(node, "language"),
                        node.path("fork").asBoolean(false),
                        node.path("archived").asBoolean(false),
                        null));
            }
            return repositories;
        } catch (GitHubException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GitHubException(INVALID_RESPONSE, "Could not parse GitHub repositories.", exception);
        }
    }
    
    public Optional<String> loadReadme(Repository repository) {
        HttpRequest request = request(readmeUri(repository.fullName()), RAW_ACCEPT).GET().build();
        HttpRequestExecutor.Response response = execute(request, "Could not fetch README for " + repository.fullName());

        if (response.statusCode() == HTTP_NOT_FOUND) {
            return Optional.empty();
        }
        ensureSuccess(response, "Could not fetch README for " + repository.fullName());
        return Optional.ofNullable(response.body());
    }
    
    private URI readmeUri(String fullName) {
        String[] parts = fullName.split("/", 2);
        if (parts.length != 2) {
            throw new GitHubException(INVALID_RESPONSE, "Invalid repository name returned by GitHub: " + fullName);
        }
        String path = "/repos/" + encode(parts[0]) + "/" + encode(parts[1]) + "/readme";
        return URI.create(trimTrailingSlash(settings.apiBaseUrl()) + path);
    }

    private void ensureSuccess(HttpRequestExecutor.Response response, String message) {
        if (response.successful()) {
            return;
        }
        if (isRateLimited(response)) {
            throw new GitHubException(RATE_LIMITED, "GitHub API rate limit was reached.");
        }
        throw new GitHubException(API_ERROR, message + " (HTTP " + response.statusCode() + ").");
    }

    private boolean isRateLimited(HttpRequestExecutor.Response response) {
        return response.statusCode() == HTTP_TOO_MANY_REQUESTS
                || response.statusCode() == HTTP_FORBIDDEN
                && response.headers().firstValue("X-RateLimit-Remaining").orElse("1").equals("0");
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value.isBlank()) {
            throw new GitHubException(INVALID_RESPONSE, "GitHub response is missing field: " + field);
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
