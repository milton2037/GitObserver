package com.gitobserver.model;

import java.util.List;

public record PersonContribution(
        String name,
        int totalCommits,
        int additions,
        int deletions,
        List<RepoContribution> repositories
) {
    public PersonContribution {
        repositories = repositories == null ? List.of() : List.copyOf(repositories);
    }

    public boolean hasLineStats() {
        return additions >= 0 && deletions >= 0;
    }
}
