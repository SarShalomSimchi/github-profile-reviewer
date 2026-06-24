package com.hireme.reviewer.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireme.reviewer.config.AppConfig;
import com.hireme.reviewer.exception.GitHubException;
import com.hireme.reviewer.http.HttpRequestExecutor;
import com.hireme.reviewer.model.Repository;
import com.hireme.reviewer.testutil.StubHttpServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubClientTest {
    @Test
    void loadsRepositoriesAndRawReadme() {
        try (var server = new StubHttpServer()) {
            server.handle("/users/octocat/repos", exchange -> StubHttpServer.respond(exchange, 200, """
                    [{
                      "name":"demo",
                      "full_name":"octocat/demo",
                      "description":"Demo repository",
                      "html_url":"https://github.com/octocat/demo",
                      "language":"Java",
                      "fork":false,
                      "archived":false
                    }]
                    """));
            server.handle("/repos/octocat/demo/readme", exchange -> {
                assertEquals("application/vnd.github.raw+json", exchange.getRequestHeaders().getFirst("Accept"));
                StubHttpServer.respond(exchange, 200, "# Demo");
            });

            GitHubClient client = client(server.baseUrl());
            List<Repository> repositories = client.listRepositories("octocat");

            assertEquals(1, repositories.size());
            assertEquals("# Demo", client.loadReadme(repositories.getFirst()).orElseThrow());
        }
    }

    @Test
    void returnsEmptyWhenReadmeDoesNotExist() {
        try (var server = new StubHttpServer()) {
            server.handle("/repos/octocat/demo/readme", exchange -> StubHttpServer.respond(exchange, 404, "{}"));
            Repository repository = new Repository("demo", "octocat/demo", "", "url", "", false, false, null);
            assertTrue(client(server.baseUrl()).loadReadme(repository).isEmpty());
        }
    }

    @Test
    void reportsMissingUser() {
        try (var server = new StubHttpServer()) {
            server.handle("/users/missing/repos", exchange -> StubHttpServer.respond(exchange, 404, "{}"));
            GitHubException exception = assertThrows(
                    GitHubException.class,
                    () -> client(server.baseUrl()).listRepositories("missing"));
            assertEquals(GitHubException.Reason.USER_NOT_FOUND, exception.reason());
        }
    }

    private GitHubClient client(String baseUrl) {
        var settings = new AppConfig.GitHubSettings(baseUrl, "", Duration.ofSeconds(2));
        var http = new HttpRequestExecutor(new AppConfig.HttpSettings(Duration.ofSeconds(1), 2, 1));
        return new GitHubClient(settings, http, new ObjectMapper());
    }
}
