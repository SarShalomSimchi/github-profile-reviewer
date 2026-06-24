package com.hireme.reviewer.report;

import com.hireme.reviewer.ai.AiReviewClient;
import com.hireme.reviewer.config.AppConfig;
import com.hireme.reviewer.model.ProfileReview;
import com.hireme.reviewer.model.Repository;
import com.hireme.reviewer.model.RepositoryReview;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportWriterTest {
    @TempDir
    Path tempDirectory;

    @Test
    void writesEscapedHtmlWithSuccessAndError() throws Exception {
        Repository repository = new Repository(
                "demo", "octocat/demo", "", "https://github.com/octocat/demo", "Java", false, false, "README");
        var details = new AiReviewClient.ReviewDetails(
                "octocat/demo",
                AiReviewClient.ReviewLevel.INTERMEDIATE,
                "Uses <API> safely",
                "Clear",
                "Shows practical work",
                List.of("Tests & deployment"),
                0.9);
        ProfileReview profile = new ProfileReview(
                "octocat",
                Instant.parse("2026-06-22T10:15:30Z"),
                List.of(
                        RepositoryReview.success(repository, details, 1),
                        RepositoryReview.error(
                                new Repository("bad", "octocat/bad", "", "https://github.com/octocat/bad", "", false, false, null),
                                "AI failed <once>",
                                2)));

        Path report = new ReportWriter(new AppConfig.ReportSettings(tempDirectory)).write(profile);
        String html = Files.readString(report);

        assertTrue(html.contains("Uses &lt;API&gt; safely"));
        assertTrue(html.contains("Tests &amp; deployment"));
        assertTrue(html.contains("AI failed &lt;once&gt;"));
        assertFalse(html.contains("Uses <API> safely"));
    }
}
