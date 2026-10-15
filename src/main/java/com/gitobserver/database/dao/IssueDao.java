package com.gitobserver.database.dao;

import com.gitobserver.database.DatabaseManager;
import com.gitobserver.model.IssueItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class IssueDao {
    private final DatabaseManager manager;

    public IssueDao(DatabaseManager manager) {
        this.manager = manager;
    }

    public void upsertAll(String repoFullName, List<IssueItem> issues) throws SQLException {
        if (issues == null || issues.isEmpty()) {
            return;
        }

        String checkSql = "SELECT 1 FROM issues WHERE id = ?";
        String updateSql = """
            UPDATE issues
            SET repository = ?, account_alias = ?, number = ?, title = ?, body = ?, state = ?, author = ?, html_url = ?, updated_at = ?, comments_count = ?, assignees = ?
            WHERE id = ?
        """;
        String insertSql = """
            INSERT INTO issues (id, repository, account_alias, number, title, body, state, author, html_url, updated_at, comments_count, assignees)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = manager.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                 PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

                for (IssueItem issue : issues) {
                    if (issue == null) {
                        continue;
                    }
                    String repo = (repoFullName != null && !repoFullName.isBlank()) ? repoFullName : issue.repository();
                    String id = (repo + "#issue#" + issue.number()).toLowerCase();

                    checkStmt.setString(1, id);
                    boolean exists;
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        exists = rs.next();
                    }

                    String updatedStr = issue.updatedAt() != null ? issue.updatedAt().toString() : null;

                    if (exists) {
                        updateStmt.setString(1, repo != null ? repo : "");
                        updateStmt.setString(2, issue.accountAlias() != null ? issue.accountAlias() : "");
                        updateStmt.setInt(3, issue.number());
                        updateStmt.setString(4, issue.title());
                        updateStmt.setString(5, issue.body());
                        updateStmt.setString(6, issue.state());
                        updateStmt.setString(7, issue.author());
                        updateStmt.setString(8, issue.htmlUrl());
                        updateStmt.setString(9, updatedStr);
                        updateStmt.setInt(10, issue.commentCount());
                        updateStmt.setString(11, issue.assignees());
                        updateStmt.setString(12, id);
                        updateStmt.addBatch();
                    } else {
                        insertStmt.setString(1, id);
                        insertStmt.setString(2, repo != null ? repo : "");
                        insertStmt.setString(3, issue.accountAlias() != null ? issue.accountAlias() : "");
                        insertStmt.setInt(4, issue.number());
                        insertStmt.setString(5, issue.title());
                        insertStmt.setString(6, issue.body());
                        insertStmt.setString(7, issue.state());
                        insertStmt.setString(8, issue.author());
                        insertStmt.setString(9, issue.htmlUrl());
                        insertStmt.setString(10, updatedStr);
                        insertStmt.setInt(11, issue.commentCount());
                        insertStmt.setString(12, issue.assignees());
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

    public List<IssueItem> listByRepository(String repoFullName, String stateFilter) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT account_alias, repository, number, title, body, state, author, html_url, updated_at, comments_count, assignees
            FROM issues
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

    private List<IssueItem> mapList(ResultSet rs) throws SQLException {
        List<IssueItem> list = new ArrayList<>();
        while (rs.next()) {
            String updatedStr = rs.getString("updated_at");
            Instant updatedAt = null;
            if (updatedStr != null && !updatedStr.isBlank()) {
                try {
                    updatedAt = Instant.parse(updatedStr);
                } catch (Exception ignored) {}
            }

            list.add(new IssueItem(
                    rs.getString("account_alias"),
                    rs.getString("repository"),
                    rs.getInt("number"),
                    rs.getString("title"),
                    rs.getString("body"),
                    rs.getString("state"),
                    rs.getString("author"),
                    rs.getString("html_url"),
                    updatedAt,
                    rs.getInt("comments_count"),
                    rs.getString("assignees")
            ));
        }
        return list;
    }
}
