package com.gitobserver.github;

import com.gitobserver.model.Account;
import com.gitobserver.model.CommitRecord;
import com.gitobserver.model.ContributorRef;
import com.gitobserver.model.FetchReport;
import com.gitobserver.model.RepoRef;
import com.gitobserver.model.TimeRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class MonitorService {
    private final RateLimitTracker rateLimits;
    private final RepositoryCache cache;

    public MonitorService(RateLimitTracker rateLimits, RepositoryCache cache) {
        this.rateLimits = rateLimits;
        this.cache = cache;
    }

    public List<RepoRef> loadRepositories(
            Account account,
            boolean refresh,
            BooleanSupplier cancelled,
            Consumer<String> progress
    ) {
        if (account == null) {
            throw new GitHubException(0, "No hay una cuenta activa.");
        }
        if (!refresh) {
            List<RepoRef> cached = cache.cachedRepos(account.getId());
            if (cached != null) {
                return cached;
            }
        } else {
            cache.invalidateRepos(account.getId());
        }
        report(progress, "Leyendo repositorios de " + account.getAlias() + "...");
        List<RepoRef> repos = clientFor(account).listRepositories(cancelled);
        cache.cacheRepos(account.getId(), repos);
        return repos;
    }

    public FetchReport<ContributorRef> loadContributors(
            List<Account> accounts,
            String repositoryFullName,
            boolean hideBots,
            BooleanSupplier cancelled,
            Consumer<String> progress
    ) {
        List<ContributorRef> contributors = Collections.synchronizedList(new ArrayList<>());
        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        try {
            List<RepoTarget> targets = resolve(accounts, repositoryFullName, cancelled, progress, errors);
            runInParallel(targets, cancelled, progress, errors, "Leyendo colaboradores de ", (target, client) -> {
                GitHubClient.ContributorBatch batch = client.listContributors(
                        target.repo().owner(),
                        target.repo().name(),
                        cancelled
                );
                contributors.addAll(batch.contributors());
                if (batch.truncated()) {
                    errors.add(target.repo().fullName() + ": hay más colaboradores de los que se muestran.");
                }
            });
        } catch (QueryCancelledException ex) {
            return filteredContributors(contributors, errors, hideBots, true);
        }
        return filteredContributors(contributors, errors, hideBots, cancelled.getAsBoolean());
    }

    public FetchReport<CommitRecord> loadCommits(
            List<Account> accounts,
            String repositoryFullName,
            TimeRange range,
            String authorContains,
            boolean hideBots,
            BooleanSupplier cancelled,
            Consumer<String> progress
    ) {
        List<CommitRecord> commits = Collections.synchronizedList(new ArrayList<>());
        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        try {
            List<RepoTarget> targets = resolve(accounts, repositoryFullName, cancelled, progress, errors);
            runInParallel(targets, cancelled, progress, errors, "Leyendo commits de ", (target, client) -> {
                GitHubClient.CommitBatch batch = client.listCommits(
                        target.accountAlias(),
                        target.repo().owner(),
                        target.repo().name(),
                        range.githubSince(),
                        range.githubUntil(),
                        cancelled
                );
                commits.addAll(batch.commits());
                if (batch.truncated()) {
                    errors.add(target.repo().fullName() + ": se mostraron como máximo 2000 commits. Acota el rango.");
                }
            });
        } catch (QueryCancelledException ex) {
            return filteredCommits(commits, errors, authorContains, hideBots, true);
        }
        return filteredCommits(commits, errors, authorContains, hideBots, cancelled.getAsBoolean());
    }

    public CommitRecord loadCommitDetail(List<Account> accounts, CommitRecord commit, BooleanSupplier cancelled) {
        if (commit == null || commit.sha() == null || commit.sha().isBlank()) {
            throw new GitHubException(0, "Ese cambio no tiene un identificador para consultar los archivos.");
        }
        List<String> errors = new ArrayList<>();
        List<RepoTarget> targets = resolve(accounts, commit.repository(), cancelled, message -> { }, errors);
        if (targets.isEmpty()) {
            throw new GitHubException(0, errors.isEmpty()
                    ? "No hay un token con acceso a " + commit.repository() + "."
                    : errors.get(0));
        }
        RepoTarget target = targets.get(0);
        return clientFor(target).getCommit(
                target.accountAlias(),
                target.repo().owner(),
                target.repo().name(),
                commit.sha(),
                cancelled
        );
    }

    public FetchReport<CommitRecord> loadAuthorChanges(
            List<Account> accounts,
            String author,
            List<String> repositories,
            BooleanSupplier cancelled,
            Consumer<String> progress
    ) {
        List<CommitRecord> commits = Collections.synchronizedList(new ArrayList<>());
        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        Map<String, RepoTarget> byRepository = new java.util.LinkedHashMap<>();
        try {
            for (RepoTarget target : resolve(accounts, null, cancelled, progress, errors)) {
                byRepository.putIfAbsent(target.repo().fullName(), target);
            }
            List<RepoTarget> targets = new ArrayList<>();
            for (String repository : repositories) {
                if (targets.size() == 8) {
                    break;
                }
                RepoTarget target = byRepository.get(repository);
                if (target != null) {
                    targets.add(target);
                }
            }
            runInParallel(targets, cancelled, progress, errors, "Leyendo cambios de " + author + " en ", (target, client) -> {
                GitHubClient.CommitBatch batch = client.listCommits(
                        target.accountAlias(),
                        target.repo().owner(),
                        target.repo().name(),
                        null,
                        null,
                        author,
                        1,
                        cancelled
                );
                commits.addAll(batch.commits());
            });
            List<CommitRecord> recent = snapshot(commits).stream()
                    .sorted(Comparator.comparing(CommitRecord::committedAt).reversed())
                    .limit(12)
                    .toList();
            List<CommitRecord> detailed = enrichCommits(recent, byRepository, cancelled, progress, errors);
            return new FetchReport<>(detailed, snapshot(errors), cancelled.getAsBoolean());
        } catch (QueryCancelledException ex) {
            return new FetchReport<>(snapshot(commits), snapshot(errors), true);
        }
    }

    private List<CommitRecord> enrichCommits(
            List<CommitRecord> commits,
            Map<String, RepoTarget> byRepository,
            BooleanSupplier cancelled,
            Consumer<String> progress,
            List<String> errors
    ) {
        if (commits.isEmpty()) {
            return List.of();
        }
        CommitRecord[] detailed = new CommitRecord[commits.size()];
        int threads = Math.min(4, commits.size());
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < commits.size(); index++) {
                int position = index;
                CommitRecord commit = commits.get(index);
                futures.add(pool.submit(() -> {
                    if (cancelled.getAsBoolean()) {
                        detailed[position] = commit;
                        return;
                    }
                    report(progress, "Leyendo archivos de " + commit.repository());
                    RepoTarget target = byRepository.get(commit.repository());
                    if (target == null || commit.sha() == null || commit.sha().isBlank()) {
                        detailed[position] = commit;
                        return;
                    }
                    try {
                        detailed[position] = clientFor(target).getCommit(
                                target.accountAlias(),
                                target.repo().owner(),
                                target.repo().name(),
                                commit.sha(),
                                cancelled
                        );
                    } catch (QueryCancelledException ex) {
                        detailed[position] = commit;
                    } catch (GitHubException ex) {
                        errors.add(commit.repository() + ": " + ex.getMessage());
                        detailed[position] = commit;
                    }
                }));
            }
            for (Future<?> future : futures) {
                if (cancelled.getAsBoolean()) {
                    pool.shutdownNow();
                    throw new QueryCancelledException();
                }
                try {
                    future.get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    pool.shutdownNow();
                    throw new QueryCancelledException();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    if (cause instanceof QueryCancelledException cancelledException) {
                        throw cancelledException;
                    }
                    errors.add(messageOf(cause));
                }
            }
        }
        List<CommitRecord> ready = new ArrayList<>();
        for (CommitRecord commit : detailed) {
            if (commit != null) {
                ready.add(commit);
            }
        }
        return ready;
    }

    private void runInParallel(
            List<RepoTarget> targets,
            BooleanSupplier cancelled,
            Consumer<String> progress,
            List<String> errors,
            String prefix,
            RepoJob job
    ) {
        if (targets.isEmpty()) {
            return;
        }
        AtomicInteger done = new AtomicInteger();
        int total = targets.size();
        int threads = Math.min(4, total);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> futures = new ArrayList<>();
            for (RepoTarget target : targets) {
                futures.add(pool.submit(() -> {
                    if (cancelled.getAsBoolean()) {
                        return;
                    }
                    report(progress, prefix + target.repo().fullName());
                    try {
                        job.run(target, clientFor(target));
                    } catch (QueryCancelledException ex) {
                        // La consulta principal revisa la bandera al terminar.
                    } catch (GitHubException ex) {
                        errors.add(target.repo().fullName() + ": " + ex.getMessage());
                    } catch (RuntimeException ex) {
                        errors.add(target.repo().fullName() + ": " + messageOf(ex));
                    } finally {
                        int current = done.incrementAndGet();
                        report(progress, "Repositorios consultados: " + current + " de " + total);
                    }
                }));
            }
            for (Future<?> future : futures) {
                if (cancelled.getAsBoolean()) {
                    pool.shutdownNow();
                    throw new QueryCancelledException();
                }
                try {
                    future.get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    pool.shutdownNow();
                    throw new QueryCancelledException();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    if (cause instanceof QueryCancelledException cancelledException) {
                        throw cancelledException;
                    }
                    errors.add(messageOf(cause));
                }
            }
        }
    }

    private List<RepoTarget> resolve(
            List<Account> accounts,
            String repositoryFullName,
            BooleanSupplier cancelled,
            Consumer<String> progress,
            List<String> errors
    ) {
        List<RepoTarget> targets = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String only = repositoryFullName == null ? "" : repositoryFullName.strip();
        int index = 1;
        for (Account account : accounts) {
            ensureActive(cancelled);
            report(progress, "Cargando repositorios de " + account.getAlias() + " (" + index + "/" + accounts.size() + ")");
            index++;
            List<RepoRef> repos;
            try {
                repos = loadRepositories(account, false, cancelled, progress);
            } catch (QueryCancelledException ex) {
                throw ex;
            } catch (GitHubException ex) {
                errors.add(account.getAlias() + ": " + ex.getMessage());
                continue;
            }
            for (RepoRef repo : repos) {
                if (!only.isEmpty() && !repo.fullName().equals(only)) {
                    continue;
                }
                if (seen.add(repo.fullName())) {
                    targets.add(new RepoTarget(account, repo));
                }
            }
        }
        return targets;
    }

    private GitHubClient clientFor(Account account) {
        return new GitHubClient(account.getToken(), rateLimits);
    }

    private GitHubClient clientFor(RepoTarget target) {
        return new GitHubClient(target.token(), rateLimits);
    }

    private static FetchReport<ContributorRef> filteredContributors(
            List<ContributorRef> contributors,
            List<String> errors,
            boolean hideBots,
            boolean cancelled
    ) {
        List<ContributorRef> filtered = snapshot(contributors).stream()
                .filter(ref -> !hideBots || !StatsAggregator.isBot(ref.displayName()))
                .toList();
        return new FetchReport<>(filtered, snapshot(errors), cancelled);
    }

    private static FetchReport<CommitRecord> filteredCommits(
            List<CommitRecord> commits,
            List<String> errors,
            String authorContains,
            boolean hideBots,
            boolean cancelled
    ) {
        String needle = authorContains == null ? "" : authorContains.strip().toLowerCase(Locale.ROOT);
        List<CommitRecord> filtered = snapshot(commits).stream()
                .filter(commit -> !hideBots || !StatsAggregator.isBot(commit.author()))
                .filter(commit -> needle.isEmpty()
                        || (commit.author() != null && commit.author().toLowerCase(Locale.ROOT).contains(needle)))
                .sorted(Comparator.comparing(CommitRecord::committedAt).reversed())
                .toList();
        return new FetchReport<>(filtered, snapshot(errors), cancelled);
    }

    private static <T> List<T> snapshot(List<T> items) {
        synchronized (items) {
            return List.copyOf(items);
        }
    }

    private static String messageOf(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "Falló una consulta a GitHub." : message;
    }

    private static void ensureActive(BooleanSupplier cancelled) {
        if (cancelled != null && cancelled.getAsBoolean()) {
            throw new QueryCancelledException();
        }
    }

    private static void report(Consumer<String> progress, String message) {
        if (progress != null) {
            progress.accept(message);
        }
    }

    @FunctionalInterface
    private interface RepoJob {
        void run(RepoTarget target, GitHubClient client);
    }

    private record RepoTarget(String token, String accountAlias, RepoRef repo) {
        private RepoTarget(Account account, RepoRef repo) {
            this(account.getToken(), account.getAlias(), repo);
        }

        @Override
        public String toString() {
            return accountAlias + ":" + repo.fullName();
        }
    }
}
