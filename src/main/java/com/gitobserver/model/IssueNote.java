package com.gitobserver.model;

import java.time.Instant;

public record IssueNote(long id, String author, String body, Instant createdAt) {
}
