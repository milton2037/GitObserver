package com.gitobserver.model;

import java.time.Instant;

public record PullItem(
        String accountAlias,
        String repository,
        int number,
        String title,
        String body,
        String state,
        String author,
        String htmlUrl,
        Instant updatedAt,
        boolean draft
) {
    public String stateLabel() {
        if (draft) {
            return "Borrador";
        }
        return "open".equalsIgnoreCase(state) ? "Abierta" : "Cerrada";
    }
}
