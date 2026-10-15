package com.gitobserver.database;

import com.gitobserver.database.dao.CommitDao;
import com.gitobserver.database.dao.IssueDao;
import com.gitobserver.database.dao.PullRequestDao;
import com.gitobserver.database.dao.RepositoryDao;
import com.gitobserver.model.CommitRecord;
import com.gitobserver.model.IssueItem;
import com.gitobserver.model.PullItem;
import com.gitobserver.model.RepoRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    private DatabaseConfig config;
    private DatabaseManager manager;

    @BeforeEach
    void setUp() throws SQLException {
        Path dbPath = tempDir.resolve("test_gitobserver.db");
        config = new DatabaseConfig();
        config.setType(DatabaseType.SQLITE);
        config.setSqlitePath(dbPath.toString());

        manager = new DatabaseManager(config);
        manager.initSchema();
    }

    @Test
    void testSchemaInitializationAndStats() {
        DatabaseManager.CacheStats stats = manager.getStats();
        assertEquals(0, stats.repositories());
        assertEquals(0, stats.commits());
        assertEquals(0, stats.pullRequests());
        assertEquals(0, stats.issues());
    }

    @Test
    void testConnectionValidation() {
        DatabaseManager.TestResult result = DatabaseManager.testConnection(config);
        assertTrue(result.success());
        assertTrue(result.message().contains("exitosa"));
    }

    @Test
    void testRepositoryDao() throws SQLException {
        RepositoryDao dao = new RepositoryDao(manager);
        RepoRef repo = new RepoRef(
                "owner1", "repo1", "owner1/repo1", false, "Java",
                "https://github.com/owner1/repo1", Instant.now(), "Demo repository"
        );

        dao.upsert(repo);
        Optional<RepoRef> found = dao.findByFullName("owner1/repo1");
        assertTrue(found.isPresent());
        assertEquals("Java", found.get().language());

        List<RepoRef> all = dao.listAll();
        assertEquals(1, all.size());
        assertEquals("owner1/repo1", all.get(0).fullName());
    }

    @Test
    void testCommitDao() throws SQLException {
        CommitDao dao = new CommitDao(manager);
        Instant now = Instant.now();
        CommitRecord c1 = new CommitRecord(
                "alias1", "owner1/repo1", "sha12345", "https://github.com/c1",
                "alice", "Initial commit", now.minusSeconds(3600), 10, 2, List.of()
        );
        CommitRecord c2 = new CommitRecord(
                "alias1", "owner1/repo1", "sha67890", "https://github.com/c2",
                "bob", "Second commit", now, 25, 5, List.of()
        );

        dao.upsertAll("owner1/repo1", List.of(c1, c2));
        assertEquals(2, dao.countByRepository("owner1/repo1"));

        List<CommitRecord> list = dao.listByRepository("owner1/repo1");
        assertEquals(2, list.size());

        List<CommitRecord> filtered = dao.listByRepositoryAndRange("owner1/repo1", now.minusSeconds(1800), now.plusSeconds(1800));
        assertEquals(1, filtered.size());
        assertEquals("sha67890", filtered.get(0).sha());
    }

    @Test
    void testPullRequestDao() throws SQLException {
        PullRequestDao dao = new PullRequestDao(manager);
        Instant now = Instant.now();
        PullItem pr1 = new PullItem("alias1", "owner1/repo1", 1, "Feature A", "Desc", "open", "alice", "https://pr/1", now, false);
        PullItem pr2 = new PullItem("alias1", "owner1/repo1", 2, "Feature B", "Desc", "closed", "bob", "https://pr/2", now, false);

        dao.upsertAll("owner1/repo1", List.of(pr1, pr2));

        List<PullItem> all = dao.listByRepository("owner1/repo1", "todos");
        assertEquals(2, all.size());

        List<PullItem> open = dao.listByRepository("owner1/repo1", "open");
        assertEquals(1, open.size());
        assertEquals(1, open.get(0).number());
    }

    @Test
    void testIssueDao() throws SQLException {
        IssueDao dao = new IssueDao(manager);
        Instant now = Instant.now();
        IssueItem i1 = new IssueItem("alias1", "owner1/repo1", 10, "Bug 1", "Desc", "open", "alice", "https://issue/10", now, 3, "alice");
        IssueItem i2 = new IssueItem("alias1", "owner1/repo1", 11, "Bug 2", "Desc", "closed", "bob", "https://issue/11", now, 0, "");

        dao.upsertAll("owner1/repo1", List.of(i1, i2));

        List<IssueItem> all = dao.listByRepository("owner1/repo1", "todos");
        assertEquals(2, all.size());

        List<IssueItem> open = dao.listByRepository("owner1/repo1", "open");
        assertEquals(1, open.size());
        assertEquals(10, open.get(0).number());
    }

    @Test
    void testClearCache() throws SQLException {
        RepositoryDao repoDao = new RepositoryDao(manager);
        repoDao.upsert(new RepoRef("o", "r", "o/r", false, "Java", "http", Instant.now(), "desc"));

        CommitDao commitDao = new CommitDao(manager);
        commitDao.upsertAll("o/r", List.of(new CommitRecord("a", "o/r", "s", "http", "auth", "msg", Instant.now())));

        DatabaseManager.CacheStats before = manager.getStats();
        assertEquals(1, before.repositories());
        assertEquals(1, before.commits());

        manager.clearCache();

        DatabaseManager.CacheStats after = manager.getStats();
        assertEquals(0, after.repositories());
        assertEquals(0, after.commits());
    }
}
