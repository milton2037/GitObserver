package com.gitobserver.database;

public enum DatabaseType {
    SQLITE("SQLite (Local embebida)", "jdbc:sqlite:"),
    POSTGRESQL("PostgreSQL (Servidor remoto/local)", "jdbc:postgresql://"),
    MYSQL("MySQL (Servidor remoto/local)", "jdbc:mysql://");

    private final String displayName;
    private final String urlPrefix;

    DatabaseType(String displayName, String urlPrefix) {
        this.displayName = displayName;
        this.urlPrefix = urlPrefix;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUrlPrefix() {
        return urlPrefix;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
