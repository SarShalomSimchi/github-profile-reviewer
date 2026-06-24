package com.hireme.reviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.hireme.reviewer.exception.InvalidInputException;
import com.hireme.reviewer.input.GitHubInputParser;

class MainTest {
    @Test
    void acceptsUsername() {
        assertEquals("octocat", GitHubInputParser.parseUsername("octocat"));
    }

    @Test
    void extractsUsernameFromProfileUrl() {
        assertEquals("octocat", GitHubInputParser.parseUsername("https://github.com/octocat?tab=repositories"));
    }

    @Test
    void rejectsNonGitHubUrl() {
        assertThrows(InvalidInputException.class, () -> GitHubInputParser.parseUsername("https://example.com/octocat"));
    }

    @Test
    void requiresExactlyOneArgument() {
        assertThrows(InvalidInputException.class, () -> GitHubInputParser.parse(new String[0]));
    }
}
