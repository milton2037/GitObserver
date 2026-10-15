package com.gitobserver.database;

import com.gitobserver.database.dao.CommitDao;
import com.gitobserver.database.dao.IssueDao;
import com.gitobserver.database.dao.PullRequestDao;
import com.gitobserver.database.dao.RepositoryDao;
import com.gitobserver.model.CommitRecord;
import com.gitobserver.model.IssueItem;
import com.gitobserver.model.PullItem;
import com.gitobserver.model.RepoRef;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DatabaseService {
    private DatabaseManager manager;
    private RepositoryDao repositoryDao;
    private CommitDao commitDao;
    private PullRequestDao pullRequestDao;
    private IssueDao issueDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "gitobserver-db-worker");
        t.setDaemon(true);
        return t;
    });

    public DatabaseService(DatabaseConfig config) {
        initManager(config);
    }

    private synchronized void initManager(DatabaseConfig config) {
        this.manager = new DatabaseManager(config);
        this.repositoryDao = new RepositoryDao(manager);
        this.commitDao = new CommitDao(manager);
        this.pullRequestDao = new PullRequestDao(manager);
        this.issueDao = new IssueDao(manager);
    }

    public synchronized void reconfigure(DatabaseConfig newConfig) {
        initManager(newConfig);
        initAsync();
    }

    public DatabaseManager getManager() {
        return manager;
    }

    public CompletableFuture<Void> initAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                manager.initSchema();
            } catch (SQLException e) {
                System.err.println("Advertencia al inicializar esquema de base de datos: " + e.getMessage());
            }
        }, executor);
    }

    public void saveRepositoriesAsync(List<RepoRef> repos) {
        if (repos == null || repos.isEmpty()) return;
        executor.submit(() -> {
            try {
                repositoryDao.upsertAll(repos);
            } catch (SQLException e) {
                System.err.println("Error guardando repositorios en base de datos: " + e.getMessage());
            }
        });
    }

    public List<RepoRef> getCachedRepositories() {
        try {
            return repositoryDao.listAll();
        } catch (SQLException e) {
            System.err.println("Error consultando repositorios en base de datos: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public void saveCommitsAsync(String repoFullName, List<CommitRecord> commits) {
        if (commits == null || commits.isEmpty()) return;
        executor.submit(() -> {
            try {
                commitDao.upsertAll(repoFullName, commits);
            } catch (SQLException e) {
                System.err.println("Error guardando commits en base de datos: " + e.getMessage());
            }
        });
    }

    public List<CommitRecord> getCachedCommits(String repoFullName, Instant since, Instant until) {
        try {
            return commitDao.listByRepositoryAndRange(repoFullName, since, until);
        } catch (SQLException e) {
            System.err.println("Error consultando commits en base de datos: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public void savePullRequestsAsync(String repoFullName, List<PullItem> pulls) {
        if (pulls == null || pulls.isEmpty()) return;
        executor.submit(() -> {
            try {
                pullRequestDao.upsertAll(repoFullName, pulls);
            } catch (SQLException e) {
                System.err.println("Error guardando pull requests en base de datos: " + e.getMessage());
            }
        });
    }

    public List<PullItem> getCachedPullRequests(String repoFullName, String state) {
        try {
            return pullRequestDao.listByRepository(repoFullName, state);
        } catch (SQLException e) {
            System.err.println("Error consultando pull requests en base de datos: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public void saveIssuesAsync(String repoFullName, List<IssueItem> issues) {
        if (issues == null || issues.isEmpty()) return;
        executor.submit(() -> {
            try {
                issueDao.upsertAll(repoFullName, issues);
            } catch (SQLException e) {
                System.err.println("Error guardando issues en base de datos: " + e.getMessage());
            }
        });
    }

    public List<IssueItem> getCachedIssues(String repoFullName, String state) {
        try {
            return issueDao.listByRepository(repoFullName, state);
        } catch (SQLException e) {
            System.err.println("Error consultando issues en base de datos: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public DatabaseManager.CacheStats getStats() {
        return manager.getStats();
    }

    public void clearCache() throws SQLException {
        manager.clearCache();
    }
}
