package com.gitobserver.model;

public record RepoContribution(String repository, int commits, int additions, int deletions) {
    public boolean hasLineStats() {
        return additions >= 0 && deletions >= 0;
    }
}
