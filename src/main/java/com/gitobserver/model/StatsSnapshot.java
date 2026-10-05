package com.gitobserver.model;

import java.time.Instant;
import java.util.List;

public record StatsSnapshot(
        Instant generatedAt,
        String period,
        String from,
        String to,
        String repository,
        String accounts,
        boolean hideBots,
        boolean cancelled,
        int warnings,
        int commits,
        int people,
        int repositories,
        String peakHour,
        List<AuthorCount> authors,
        List<BucketCount> days,
        List<BucketCount> hours,
        List<RepoActivity> repositoryActivity
) {
    public StatsSnapshot {
        authors = authors == null ? List.of() : List.copyOf(authors);
        days = days == null ? List.of() : List.copyOf(days);
        hours = hours == null ? List.of() : List.copyOf(hours);
        repositoryActivity = repositoryActivity == null ? List.of() : List.copyOf(repositoryActivity);
        period = period == null ? "" : period;
        from = from == null ? "" : from;
        to = to == null ? "" : to;
        repository = repository == null ? "" : repository;
        accounts = accounts == null ? "" : accounts;
        peakHour = peakHour == null || peakHour.isBlank() ? "—" : peakHour;
    }
}
