package com.gitobserver.model;

import java.time.Instant;
import java.util.List;

public record CommitRecord(
        String accountAlias,
        String repository,
        String sha,
        String htmlUrl,
        String author,
        String message,
        Instant committedAt,
        int additions,
        int deletions,
        List<FileChange> files
) {
    public CommitRecord {
        files = files == null ? List.of() : List.copyOf(files);
    }

    public CommitRecord(
            String accountAlias,
            String repository,
            String sha,
            String htmlUrl,
            String author,
            String message,
            Instant committedAt
    ) {
        this(accountAlias, repository, sha, htmlUrl, author, message, committedAt, -1, -1, List.of());
    }

    public boolean hasLineStats() {
        return additions >= 0 && deletions >= 0;
    }
}
