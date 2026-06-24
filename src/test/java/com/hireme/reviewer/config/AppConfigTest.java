package com.hireme.reviewer.config;

import com.hireme.reviewer.exception.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppConfigTest {
    @Test
    void environmentOverridesProperties() {
        Properties properties = defaults();
        Map<String, String> environment = new HashMap<>();
        environment.put("GEMINI_API_KEY", "secret");
        environment.put("AI_MODEL", "gemini-3.5-flash");
        environment.put("AI_BATCH_SIZE", "3");

        AppConfig config = AppConfig.from(properties, environment);

        assertEquals("secret", config.ai().apiKey());
        assertEquals("gemini-3.5-flash", config.ai().model());
        assertEquals(3, config.ai().batchSize());
    }

    @Test
    void rejectsNonPositiveValues() {
        Properties properties = defaults();
        properties.setProperty("ai.batch-size", "0");

        assertThrows(ConfigurationException.class, () -> AppConfig.from(properties, Map.of()));
    }

    @Test
    void requiresApiKeyOnlyForRealRun() {
        AppConfig config = AppConfig.from(defaults(), Map.of());
        assertThrows(ConfigurationException.class, config.ai()::requireApiKey);
    }

    private Properties defaults() {
        Properties properties = new Properties();
        properties.setProperty("http.connect-timeout-seconds", "1");
        properties.setProperty("http.max-attempts", "2");
        properties.setProperty("http.initial-retry-delay-millis", "1");
        properties.setProperty("github.api-base-url", "http://localhost");
        properties.setProperty("github.request-timeout-seconds", "1");
        properties.setProperty("github.token", "");
        properties.setProperty("ai.provider", "gemini");
        properties.setProperty("ai.api-base-url", "http://localhost");
        properties.setProperty("ai.api-key", "");
        properties.setProperty("ai.model", "gemini-3.1-flash-lite");
        properties.setProperty("ai.request-timeout-seconds", "1");
        properties.setProperty("ai.batch-size", "5");
        properties.setProperty("ai.max-batch-characters", "120000");
        properties.setProperty("ai.max-output-tokens-per-repository", "220");
        properties.setProperty("ai.max-attempts-per-repository", "2");
        properties.setProperty("ai.thinking-level", "minimal");
        properties.setProperty("ai.max-concurrent-requests", "1");
        properties.setProperty("processing.max-concurrency", "8");
        properties.setProperty("report.output-directory", "reports");
        properties.setProperty("ai.prompt-path", "prompts/repository-review.txt");
        return properties;
    }
}
