package com.hireme.reviewer.report;

import com.hireme.reviewer.config.AppConfig;
import com.hireme.reviewer.exception.ReportException;
import com.hireme.reviewer.model.ProfileReview;
import com.hireme.reviewer.model.RepositoryReview;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ReportWriter {

	private static final String REPORT_TEMPLATE_RESOURCE = "report/report-template.html";

	private static final String USERNAME_PLACEHOLDER = "{{username}}";

	private static final String GENERATED_AT_PLACEHOLDER = "{{generatedAt}}";

	private static final String REPOSITORY_CARDS_PLACEHOLDER = "{{repositoryCards}}";

	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withZone(ZoneId.systemDefault());

	private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
			.withZone(ZoneId.systemDefault());

	private final Path outputDirectory;
	private final String reportTemplate;

	public ReportWriter(AppConfig.ReportSettings settings) {
		this.outputDirectory = settings.outputDirectory();
		this.reportTemplate = loadTemplate(REPORT_TEMPLATE_RESOURCE);
	}

	public Path write(ProfileReview profileReview) {
		print(profileReview);
		return writeHtml(profileReview);
	}

	private void print(ProfileReview profileReview) {
		System.out.printf("%nGitHub profile review: %s%n", profileReview.username());
		System.out.println("=".repeat(72));

		for (RepositoryReview review : profileReview.repositories()) {
			printRepository(review);
		}
	}

	private void printRepository(RepositoryReview review) {
		System.out.printf("%nRepository: %s%n", review.repository().fullName());
		System.out.printf("URL: %s%n", review.repository().htmlUrl());
		System.out.printf("Status: %s%n", review.status());

		if (review.status() == RepositoryReview.Status.SUCCESS) {
			System.out.printf("Level: %s%n", review.level());
			System.out.printf("Summary: %s%n", review.summary());
			System.out.printf("README quality: %s%n", review.readmeQuality());
			System.out.printf("Experience signal: %s%n", review.experienceSignal());
			System.out.printf("Confidence: %.2f%n", review.confidence());
			review.evidence().forEach(item -> System.out.println("Evidence: " + item));
		} else {
			System.out.println("Message: " + review.errorMessage());
		}

		if (review.attempts() > 0) {
			System.out.println("AI attempts: " + review.attempts());
		}
	}

	private Path writeHtml(ProfileReview profileReview) {
		try {
			Files.createDirectories(outputDirectory);
			Path reportPath = outputDirectory.resolve(fileName(profileReview));
			Files.writeString(reportPath, html(profileReview), StandardCharsets.UTF_8);

			return reportPath.toAbsolutePath().normalize();
		} catch (IOException exception) {
			throw new ReportException("Could not write HTML report.", exception);
		}
	}

	private String fileName(ProfileReview profileReview) {

		return "github-profile-review-" + safeFilePart(profileReview.username()) + "-"
				+ FILE_TIME.format(profileReview.generatedAt()) + ".html";
	}

	private String html(ProfileReview profileReview) {
		StringBuilder cards = new StringBuilder();

		for (RepositoryReview review : profileReview.repositories()) {
			cards.append(card(review));
		}

		return reportTemplate.replace(USERNAME_PLACEHOLDER, escape(profileReview.username()))
				.replace(GENERATED_AT_PLACEHOLDER, escape(DISPLAY_TIME.format(profileReview.generatedAt())))
				.replace(REPOSITORY_CARDS_PLACEHOLDER, cards.toString());
	}

	private String card(RepositoryReview review) {

		String details = review.status() == RepositoryReview.Status.SUCCESS ? successDetails(review)
				: "<p><strong>Message:</strong> " + escape(review.errorMessage()) + "</p>";

		return """
				<section class="card">
				  <h2><a href="%s">%s</a></h2>
				  <span class="status %s">%s</span>
				  <p class="meta">Language: %s | Fork: %s | Archived: %s</p>
				  %s
				  %s
				</section>
				""".formatted(escape(review.repository().htmlUrl()), escape(review.repository().fullName()),
				review.status().name().toLowerCase(), review.status(),
				escape(blankAsUnknown(review.repository().primaryLanguage())), review.repository().fork(),
				review.repository().archived(), details, attempts(review));
	}

	private String successDetails(RepositoryReview review) {

		String evidence = review.evidence().stream().map(item -> "<li>" + escape(item) + "</li>").reduce("",
				String::concat);

		return """
				<dl>
				  <dt>Level</dt><dd>%s</dd>
				  <dt>Summary</dt><dd>%s</dd>
				  <dt>README quality</dt><dd>%s</dd>
				  <dt>Experience signal</dt><dd>%s</dd>
				  <dt>Confidence</dt><dd>%.2f</dd>
				  <dt>Evidence</dt><dd><ul>%s</ul></dd>
				</dl>
				""".formatted(review.level(), escape(review.summary()), escape(review.readmeQuality()),
				escape(review.experienceSignal()), review.confidence(), evidence);
	}

	private String attempts(RepositoryReview review) {
		return review.attempts() == 0 ? "" : "<p class=\"meta\">AI attempts: " + review.attempts() + "</p>";
	}

	private static String loadTemplate(String resourcePath) {

		try (InputStream stream = ReportWriter.class.getClassLoader().getResourceAsStream(resourcePath)) {

			if (stream == null) {
				throw new IOException("Report template was not found: " + resourcePath);
			}

			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			throw new ReportException("Could not load report template: " + resourcePath, exception);
		}
	}

	private String escape(String value) {
		return value == null ? ""
				: value.replace("&", "&amp;")
					.replace("<", "&lt;")
					.replace(">", "&gt;")
					.replace("\"", "&quot;")
					.replace("'", "&#39;");
	}

	private String blankAsUnknown(String value) {
		return value == null || value.isBlank() ? "Unknown" : value;
	}

	private String safeFilePart(String value) {
		return value.replaceAll("[^A-Za-z0-9._-]", "-");
	}
}