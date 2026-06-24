package com.hireme.reviewer.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireme.reviewer.ai.AiClientFactory;
import com.hireme.reviewer.ai.AiReviewClient;
import com.hireme.reviewer.config.AppConfig;
import com.hireme.reviewer.github.GitHubClient;
import com.hireme.reviewer.http.HttpRequestExecutor;
import com.hireme.reviewer.report.ReportWriter;
import com.hireme.reviewer.service.ReviewService;

import java.util.Objects;

public record ApplicationComponents(
        ReviewService reviewService,
        ReportWriter reportWriter) {

    public ApplicationComponents {
        Objects.requireNonNull(
                reviewService,
                "reviewService must not be null");

        Objects.requireNonNull(
                reportWriter,
                "reportWriter must not be null");
    }

    public static ApplicationComponents create(AppConfig config) {
        Objects.requireNonNull(config, "config must not be null");

        ObjectMapper json = new ObjectMapper();
        HttpRequestExecutor http =
                new HttpRequestExecutor(config.http());

        GitHubClient github =
                new GitHubClient(config.github(), http, json);

        AiReviewClient ai =
                AiClientFactory.create(config.ai(), http, json);

        ReviewService reviewService = new ReviewService(
                github,
                ai,
                config.ai(),
                config.processing());

        ReportWriter reportWriter =
                new ReportWriter(config.report());

        return new ApplicationComponents(
                reviewService,
                reportWriter);
    }
}