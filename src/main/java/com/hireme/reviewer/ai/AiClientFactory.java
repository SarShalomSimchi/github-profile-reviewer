package com.hireme.reviewer.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireme.reviewer.config.AppConfig;
import com.hireme.reviewer.exception.ConfigurationException;
import com.hireme.reviewer.http.HttpRequestExecutor;

public final class AiClientFactory {
    private static final String GEMINI = "gemini";

    private AiClientFactory() {
    }

    public static AiReviewClient create(
            AppConfig.AiSettings settings,
            HttpRequestExecutor http,
            ObjectMapper json) {
        return switch (settings.provider()) {
            case GEMINI -> new GeminiReviewClient(http, settings, json);
            default -> throw new ConfigurationException("Unsupported AI provider: " + settings.provider());
        };
    }
}
