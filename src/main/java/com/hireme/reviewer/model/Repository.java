package com.hireme.reviewer.model;

public record Repository(
        String name,
        String fullName,
        String description,
        String htmlUrl,
        String primaryLanguage,
        boolean fork,
        boolean archived,
        String readme) {

    public Repository withReadme(String content) {
        return new Repository(name, fullName, description, htmlUrl, primaryLanguage, fork, archived, content);
    }

    public boolean hasReadme() {
        return readme != null && !readme.isBlank();
    }
}
