package com.gitobserver.model;

import java.time.Instant;

public final class AccessLogEntry {
    private String username;
    private String timestamp;
    private boolean success;
    private String detail;

    public AccessLogEntry() {
    }

    public AccessLogEntry(String username, boolean success, String detail) {
        this.username = username;
        this.timestamp = Instant.now().toString();
        this.success = success;
        this.detail = detail;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Instant instant() {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(timestamp);
        } catch (Exception ignored) {
            return null;
        }
    }
}
