package com.hireme.reviewer.input;

import java.net.URI;
import java.util.regex.Pattern;

import com.hireme.reviewer.exception.InvalidInputException;

public final class GitHubInputParser {

	private static final String GITHUB_HOST = "github.com";
	private static final String GITHUB_WWW_HOST = "www.github.com";
	private static final String HTTPS_PREFIX = "https://";
	
	private static final Pattern USERNAME_PATTERN =
            Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?");

    private GitHubInputParser() {
    }

    public static String parse(String[] args) {
        validateArguments(args);
        return parseUsername(args[0].trim());
    }

    private static void validateArguments(String[] args) {
        if (args == null || args.length != 1 || args[0] == null || args[0].isBlank()) {
            throw new InvalidInputException(
                    "Exactly one GitHub username or profile URL is required."
            );
        }
    }

    public static String parseUsername(String input) {
        if (USERNAME_PATTERN.matcher(input).matches()) {
            return input;
        }

        URI uri = parseProfileUri(input);
        validateGitHubHost(uri);

        return extractUsername(uri);
    }

    private static URI parseProfileUri(String input) {
        String normalized = addSchemeWhenMissing(input);

        try {
            return URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new InvalidInputException("Invalid GitHub profile URL.", exception);
        }
    }

    private static String addSchemeWhenMissing(
            String input) {

        return input.startsWith(GITHUB_HOST + "/")
                || input.startsWith(GITHUB_WWW_HOST + "/")
                ? HTTPS_PREFIX + input
                : input;
    }

    private static void validateGitHubHost(URI uri) {
        if (!isGitHubHost(uri.getHost())) {
            throw new InvalidInputException(
                    "The profile URL must use github.com."
            );
        }
    }

    private static boolean isGitHubHost(String host) {
        return GITHUB_HOST.equalsIgnoreCase(host)
                || GITHUB_WWW_HOST.equalsIgnoreCase(host);
    }
    
    private static String extractUsername(URI uri) {
        String[] segments = uri.getPath().split("/");

        if (segments.length < 2
                || !USERNAME_PATTERN.matcher(segments[1]).matches()) {
            throw new InvalidInputException(
                    "The GitHub profile URL does not contain a valid username."
            );
        }

        return segments[1];
    }
}
