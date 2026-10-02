package com.gitobserver.model;

import java.time.Instant;

public record RepoRef(
        String owner,
        String name,
        String fullName,
        boolean privateRepo,
        String language,
        String htmlUrl,
        Instant pushedAt,
        String description
) {
}
