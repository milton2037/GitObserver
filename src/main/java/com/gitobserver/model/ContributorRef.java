package com.gitobserver.model;

public record ContributorRef(String displayName, String repository, int commits, int additions, int deletions) {
    public ContributorRef(String displayName, String repository, int commits) {
        this(displayName, repository, commits, -1, -1);
    }

    public boolean hasLineStats() {
        return additions >= 0 && deletions >= 0;
    }
}
