package com.gitobserver.database;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.nio.file.Path;

public final class DatabaseConfig {
    private DatabaseType type = DatabaseType.SQLITE;
    private String sqlitePath = "";
    private String host = "localhost";
    private int port = 5432;
    private String databaseName = "gitobserver";
    private String username = "postgres";
    private String password = "";
    private boolean ssl = false;
    private boolean enabled = true;

    public DatabaseConfig() {
    }

    public DatabaseType getType() {
        return type == null ? DatabaseType.SQLITE : type;
    }

    public void setType(DatabaseType type) {
        this.type = type == null ? DatabaseType.SQLITE : type;
    }

    public String getSqlitePath() {
        return sqlitePath;
    }

    public void setSqlitePath(String sqlitePath) {
        this.sqlitePath = sqlitePath;
    }

    public String getHost() {
        return host == null || host.isBlank() ? "localhost" : host.strip();
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        if (port <= 0) {
            return type == DatabaseType.MYSQL ? 3306 : 5432;
        }
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDatabaseName() {
        return databaseName == null || databaseName.isBlank() ? "gitobserver" : databaseName.strip();
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getUsername() {
        return username == null ? "" : username.strip();
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password == null ? "" : password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @JsonIgnore
    public Path resolveSqliteFile() {
        if (sqlitePath != null && !sqlitePath.isBlank()) {
            return Path.of(sqlitePath.strip());
        }
        return Path.of(System.getProperty("user.home"), ".gitobserver", "gitobserver.db");
    }

    @JsonIgnore
    public String toJdbcUrl() {
        return switch (getType()) {
            case SQLITE -> "jdbc:sqlite:" + resolveSqliteFile().toAbsolutePath().toString().replace('\\', '/');
            case POSTGRESQL -> {
                String base = "jdbc:postgresql://" + getHost() + ":" + getPort() + "/" + getDatabaseName();
                yield ssl ? base + "?sslmode=require" : base;
            }
            case MYSQL -> {
                String base = "jdbc:mysql://" + getHost() + ":" + getPort() + "/" + getDatabaseName();
                yield ssl ? base + "?useSSL=true" : base + "?useSSL=false&allowPublicKeyRetrieval=true";
            }
        };
    }

    @JsonIgnore
    public String getSummary() {
        return switch (getType()) {
            case SQLITE -> "SQLite local: " + resolveSqliteFile().getFileName();
            case POSTGRESQL -> "PostgreSQL en " + getHost() + ":" + getPort() + "/" + getDatabaseName();
            case MYSQL -> "MySQL en " + getHost() + ":" + getPort() + "/" + getDatabaseName();
        };
    }
}
