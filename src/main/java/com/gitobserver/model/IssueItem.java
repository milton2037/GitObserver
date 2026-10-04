package com.gitobserver.model;

import java.time.Instant;

public record IssueItem(
        String accountAlias,
        String repository,
        int number,
        String title,
        String body,
        String state,
        String author,
        String htmlUrl,
        Instant updatedAt,
        int commentCount,
        String assignees
) {
    public boolean open() {
        return "open".equalsIgnoreCase(state);
    }

    public String stateLabel() {
        return open() ? "Abierto" : "Cerrado";
    }
}
