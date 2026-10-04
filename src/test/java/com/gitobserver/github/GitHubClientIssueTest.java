package com.gitobserver.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitobserver.model.IssueItem;
import com.gitobserver.model.PullItem;
import com.gitobserver.model.ReviewNote;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubClientIssueTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsAnIssueAndSkipsPullRequestsFromTheIssueList() throws Exception {
        IssueItem issue = GitHubClient.toIssue("Trabajo", "ana/app", mapper.readTree("""
                {"number":4,"title":"Revisar el informe","body":"Deja el resumen al día","state":"open",
                "html_url":"https://github.com/ana/app/issues/4","comments":2,"updated_at":"2026-10-01T15:00:00Z",
                "user":{"login":"ana"},"assignees":[{"login":"luis"},{"login":"marta"}]}
                """));
        assertEquals(4, issue.number());
        assertEquals("ana", issue.author());
        assertEquals("luis, marta", issue.assignees());
        assertTrue(issue.open());

        assertNull(GitHubClient.toIssue("Trabajo", "ana/app", mapper.readTree("""
                {"number":8,"title":"Un pull","pull_request":{"url":"https://api.github.com/pulls/8"}}
                """)));
    }

    @Test
    void readsPullAndReviewLabels() throws Exception {
        PullItem pull = GitHubClient.toPull("Trabajo", "ana/app", mapper.readTree("""
                {"number":8,"title":"Informe","body":"Listo para revisar","state":"open","draft":true,
                "html_url":"https://github.com/ana/app/pull/8","updated_at":"2026-10-02T10:00:00Z",
                "user":{"login":"luis"}}
                """));
        assertEquals("Borrador", pull.stateLabel());

        ReviewNote review = GitHubClient.toReview(mapper.readTree("""
                {"id":15,"state":"CHANGES_REQUESTED","body":"Falta el cierre","submitted_at":"2026-10-02T11:00:00Z",
                "user":{"login":"ana"}}
                """));
        assertEquals("Cambios pedidos", review.stateLabel());
        assertEquals("ana", review.author());
    }
}
