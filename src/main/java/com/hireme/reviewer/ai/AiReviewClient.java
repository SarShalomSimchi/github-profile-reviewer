package com.hireme.reviewer.ai;

import com.hireme.reviewer.model.Repository;

import java.util.List;
import java.util.Map;

public interface AiReviewClient {
    BatchResult review(List<Repository> repositories);

    enum ReviewLevel {
        BASIC,
        INTERMEDIATE,
        ADVANCED,
        INSUFFICIENT_EVIDENCE
    }

    record ReviewDetails(
            String repositoryId,
            ReviewLevel level,
            String summary,
            String readmeQuality,
            String experienceSignal,
            List<String> evidence,
            double confidence) {
        public ReviewDetails {
            evidence = List.copyOf(evidence);
        }
    }

    record ItemError(String repositoryId, String message, boolean retryable) {
    }

    record BatchResult(Map<String, ReviewDetails> reviews, Map<String, ItemError> errors) {
        public BatchResult {
            reviews = Map.copyOf(reviews);
            errors = Map.copyOf(errors);
        }
    }
}
