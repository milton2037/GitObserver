package com.gitobserver.database.dao;

import com.gitobserver.database.DatabaseManager;
import com.gitobserver.model.PullItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class PullRequestDao {
    private final DatabaseManager manager;

    public PullRequestDao(DatabaseManager manager) {
        this.manager = manager;
    }

    public void upsertAll(String repoFullName, List<PullItem> pulls) throws SQLException {
        if (pulls == null || pulls.isEmpty()) {
            return;
        }

        String checkSql = "SELECT 1 FROM pull_requests WHERE id = ?";
        String updateSql = """
            UPDATE pull_requests
            SET repository = ?, account_alias = ?, number = ?, title = ?, body = ?, state = ?, author = ?, html_url = ?, updated_at = ?, is_draft = ?
            WHERE id = ?
        """;
        String insertSql = """
            INSERT INTO pull_requests (id, repository, account_alias, number, title, body, state, author, html_url, updated_at, is_draft)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = manager.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                 PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

                for (PullItem p : pulls) {
                    if (p == null) {
                        continue;
                    }
                    String repo = (repoFullName != null && !repoFullName.isBlank()) ? repoFullName : p.repository();
                    String id = (repo + "#pr#" + p.number()).toLowerCase();

                    checkStmt.setString(1, id);
                    boolean exists;
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        exists = rs.next();
                    }

                    String updatedStr = p.updatedAt() != null ? p.updatedAt().toString() : null;

                    if (exists) {
                        updateStmt.setString(1, repo != null ? repo : "");
                        updateStmt.setString(2, p.accountAlias() != null ? p.accountAlias() : "");
                        updateStmt.setInt(3, p.number());
                        updateStmt.setString(4, p.title());
                        updateStmt.setString(5, p.body());
                        updateStmt.setString(6, p.state());
                        updateStmt.setString(7, p.author());
                        updateStmt.setString(8, p.htmlUrl());
                        updateStmt.setString(9, updatedStr);
                        updateStmt.setInt(10, p.draft() ? 1 : 0);
                        updateStmt.setString(11, id);
                        updateStmt.addBatch();
                    } else {
                        insertStmt.setString(1, id);
                        insertStmt.setString(2, repo != null ? repo : "");
                        insertStmt.setString(3, p.accountAlias() != null ? p.accountAlias() : "");
                        insertStmt.setInt(4, p.number());
                        insertStmt.setString(5, p.title());
                        insertStmt.setString(6, p.body());
                        insertStmt.setString(7, p.state());
                        insertStmt.setString(8, p.author());
                        insertStmt.setString(9, p.htmlUrl());
                        insertStmt.setString(10, updatedStr);
                        insertStmt.setInt(11, p.draft() ? 1 : 0);
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

    public List<PullItem> listByRepository(String repoFullName, String stateFilter) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT account_alias, repository, number, title, body, state, author, html_url, updated_at, is_draft
            FROM pull_requests
            WHERE LOWER(repository) = LOWER(?)
        """);

        if (stateFilter != null && !stateFilter.equalsIgnoreCase("todos") && !stateFilter.isBlank()) {
            sql.append(" AND LOWER(state) = LOWER(?)");
        }
        sql.append(" ORDER BY number DESC");

        try (Connection conn = manager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            stmt.setString(1, repoFullName.strip());
            if (stateFilter != null && !stateFilter.equalsIgnoreCase("todos") && !stateFilter.isBlank()) {
                stmt.setString(2, stateFilter.strip());
            }

            try (ResultSet rs = stmt.executeQuery()) {
                return mapList(rs);
            }
        }
    }

    private List<PullItem> mapList(ResultSet rs) throws SQLException {
        List<PullItem> list = new ArrayList<>();
        while (rs.next()) {
            String updatedStr = rs.getString("updated_at");
            Instant updatedAt = null;
            if (updatedStr != null && !updatedStr.isBlank()) {
                try {
                    updatedAt = Instant.parse(updatedStr);
                } catch (Exception ignored) {}
            }

            list.add(new PullItem(
                    rs.getString("account_alias"),
                    rs.getString("repository"),
                    rs.getInt("number"),
                    rs.getString("title"),
                    rs.getString("body"),
                    rs.getString("state"),
                    rs.getString("author"),
                    rs.getString("html_url"),
                    updatedAt,
                    rs.getInt("is_draft") == 1
            ));
        }
        return list;
    }
}
