package com.hireme.reviewer.service;

import com.hireme.reviewer.ai.AiReviewClient;
import com.hireme.reviewer.config.AppConfig;
import com.hireme.reviewer.exception.AiReviewException;
import com.hireme.reviewer.exception.GitHubException;
import com.hireme.reviewer.exception.ReviewException;
import com.hireme.reviewer.github.GitHubClient;
import com.hireme.reviewer.model.ProfileReview;
import com.hireme.reviewer.model.Repository;
import com.hireme.reviewer.model.RepositoryReview;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ReviewService {
    private final GitHubClient github;
    private final AiReviewClient ai;
    private final AppConfig.AiSettings aiSettings;
    private final AppConfig.ProcessingSettings processingSettings;

    public ReviewService(
            GitHubClient github,
            AiReviewClient ai,
            AppConfig.AiSettings aiSettings,
            AppConfig.ProcessingSettings processingSettings) {
        this.github = github;
        this.ai = ai;
        this.aiSettings = aiSettings;
        this.processingSettings = processingSettings;
    }

    public ProfileReview review(String username) {
        List<Repository> repositories = github.listRepositories(username);
        List<ReadmeLoad> readmes = loadReadmes(repositories);
        Map<String, RepositoryReview> results = initialResults(readmes);
        List<Repository> reviewable = readmes.stream()
                .filter(ReadmeLoad::reviewable)
                .map(ReadmeLoad::repository)
                .toList();
        results.putAll(reviewWithRetries(reviewable));

        List<RepositoryReview> ordered = repositories.stream()
                .map(repository -> results.get(repository.fullName()))
                .toList();
        return new ProfileReview(username, Instant.now(), ordered);
    }

    private List<ReadmeLoad> loadReadmes(List<Repository> repositories) {
        return runInParallel(
                repositories,
                processingSettings.maxConcurrency(),
                this::loadReadme);
    }
    
    private <T, R> List<R> runInParallel(
            List<T> items,
            int maxConcurrency,
            Function<T, R> operation) {
    	
        if (items.isEmpty()) {
            return List.of();
        }

        Semaphore limit = new Semaphore(maxConcurrency);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<R>> futures = items.stream()
                    .map(item -> executor.submit(() -> runLimited(limit, item, operation)))
                    .toList();
            return futures.stream().map(this::resultOf).toList();
        }
    }
    
    private <T, R> R runLimited(Semaphore limit, T item, Function<T, R> operation) {
        boolean acquired = false;
        try {
            limit.acquire();
            acquired = true;
            return operation.apply(item);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ReviewException("Parallel review processing was interrupted.", exception);
        } finally {
            if (acquired) {
                limit.release();
            }
        }
    }
    
    private <R> R resultOf(Future<R> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ReviewException("Parallel review processing was interrupted.", exception);
        } catch (ExecutionException exception) {
            throw new ReviewException("Parallel review processing failed.", exception.getCause());
        }
    }

    private ReadmeLoad loadReadme(Repository repository) {
        try {
            return github.loadReadme(repository)
                    .map(content -> ReadmeLoad.found(repository.withReadme(content)))
                    .orElseGet(() -> ReadmeLoad.missing(repository));
        } catch (GitHubException exception) {
            return ReadmeLoad.failed(repository, exception.getMessage());
        }
    }

    private Map<String, RepositoryReview> initialResults(List<ReadmeLoad> readmes) {
        Map<String, RepositoryReview> results = new LinkedHashMap<>();
        for (ReadmeLoad load : readmes) {
            if (load.status() == ReadmeStatus.MISSING) {
                results.put(load.repository().fullName(), RepositoryReview.noReadme(load.repository()));
            } else if (load.status() == ReadmeStatus.ERROR) {
                results.put(load.repository().fullName(), RepositoryReview.error(load.repository(), load.error(), 0));
            }
        }
        return results;
    }

    private Map<String, RepositoryReview> reviewWithRetries(List<Repository> repositories) {
        Map<String, RepositoryReview> completed = new LinkedHashMap<>();
        Map<String, Repository> pending = indexById(repositories);
        Map<String, Integer> attempts = new LinkedHashMap<>();

        while (!pending.isEmpty()) {
            List<List<Repository>> batches = createBatches(new ArrayList<>(pending.values()));
            List<BatchAttempt> batchAttempts = runInParallel(
                    batches,
                    aiSettings.maxConcurrentRequests(),
                    this::reviewBatch);
            applyAttempts(batchAttempts, pending, completed, attempts);
        }
        return completed;
    }

    private BatchAttempt reviewBatch(List<Repository> batch) {
        try {
            return BatchAttempt.success(batch, ai.review(batch));
        } catch (AiReviewException exception) {
            return BatchAttempt.failed(batch, exception);
        }
    }

    private void applyAttempts(
            List<BatchAttempt> batchAttempts,
            Map<String, Repository> pending,
            Map<String, RepositoryReview> completed,
            Map<String, Integer> attempts) {
        for (BatchAttempt attempt : batchAttempts) {
            incrementAttempts(attempt.repositories(), attempts);
            if (attempt.error() != null) {
                handleBatchError(attempt, pending, completed, attempts);
            } else {
                handleBatchResult(attempt, pending, completed, attempts);
            }
        }
    }

    private void handleBatchError(
            BatchAttempt attempt,
            Map<String, Repository> pending,
            Map<String, RepositoryReview> completed,
            Map<String, Integer> attempts) {
        for (Repository repository : attempt.repositories()) {
            int used = attempts.get(repository.fullName());
            if (!attempt.error().retryable() || used >= aiSettings.maxAttemptsPerRepository()) {
                completeError(repository, attempt.error().getMessage(), used, pending, completed);
            }
        }
    }

    private void handleBatchResult(
            BatchAttempt attempt,
            Map<String, Repository> pending,
            Map<String, RepositoryReview> completed,
            Map<String, Integer> attempts) {
        for (Repository repository : attempt.repositories()) {
            String id = repository.fullName();
            AiReviewClient.ReviewDetails review = attempt.result().reviews().get(id);
            if (review != null) {
                completed.put(id, RepositoryReview.success(repository, review, attempts.get(id)));
                pending.remove(id);
                continue;
            }

            AiReviewClient.ItemError error = attempt.result().errors().get(id);
            String message = error == null ? "AI provider did not return a review." : error.message();
            boolean retryable = error == null || error.retryable();
            int used = attempts.get(id);
            if (!retryable || used >= aiSettings.maxAttemptsPerRepository()) {
                completeError(repository, message, used, pending, completed);
            }
        }
    }

    private void completeError(
            Repository repository,
            String message,
            int attempts,
            Map<String, Repository> pending,
            Map<String, RepositoryReview> completed) {
        completed.put(repository.fullName(), RepositoryReview.error(
                repository,
                message + " Review failed after " + attempts + " attempt(s).",
                attempts));
        pending.remove(repository.fullName());
    }

    private void incrementAttempts(List<Repository> repositories, Map<String, Integer> attempts) {
        repositories.forEach(repository -> attempts.merge(repository.fullName(), 1, Integer::sum));
    }

    private List<List<Repository>> createBatches(List<Repository> repositories) {
        List<List<Repository>> batches = new ArrayList<>();
        List<Repository> current = new ArrayList<>();
        int currentCharacters = 0;

        for (Repository repository : repositories) {
            int repositoryCharacters = repository.readme().length();
            if (!current.isEmpty() && shouldStartNewBatch(current, currentCharacters, repositoryCharacters)) {
                batches.add(List.copyOf(current));
                current.clear();
                currentCharacters = 0;
            }
            current.add(repository);
            currentCharacters += repositoryCharacters;
        }
        if (!current.isEmpty()) {
            batches.add(List.copyOf(current));
        }
        return batches;
    }

    private boolean shouldStartNewBatch(
            List<Repository> current,
            int currentCharacters,
            int nextRepositoryCharacters) {
        return current.size() >= aiSettings.batchSize()
                || currentCharacters + nextRepositoryCharacters > aiSettings.maxBatchCharacters();
    }

    private Map<String, Repository> indexById(List<Repository> repositories) {
    	return repositories.stream()
    	        .collect(Collectors.toMap(
    	                Repository::fullName,
    	                Function.identity(),
    	                (existing, replacement) -> replacement,
    	                LinkedHashMap::new));
    }

    private enum ReadmeStatus {
        FOUND,
        MISSING,
        ERROR
    }

    private record ReadmeLoad(Repository repository, ReadmeStatus status, String error) {
        static ReadmeLoad found(Repository repository) {
            return new ReadmeLoad(repository, ReadmeStatus.FOUND, "");
        }

        static ReadmeLoad missing(Repository repository) {
            return new ReadmeLoad(repository, ReadmeStatus.MISSING, "");
        }

        static ReadmeLoad failed(Repository repository, String error) {
            return new ReadmeLoad(repository, ReadmeStatus.ERROR, error);
        }

        boolean reviewable() {
            return status == ReadmeStatus.FOUND;
        }
    }

    private record BatchAttempt(
            List<Repository> repositories,
            AiReviewClient.BatchResult result,
            AiReviewException error) {
    	
        static BatchAttempt success(List<Repository> repositories, AiReviewClient.BatchResult result) {
            return new BatchAttempt(repositories, result, null);
        }

        static BatchAttempt failed(List<Repository> repositories, AiReviewException error) {
            return new BatchAttempt(repositories, null, error);
        }
    }
}
