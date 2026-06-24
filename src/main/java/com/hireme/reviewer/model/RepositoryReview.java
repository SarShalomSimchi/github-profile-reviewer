package com.hireme.reviewer.model;

import com.hireme.reviewer.ai.AiReviewClient;

import java.util.List;

public record RepositoryReview(
        Repository repository,
        Status status,
        AiReviewClient.ReviewLevel level,
        String summary,
        String readmeQuality,
        String experienceSignal,
        List<String> evidence,
        double confidence,
        String errorMessage,
        int attempts) {

    public static RepositoryReview success(
            Repository repository,
            AiReviewClient.ReviewDetails details,
            int attempts) {
        return new RepositoryReview(
                repository,
                Status.SUCCESS,
                details.level(),
                details.summary(),
                details.readmeQuality(),
                details.experienceSignal(),
                details.evidence(),
                details.confidence(),
                "",
                attempts);
    }

    public static RepositoryReview noReadme(Repository repository) {
        return errorLike(repository, Status.NO_README, "No README was found.", 0);
    }

    public static RepositoryReview error(Repository repository, String message, int attempts) {
        return errorLike(repository, Status.ERROR, message, attempts);
    }

    private static RepositoryReview errorLike(
            Repository repository,
            Status status,
            String message,
            int attempts) {
        return new RepositoryReview(
                repository,
                status,
                AiReviewClient.ReviewLevel.INSUFFICIENT_EVIDENCE,
                "",
                "",
                "",
                List.of(),
                0,
                message,
                attempts);
    }

    public enum Status {
        SUCCESS,
        NO_README,
        ERROR
    }
}
