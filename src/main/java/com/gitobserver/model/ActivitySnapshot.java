package com.gitobserver.model;

import java.time.Instant;
import java.util.List;

public record ActivitySnapshot(
        Instant generatedAt,
        String from,
        String to,
        String repository,
        String author,
        String accounts,
        boolean hideBots,
        boolean cancelled,
        int warnings,
        List<AuthorCount> authors,
        List<CommitRecord> commits
) {
    public ActivitySnapshot {
        authors = authors == null ? List.of() : List.copyOf(authors);
        commits = commits == null ? List.of() : List.copyOf(commits);
        from = from == null ? "" : from;
        to = to == null ? "" : to;
        repository = repository == null ? "" : repository;
        author = author == null || author.isBlank() ? "Todos" : author;
        accounts = accounts == null ? "" : accounts;
    }
}
