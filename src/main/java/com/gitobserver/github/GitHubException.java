package com.gitobserver.github;

public final class GitHubException extends RuntimeException {
    private final int status;

    public GitHubException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
