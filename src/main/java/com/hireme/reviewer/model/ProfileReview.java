package com.hireme.reviewer.model;

import java.time.Instant;
import java.util.List;

public record ProfileReview(String username, Instant generatedAt, List<RepositoryReview> repositories) {
    public ProfileReview {
        repositories = List.copyOf(repositories);
    }
}
