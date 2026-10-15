package com.gitobserver.database.dao;

import com.gitobserver.database.DatabaseManager;
import com.gitobserver.model.CommitRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class CommitDao {
    private final DatabaseManager manager;

    public CommitDao(DatabaseManager manager) {
        this.manager = manager;
    }

    public void upsertAll(String repoFullName, List<CommitRecord> commits) throws SQLException {
        if (commits == null || commits.isEmpty()) {
            return;
        }

        String checkSql = "SELECT 1 FROM commits WHERE sha = ?";
        String updateSql = """
            UPDATE commits
            SET repository = ?, account_alias = ?, author = ?, message = ?, committed_at = ?, html_url = ?, additions = ?, deletions = ?
            WHERE sha = ?
        """;
        String insertSql = """
            INSERT INTO commits (sha, repository, account_alias, author, message, committed_at, html_url, additions, deletions)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = manager.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                 PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

                for (CommitRecord c : commits) {
                    if (c == null || c.sha() == null || c.sha().isBlank()) {
                        continue;
                    }

                    checkStmt.setString(1, c.sha());
                    boolean exists;
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        exists = rs.next();
                    }

                    String repo = (repoFullName != null && !repoFullName.isBlank()) ? repoFullName : c.repository();
                    String committedAtStr = c.committedAt() != null ? c.committedAt().toString() : null;

                    if (exists) {
                        updateStmt.setString(1, repo != null ? repo : "");
                        updateStmt.setString(2, c.accountAlias() != null ? c.accountAlias() : "");
                        updateStmt.setString(3, c.author() != null ? c.author() : "");
                        updateStmt.setString(4, c.message() != null ? c.message() : "");
                        updateStmt.setString(5, committedAtStr);
                        updateStmt.setString(6, c.htmlUrl());
                        updateStmt.setInt(7, c.additions());
                        updateStmt.setInt(8, c.deletions());
                        updateStmt.setString(9, c.sha());
                        updateStmt.addBatch();
                    } else {
                        insertStmt.setString(1, c.sha());
                        insertStmt.setString(2, repo != null ? repo : "");
                        insertStmt.setString(3, c.accountAlias() != null ? c.accountAlias() : "");
                        insertStmt.setString(4, c.author() != null ? c.author() : "");
                        insertStmt.setString(5, c.message() != null ? c.message() : "");
                        insertStmt.setString(6, committedAtStr);
                        insertStmt.setString(7, c.htmlUrl());
                        insertStmt.setInt(8, c.additions());
                        insertStmt.setInt(9, c.deletions());
                        insertStmt.addBatch();
                    }
                }

                updateStmt.executeBatch();
                insertStmt.executeBatch();
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        }
    }

    public List<CommitRecord> listByRepository(String repoFullName) throws SQLException {
        String sql = """
            SELECT sha, repository, account_alias, author, message, committed_at, html_url, additions, deletions
            FROM commits
            WHERE LOWER(repository) = LOWER(?)
            ORDER BY committed_at DESC
        """;
        try (Connection conn = manager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, repoFullName.strip());
            try (ResultSet rs = stmt.executeQuery()) {
                return mapList(rs);
            }
        }
    }

    public List<CommitRecord> listByRepositoryAndRange(String repoFullName, Instant since, Instant until) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT sha, repository, account_alias, author, message, committed_at, html_url, additions, deletions
            FROM commits
            WHERE LOWER(repository) = LOWER(?)
        """);

        if (since != null) {
            sql.append(" AND committed_at >= ?");
        }
        if (until != null) {
            sql.append(" AND committed_at <= ?");
        }
        sql.append(" ORDER BY committed_at DESC");

        try (Connection conn = manager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            stmt.setString(paramIndex++, repoFullName.strip());
            if (since != null) {
                stmt.setString(paramIndex++, since.toString());
            }
            if (until != null) {
                stmt.setString(paramIndex++, until.toString());
            }

            try (ResultSet rs = stmt.executeQuery()) {
                return mapList(rs);
            }
        }
    }

    public int countByRepository(String repoFullName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM commits WHERE LOWER(repository) = LOWER(?)";
        try (Connection conn = manager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, repoFullName.strip());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    private List<CommitRecord> mapList(ResultSet rs) throws SQLException {
        List<CommitRecord> list = new ArrayList<>();
        while (rs.next()) {
            String committedStr = rs.getString("committed_at");
            Instant committedAt = null;
            if (committedStr != null && !committedStr.isBlank()) {
                try {
                    committedAt = Instant.parse(committedStr);
                } catch (Exception ignored) {}
            }

            list.add(new CommitRecord(
                    rs.getString("account_alias"),
                    rs.getString("repository"),
                    rs.getString("sha"),
                    rs.getString("html_url"),
                    rs.getString("author"),
                    rs.getString("message"),
                    committedAt,
                    rs.getInt("additions"),
                    rs.getInt("deletions"),
                    List.of()
            ));
        }
        return list;
    }
}
