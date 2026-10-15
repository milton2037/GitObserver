package com.gitobserver.database.dao;

import com.gitobserver.database.DatabaseManager;
import com.gitobserver.model.RepoRef;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RepositoryDao {
    private final DatabaseManager manager;

    public RepositoryDao(DatabaseManager manager) {
        this.manager = manager;
    }

    public void upsert(RepoRef repo) throws SQLException {
        if (repo == null || repo.fullName() == null || repo.fullName().isBlank()) {
            return;
        }
        upsertAll(List.of(repo));
    }

    public void upsertAll(List<RepoRef> repos) throws SQLException {
        if (repos == null || repos.isEmpty()) {
            return;
        }

        String checkSql = "SELECT 1 FROM repositories WHERE id = ?";
        String updateSql = """
            UPDATE repositories
            SET owner = ?, name = ?, full_name = ?, is_private = ?, language = ?, html_url = ?, pushed_at = ?, description = ?, synced_at = ?
            WHERE id = ?
        """;
        String insertSql = """
            INSERT INTO repositories (id, owner, name, full_name, is_private, language, html_url, pushed_at, description, synced_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        String nowIso = Instant.now().toString();

        try (Connection conn = manager.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                 PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

                for (RepoRef repo : repos) {
                    if (repo == null || repo.fullName() == null || repo.fullName().isBlank()) {
                        continue;
                    }
                    String id = repo.fullName().toLowerCase();

                    checkStmt.setString(1, id);
                    boolean exists;
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        exists = rs.next();
                    }

                    if (exists) {
                        updateStmt.setString(1, repo.owner() != null ? repo.owner() : "");
                        updateStmt.setString(2, repo.name() != null ? repo.name() : "");
                        updateStmt.setString(3, repo.fullName());
                        updateStmt.setInt(4, repo.privateRepo() ? 1 : 0);
                        updateStmt.setString(5, repo.language());
                        updateStmt.setString(6, repo.htmlUrl());
                        updateStmt.setString(7, repo.pushedAt() != null ? repo.pushedAt().toString() : null);
                        updateStmt.setString(8, repo.description());
                        updateStmt.setString(9, nowIso);
                        updateStmt.setString(10, id);
                        updateStmt.addBatch();
                    } else {
                        insertStmt.setString(1, id);
                        insertStmt.setString(2, repo.owner() != null ? repo.owner() : "");
                        insertStmt.setString(3, repo.name() != null ? repo.name() : "");
                        insertStmt.setString(4, repo.fullName());
                        insertStmt.setInt(5, repo.privateRepo() ? 1 : 0);
                        insertStmt.setString(6, repo.language());
                        insertStmt.setString(7, repo.htmlUrl());
                        insertStmt.setString(8, repo.pushedAt() != null ? repo.pushedAt().toString() : null);
                        insertStmt.setString(9, repo.description());
                        insertStmt.setString(10, nowIso);
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

    public List<RepoRef> listAll() throws SQLException {
        String sql = "SELECT owner, name, full_name, is_private, language, html_url, pushed_at, description FROM repositories ORDER BY full_name ASC";
        try (Connection conn = manager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return mapList(rs);
        }
    }

    public Optional<RepoRef> findByFullName(String fullName) throws SQLException {
        if (fullName == null || fullName.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT owner, name, full_name, is_private, language, html_url, pushed_at, description FROM repositories WHERE id = ?";
        try (Connection conn = manager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, fullName.strip().toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    private List<RepoRef> mapList(ResultSet rs) throws SQLException {
        List<RepoRef> list = new ArrayList<>();
        while (rs.next()) {
            list.add(mapRow(rs));
        }
        return list;
    }

    private RepoRef mapRow(ResultSet rs) throws SQLException {
        String pushedStr = rs.getString("pushed_at");
        Instant pushedAt = null;
        if (pushedStr != null && !pushedStr.isBlank()) {
            try {
                pushedAt = Instant.parse(pushedStr);
            } catch (Exception ignored) {}
        }
        return new RepoRef(
                rs.getString("owner"),
                rs.getString("name"),
                rs.getString("full_name"),
                rs.getInt("is_private") == 1,
                rs.getString("language"),
                rs.getString("html_url"),
                pushedAt,
                rs.getString("description")
        );
    }
}
