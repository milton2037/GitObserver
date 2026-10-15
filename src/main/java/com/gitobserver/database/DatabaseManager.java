package com.gitobserver.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {
    private final DatabaseConfig config;
    private boolean initialized = false;

    public record TestResult(boolean success, String message, long pingMs) {}
    public record CacheStats(int repositories, int commits, int pullRequests, int issues) {}

    public DatabaseManager(DatabaseConfig config) {
        this.config = config != null ? config : new DatabaseConfig();
    }

    public DatabaseConfig getConfig() {
        return config;
    }

    public synchronized Connection getConnection() throws SQLException {
        ensureDriver(config.getType());
        String url = config.toJdbcUrl();

        if (config.getType() == DatabaseType.SQLITE) {
            Path file = config.resolveSqliteFile();
            if (file.getParent() != null && Files.notExists(file.getParent())) {
                try {
                    Files.createDirectories(file.getParent());
                } catch (IOException e) {
                    throw new SQLException("No se pudo crear la carpeta para SQLite en " + file.getParent(), e);
                }
            }
            return DriverManager.getConnection(url);
        } else {
            return DriverManager.getConnection(url, config.getUsername(), config.getPassword());
        }
    }

    public synchronized void initSchema() throws SQLException {
        if (initialized) {
            return;
        }

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // 1. Repositorios
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS repositories (
                    id VARCHAR(100) PRIMARY KEY,
                    owner VARCHAR(100) NOT NULL,
                    name VARCHAR(100) NOT NULL,
                    full_name VARCHAR(200) NOT NULL,
                    is_private INTEGER DEFAULT 0,
                    language VARCHAR(50),
                    html_url VARCHAR(300),
                    pushed_at VARCHAR(40),
                    description TEXT,
                    synced_at VARCHAR(40)
                )
            """);

            // 2. Commits
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS commits (
                    sha VARCHAR(64) PRIMARY KEY,
                    repository VARCHAR(200) NOT NULL,
                    account_alias VARCHAR(100),
                    author VARCHAR(150),
                    message TEXT,
                    committed_at VARCHAR(40),
                    html_url VARCHAR(300),
                    additions INTEGER DEFAULT 0,
                    deletions INTEGER DEFAULT 0
                )
            """);

            // 3. Pull Requests
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pull_requests (
                    id VARCHAR(100) PRIMARY KEY,
                    repository VARCHAR(200) NOT NULL,
                    account_alias VARCHAR(100),
                    number INTEGER NOT NULL,
                    title TEXT,
                    body TEXT,
                    state VARCHAR(30),
                    author VARCHAR(150),
                    html_url VARCHAR(300),
                    updated_at VARCHAR(40),
                    is_draft INTEGER DEFAULT 0
                )
            """);

            // 4. Issues
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS issues (
                    id VARCHAR(100) PRIMARY KEY,
                    repository VARCHAR(200) NOT NULL,
                    account_alias VARCHAR(100),
                    number INTEGER NOT NULL,
                    title TEXT,
                    body TEXT,
                    state VARCHAR(30),
                    author VARCHAR(150),
                    html_url VARCHAR(300),
                    updated_at VARCHAR(40),
                    comments_count INTEGER DEFAULT 0,
                    assignees TEXT
                )
            """);

            // 5. Historial de sincronización
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sync_history (
                    id VARCHAR(64) PRIMARY KEY,
                    repository VARCHAR(200) NOT NULL,
                    sync_type VARCHAR(50),
                    records_synced INTEGER DEFAULT 0,
                    status VARCHAR(30),
                    synced_at VARCHAR(40),
                    message TEXT
                )
            """);

            createIndexSilently(stmt, "idx_commits_repo_date", "CREATE INDEX idx_commits_repo_date ON commits(repository, committed_at)");
            createIndexSilently(stmt, "idx_commits_author", "CREATE INDEX idx_commits_author ON commits(author)");
            createIndexSilently(stmt, "idx_prs_repo_state", "CREATE INDEX idx_prs_repo_state ON pull_requests(repository, state)");
            createIndexSilently(stmt, "idx_issues_repo_state", "CREATE INDEX idx_issues_repo_state ON issues(repository, state)");

            initialized = true;
        }
    }

    private void createIndexSilently(Statement stmt, String indexName, String sql) {
        try {
            stmt.execute(sql);
        } catch (SQLException ignored) {
            // El índice ya existe o la base de datos ya lo maneja
        }
    }

    public static TestResult testConnection(DatabaseConfig testCfg) {
        long start = System.currentTimeMillis();
        try {
            ensureDriver(testCfg.getType());
            String url = testCfg.toJdbcUrl();
            Connection conn;
            if (testCfg.getType() == DatabaseType.SQLITE) {
                Path file = testCfg.resolveSqliteFile();
                if (file.getParent() != null && Files.notExists(file.getParent())) {
                    Files.createDirectories(file.getParent());
                }
                conn = DriverManager.getConnection(url);
            } else {
                DriverManager.setLoginTimeout(5);
                conn = DriverManager.getConnection(url, testCfg.getUsername(), testCfg.getPassword());
            }

            try (conn; Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1")) {
                if (rs.next()) {
                    long elapsed = System.currentTimeMillis() - start;
                    return new TestResult(true, "Conexión exitosa (" + elapsed + " ms) a " + testCfg.getType().getDisplayName(), elapsed);
                }
            }
            return new TestResult(false, "No se recibió respuesta al consultar la base de datos.", -1);
        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - start;
            String msg = ex.getMessage();
            if (msg == null || msg.isBlank()) {
                msg = ex.getClass().getSimpleName();
            }
            return new TestResult(false, "Error de conexión: " + msg, elapsed);
        }
    }

    public CacheStats getStats() {
        try (Connection conn = getConnection()) {
            int repos = countTable(conn, "repositories");
            int commits = countTable(conn, "commits");
            int prs = countTable(conn, "pull_requests");
            int issues = countTable(conn, "issues");
            return new CacheStats(repos, commits, prs, issues);
        } catch (Exception ignored) {
            return new CacheStats(0, 0, 0, 0);
        }
    }

    private int countTable(Connection conn, String table) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException ignored) {}
        return 0;
    }

    public void clearCache() throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM commits");
            stmt.execute("DELETE FROM pull_requests");
            stmt.execute("DELETE FROM issues");
            stmt.execute("DELETE FROM repositories");
            stmt.execute("DELETE FROM sync_history");
        }
    }

    private static void ensureDriver(DatabaseType type) throws SQLException {
        try {
            switch (type) {
                case SQLITE -> Class.forName("org.sqlite.JDBC");
                case POSTGRESQL -> Class.forName("org.postgresql.Driver");
                case MYSQL -> Class.forName("com.mysql.cj.jdbc.Driver");
            }
        } catch (ClassNotFoundException e) {
            throw new SQLException("No se encontró el controlador JDBC para " + type.getDisplayName(), e);
        }
    }
}
