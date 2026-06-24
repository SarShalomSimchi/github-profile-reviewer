package com.hireme.reviewer.config;

import com.hireme.reviewer.exception.ConfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public record AppConfig(
        HttpSettings http,
        GitHubSettings github,
        AiSettings ai,
        ProcessingSettings processing,
        ReportSettings report) {

    private static final String CONFIG_RESOURCE = "application.properties";
    private static final String GEMINI_API_KEY_ENV = "GEMINI_API_KEY";

    public static AppConfig load() {
        return from(loadProperties(), System.getenv());
    }

    static AppConfig from(
            Properties properties,
            Map<String, String> environment) {

        ConfigSources sources =
                new ConfigSources(properties, environment);

        return new AppConfig(
                loadHttpSettings(sources),
                loadGitHubSettings(sources),
                loadAiSettings(sources),
                loadProcessingSettings(sources),
                loadReportSettings(sources));
    }

    private static HttpSettings loadHttpSettings(
            ConfigSources sources) {

        return new HttpSettings(
                duration(
                        sources,
                        "http.connect-timeout-seconds",
                        "HTTP_CONNECT_TIMEOUT_SECONDS"),
                positiveInt(
                        sources,
                        "http.max-attempts",
                        "HTTP_MAX_ATTEMPTS"),
                positiveLong(
                        sources,
                        "http.initial-retry-delay-millis",
                        "HTTP_INITIAL_RETRY_DELAY_MILLIS"));
    }

    private static GitHubSettings loadGitHubSettings(
            ConfigSources sources) {

        return new GitHubSettings(
                required(
                        sources,
                        "github.api-base-url",
                        "GITHUB_API_BASE_URL"),
                optional(
                        sources,
                        "github.token",
                        "GITHUB_TOKEN"),
                duration(
                        sources,
                        "github.request-timeout-seconds",
                        "GITHUB_REQUEST_TIMEOUT_SECONDS"));
    }

    private static AiSettings loadAiSettings(
            ConfigSources sources) {

        String provider = required(
                sources,
                "ai.provider",
                "AI_PROVIDER")
                .toLowerCase(Locale.ROOT);

        String promptPath = required(
                sources,
                "ai.prompt-path",
                "AI_PROMPT_PATH");

        return new AiSettings(
                provider,
                required(
                        sources,
                        "ai.api-base-url",
                        "AI_API_BASE_URL"),
                apiKey(sources),
                required(
                        sources,
                        "ai.model",
                        "AI_MODEL"),
                loadTextResource(promptPath),
                duration(
                        sources,
                        "ai.request-timeout-seconds",
                        "AI_REQUEST_TIMEOUT_SECONDS"),
                positiveInt(
                        sources,
                        "ai.batch-size",
                        "AI_BATCH_SIZE"),
                positiveInt(
                        sources,
                        "ai.max-batch-characters",
                        "AI_MAX_BATCH_CHARACTERS"),
                positiveInt(
                        sources,
                        "ai.max-output-tokens-per-repository",
                        "AI_MAX_OUTPUT_TOKENS_PER_REPOSITORY"),
                positiveInt(
                        sources,
                        "ai.max-attempts-per-repository",
                        "AI_MAX_ATTEMPTS_PER_REPOSITORY"),
                required(
                        sources,
                        "ai.thinking-level",
                        "AI_THINKING_LEVEL")
                        .toLowerCase(Locale.ROOT),
                positiveInt(
                        sources,
                        "ai.max-concurrent-requests",
                        "AI_MAX_CONCURRENT_REQUESTS"));
    }

    private static ProcessingSettings loadProcessingSettings(
            ConfigSources sources) {

        return new ProcessingSettings(
                positiveInt(
                        sources,
                        "processing.max-concurrency",
                        "PROCESSING_MAX_CONCURRENCY"));
    }

    private static ReportSettings loadReportSettings(
            ConfigSources sources) {

        String outputDirectory = required(
                sources,
                "report.output-directory",
                "REPORT_OUTPUT_DIRECTORY");

        return new ReportSettings(
                Path.of(outputDirectory));
    }

    private static String apiKey(
            ConfigSources sources) {

        return sources.resolve(
                "ai.api-key",
                GEMINI_API_KEY_ENV);
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();

        try (InputStream stream = openResource(CONFIG_RESOURCE);
             InputStreamReader reader = new InputStreamReader(
                     stream,
                     StandardCharsets.UTF_8)) {

            properties.load(reader);
            return properties;
        } catch (IOException exception) {
            throw new ConfigurationException(
                    "Could not load application properties.",
                    exception);
        }
    }

    private static String loadTextResource(
            String resourcePath) {

        String normalizedPath =
                normalizeResourcePath(resourcePath);

        try (InputStream stream =
                     openResource(normalizedPath)) {

            String content = new String(
                    stream.readAllBytes(),
                    StandardCharsets.UTF_8)
                    .strip();

            if (content.isEmpty()) {
                throw new ConfigurationException(
                        "Resource must not be empty: "
                                + normalizedPath);
            }

            return content;
        } catch (IOException exception) {
            throw new ConfigurationException(
                    "Could not load resource: "
                            + normalizedPath,
                    exception);
        }
    }

    private static InputStream openResource(
            String resourcePath) {

        String normalizedPath =
                normalizeResourcePath(resourcePath);

        InputStream stream = AppConfig.class
                .getClassLoader()
                .getResourceAsStream(normalizedPath);

        if (stream == null) {
            throw new ConfigurationException(
                    "Required classpath resource was not found: "
                            + normalizedPath);
        }

        return stream;
    }

    private static String normalizeResourcePath(
            String resourcePath) {

        if (!hasText(resourcePath)) {
            throw new ConfigurationException(
                    "Resource path must not be empty.");
        }

        String normalizedPath =
                resourcePath.trim();

        while (normalizedPath.startsWith("/")) {
            normalizedPath =
                    normalizedPath.substring(1);
        }

        if (normalizedPath.isBlank()) {
            throw new ConfigurationException(
                    "Resource path must not be empty.");
        }

        return normalizedPath;
    }

    private static String required(
            ConfigSources sources,
            String propertyName,
            String environmentName) {

        String value = optional(
                sources,
                propertyName,
                environmentName);

        if (!hasText(value)) {
            throw new ConfigurationException(
                    "Missing configuration value: "
                            + propertyName);
        }

        return value;
    }

    private static String optional(
            ConfigSources sources,
            String propertyName,
            String environmentName) {

        return sources.resolve(
                propertyName,
                environmentName);
    }

    private static int positiveInt(
            ConfigSources sources,
            String propertyName,
            String environmentName) {

        String value = required(
                sources,
                propertyName,
                environmentName);

        try {
            int number = Integer.parseInt(value);

            if (number < 1) {
                throw new NumberFormatException();
            }

            return number;
        } catch (NumberFormatException exception) {
            throw new ConfigurationException(
                    propertyName
                            + " must be a positive integer.");
        }
    }

    private static long positiveLong(
            ConfigSources sources,
            String propertyName,
            String environmentName) {

        String value = required(
                sources,
                propertyName,
                environmentName);

        try {
            long number = Long.parseLong(value);

            if (number < 1) {
                throw new NumberFormatException();
            }

            return number;
        } catch (NumberFormatException exception) {
            throw new ConfigurationException(
                    propertyName
                            + " must be a positive whole number.");
        }
    }

    private static Duration duration(
            ConfigSources sources,
            String propertyName,
            String environmentName) {

        return Duration.ofSeconds(
                positiveInt(
                        sources,
                        propertyName,
                        environmentName));
    }

    private static boolean hasText(
            String value) {

        return value != null
                && !value.isBlank();
    }

    private record ConfigSources(
            Properties properties,
            Map<String, String> environment) {

        private ConfigSources {
            Objects.requireNonNull(
                    properties,
                    "properties must not be null");

            Objects.requireNonNull(
                    environment,
                    "environment must not be null");
        }

        private String resolve(
                String propertyName,
                String environmentName) {

            String environmentValue =
                    environmentValue(environmentName);

            return hasText(environmentValue)
                    ? environmentValue
                    : propertyValue(propertyName);
        }

        private String environmentValue(
                String name) {

            return normalized(environment.get(name));
        }

        private String propertyValue(
                String name) {

            return normalized(
                    properties.getProperty(name));
        }

        private static String normalized(
                String value) {

            return value == null
                    ? ""
                    : value.trim();
        }
    }

    public record HttpSettings(
            Duration connectTimeout,
            int maxAttempts,
            long initialRetryDelayMillis) {
    }

    public record GitHubSettings(
            String apiBaseUrl,
            String token,
            Duration requestTimeout) {
    }

    public record AiSettings(
            String provider,
            String apiBaseUrl,
            String apiKey,
            String model,
            String systemPrompt,
            Duration requestTimeout,
            int batchSize,
            int maxBatchCharacters,
            int maxOutputTokensPerRepository,
            int maxAttemptsPerRepository,
            String thinkingLevel,
            int maxConcurrentRequests) {

        public void requireApiKey() {
            if (!hasText(apiKey)) {
                throw new ConfigurationException(
                        "GEMINI_API_KEY is required for real AI reviews. "
                                + "Set your own key as an environment variable.");
            }
        }
    }

    public record ProcessingSettings(
            int maxConcurrency) {
    }

    public record ReportSettings(
            Path outputDirectory) {
    }
}