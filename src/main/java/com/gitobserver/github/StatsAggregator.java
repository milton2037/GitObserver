package com.gitobserver.github;

import com.gitobserver.model.AuthorCount;
import com.gitobserver.model.CommitRecord;
import com.gitobserver.model.ContributorRef;
import com.gitobserver.model.PersonContribution;
import com.gitobserver.model.RepoContribution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StatsAggregator {
    private StatsAggregator() {
    }

    public static boolean isBot(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith("[bot]");
    }

    public static List<PersonContribution> aggregate(List<ContributorRef> refs) {
        Map<String, Map<String, RepoTotals>> byPerson = new LinkedHashMap<>();
        for (ContributorRef ref : refs) {
            String name = ref.displayName() == null || ref.displayName().isBlank()
                    ? "Desconocido"
                    : ref.displayName();
            String repository = ref.repository() == null ? "" : ref.repository();
            byPerson.computeIfAbsent(name, key -> new LinkedHashMap<>())
                    .merge(repository, RepoTotals.of(ref), RepoTotals::plus);
        }

        List<PersonContribution> people = new ArrayList<>();
        for (Map.Entry<String, Map<String, RepoTotals>> entry : byPerson.entrySet()) {
            List<RepoContribution> repositories = entry.getValue().entrySet().stream()
                    .map(repo -> new RepoContribution(repo.getKey(), repo.getValue().commits, repo.getValue().additions, repo.getValue().deletions))
                    .sorted(Comparator.comparingInt(RepoContribution::commits).reversed()
                            .thenComparing(RepoContribution::repository))
                    .toList();
            int total = repositories.stream().mapToInt(RepoContribution::commits).sum();
            int additions = sumKnown(repositories.stream().mapToInt(RepoContribution::additions).toArray());
            int deletions = sumKnown(repositories.stream().mapToInt(RepoContribution::deletions).toArray());
            people.add(new PersonContribution(entry.getKey(), total, additions, deletions, repositories));
        }
        people.sort(Comparator.comparingInt(PersonContribution::totalCommits).reversed()
                .thenComparing(PersonContribution::name));
        return people;
    }

    private static int sumKnown(int[] values) {
        boolean known = false;
        int total = 0;
        for (int value : values) {
            if (value >= 0) {
                known = true;
                total += value;
            }
        }
        return known ? total : -1;
    }

    private record RepoTotals(int commits, int additions, int deletions) {
        private static RepoTotals of(ContributorRef ref) {
            return new RepoTotals(ref.commits(), ref.additions(), ref.deletions());
        }

        private RepoTotals plus(RepoTotals other) {
            return new RepoTotals(
                    commits + other.commits,
                    mergeCount(additions, other.additions),
                    mergeCount(deletions, other.deletions)
            );
        }

        private static int mergeCount(int left, int right) {
            if (left < 0) {
                return right;
            }
            if (right < 0) {
                return left;
            }
            return left + right;
        }
    }

    public static List<AuthorCount> commitsByAuthor(List<CommitRecord> commits) {
        Map<String, Integer> counts = new HashMap<>();
        for (CommitRecord commit : commits) {
            String author = commit.author() == null || commit.author().isBlank() ? "Desconocido" : commit.author();
            counts.merge(author, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> new AuthorCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(AuthorCount::commits).reversed().thenComparing(AuthorCount::author))
                .toList();
    }
}
