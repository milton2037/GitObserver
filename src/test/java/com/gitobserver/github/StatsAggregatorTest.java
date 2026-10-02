package com.gitobserver.github;

import com.gitobserver.model.AuthorCount;
import com.gitobserver.model.BucketCount;
import com.gitobserver.model.CommitRecord;
import com.gitobserver.model.ContributorRef;
import com.gitobserver.model.PersonContribution;
import com.gitobserver.model.RepoActivity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsAggregatorTest {
    @Test
    void sumsTheSamePersonAcrossRepositories() {
        List<PersonContribution> people = StatsAggregator.aggregate(List.of(
                new ContributorRef("ana", "org/a", 2, 10, 1),
                new ContributorRef("ana", "org/b", 3, 4, 2),
                new ContributorRef("luis", "org/a", 1)
        ));

        assertEquals("ana", people.get(0).name());
        assertEquals(5, people.get(0).totalCommits());
        assertEquals(2, people.get(0).repositories().size());
        assertEquals("org/b", people.get(0).repositories().get(0).repository());
        assertEquals("luis", people.get(1).name());
        assertEquals(14, people.get(0).additions());
        assertEquals(3, people.get(0).deletions());
        assertEquals(4, people.get(0).repositories().get(0).additions());
    }

    @Test
    void ranksAuthorsByCommitsInTheRange() {
        Instant now = Instant.parse("2026-10-01T18:30:00Z");
        List<AuthorCount> counts = StatsAggregator.commitsByAuthor(List.of(
                commit("ana", now),
                commit("ana", now.plusSeconds(60)),
                commit("luis", now.plusSeconds(120))
        ));

        assertEquals(2, counts.size());
        assertEquals("ana", counts.get(0).author());
        assertEquals(2, counts.get(0).commits());
    }

    @Test
    void groupsCommitsByLocalDayAndHour() {
        ZoneId zone = ZoneId.of("America/Bogota");
        Instant afternoon = LocalDateTime.of(2026, 10, 1, 18, 30).atZone(zone).toInstant();
        Instant evening = LocalDateTime.of(2026, 10, 1, 18, 45).atZone(zone).toInstant();
        Instant nextMorning = LocalDateTime.of(2026, 10, 2, 9, 5).atZone(zone).toInstant();
        List<CommitRecord> commits = List.of(
                commit("ana", afternoon),
                commit("luis", evening),
                commit("ana", nextMorning)
        );

        List<BucketCount> days = StatsAggregator.commitsByDay(commits, zone);
        assertEquals("2026-10-01", days.get(0).label());
        assertEquals(2, days.get(0).commits());
        assertEquals("2026-10-02", days.get(1).label());

        assertEquals("18:00", StatsAggregator.peakHour(commits, zone));
        List<RepoActivity> repos = StatsAggregator.commitsByRepository(commits);
        assertEquals(1, repos.size());
        assertEquals(3, repos.get(0).commits());
        assertEquals(2, repos.get(0).collaborators());
    }

    @Test
    void detectsGitHubBots() {
        assertTrue(StatsAggregator.isBot("dependabot[bot]"));
        assertFalse(StatsAggregator.isBot("ana"));
    }

    private static CommitRecord commit(String author, Instant when) {
        return new CommitRecord("Trabajo", "org/a", "abc", "https://github.com", author, "cambio", when);
    }
}
