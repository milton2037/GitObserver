package com.gitobserver.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitobserver.model.CommitRecord;
import com.gitobserver.model.ContributorRef;
import com.gitobserver.model.FileChange;
import com.gitobserver.model.RepoRef;
import com.gitobserver.model.UserProfile;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

public final class GitHubClient {
    static final String API = "https://api.github.com";
    private static final int REPO_PAGES = 50;
    private static final int COMMIT_PAGES = 20;
    private static final int CONTRIBUTOR_PAGES = 10;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final String token;
    private final RateLimitTracker rateLimits;
    private final ObjectMapper mapper = new ObjectMapper();

    public GitHubClient(String token, RateLimitTracker rateLimits) {
        this.token = token == null ? "" : token.strip();
        this.rateLimits = rateLimits;
    }

    public UserProfile authenticatedUser() {
        HttpResponse<String> response = send(API + "/user", false);
        JsonNode node = parse(response.body());
        return new UserProfile(text(node, "login"), text(node, "name"));
    }

    public List<RepoRef> listRepositories(BooleanSupplier cancelled) {
        String url = API + "/user/repos?per_page=100&sort=pushed&affiliation=owner,collaborator,organization_member";
        Page batch = readPages(url, REPO_PAGES, false, cancelled);
        List<RepoRef> repos = new ArrayList<>();
        for (JsonNode node : batch.nodes) {
            RepoRef repo = toRepo(node);
            if (!repo.fullName().isBlank()) {
                repos.add(repo);
            }
        }
        if (batch.truncated) {
            throw new GitHubException(0, "Hay demasiados repositorios para cargarlos en una sola consulta.");
        }
        return repos;
    }

    public CommitBatch listCommits(
            String accountAlias,
            String owner,
            String repo,
            Instant since,
            Instant until,
            BooleanSupplier cancelled
    ) {
        return listCommits(accountAlias, owner, repo, since, until, null, COMMIT_PAGES, cancelled);
    }

    public CommitBatch listCommits(
            String accountAlias,
            String owner,
            String repo,
            Instant since,
            Instant until,
            String author,
            int maxPages,
            BooleanSupplier cancelled
    ) {
        StringBuilder urlBuilder = new StringBuilder(API)
                .append("/repos/").append(encode(owner)).append("/").append(encode(repo))
                .append("/commits?per_page=100");
        if (since != null) {
            urlBuilder.append("&since=").append(instantParam(since));
        }
        if (until != null) {
            urlBuilder.append("&until=").append(instantParam(until));
        }
        if (author != null && !author.isBlank()) {
            urlBuilder.append("&author=").append(encode(author.strip()));
        }
        String url = urlBuilder.toString();
        String fullName = owner + "/" + repo;
        int pageLimit = Math.max(1, maxPages);
        List<CommitRecord> commits = new ArrayList<>();
        int page = 0;
        while (url != null && page < pageLimit) {
            ensureActive(cancelled);
            HttpResponse<String> response = send(url, true);
            if (response.statusCode() == 409) {
                return new CommitBatch(List.of(), false);
            }
            JsonNode body = parse(response.body());
            if (!body.isArray()) {
                throw error(response.statusCode(), response.body());
            }
            for (JsonNode node : body) {
                CommitRecord commit = toCommit(accountAlias, fullName, node);
                if (commit != null) {
                    commits.add(commit);
                }
            }
            url = LinkHeader.next(response.headers().firstValue("Link").orElse(null));
            page++;
            if (page >= pageLimit && url != null) {
                return new CommitBatch(commits, true);
            }
        }
        return new CommitBatch(commits, false);
    }

    public ContributorBatch listContributors(String owner, String repo, BooleanSupplier cancelled) {
        String url = API + "/repos/" + encode(owner) + "/" + encode(repo) + "/contributors?per_page=100&anon=1";
        Page batch = readPages(url, CONTRIBUTOR_PAGES, true, cancelled);
        String fullName = owner + "/" + repo;
        Map<String, int[]> linesByAuthor = lineStats(owner, repo, cancelled);
        List<ContributorRef> contributors = new ArrayList<>();
        for (JsonNode node : batch.nodes) {
            String name = text(node, "login");
            if (name.isBlank()) {
                name = text(node, "name");
            }
            if (name.isBlank()) {
                name = text(node, "email");
            }
            if (name.isBlank()) {
                name = "Desconocido";
            }
            int[] lines = linesByAuthor.get(name);
            int additions = lines == null ? -1 : lines[0];
            int deletions = lines == null ? -1 : lines[1];
            contributors.add(new ContributorRef(name, fullName, node.path("contributions").asInt(0), additions, deletions));
        }
        return new ContributorBatch(contributors, batch.truncated);
    }

    public CommitRecord getCommit(String accountAlias, String owner, String repo, String sha, BooleanSupplier cancelled) {
        ensureActive(cancelled);
        String url = API + "/repos/" + encode(owner) + "/" + encode(repo) + "/commits/" + encode(sha);
        HttpResponse<String> response = send(url, false);
        CommitRecord commit = toCommit(accountAlias, owner + "/" + repo, parse(response.body()));
        if (commit == null) {
            throw new GitHubException(0, "GitHub no devolvió la fecha de ese commit.");
        }
        return commit;
    }

    private Map<String, int[]> lineStats(String owner, String repo, BooleanSupplier cancelled) {
        String url = API + "/repos/" + encode(owner) + "/" + encode(repo) + "/stats/contributors";
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                ensureActive(cancelled);
                HttpResponse<String> response = send(url, true, true);
                if (response.statusCode() == 202) {
                    if (attempt == 0) {
                        pauseForStats(cancelled);
                        continue;
                    }
                    return Map.of();
                }
                if (response.statusCode() == 204 || response.body() == null || response.body().isBlank()) {
                    return Map.of();
                }
                JsonNode body = parse(response.body());
                if (!body.isArray()) {
                    return Map.of();
                }
                Map<String, int[]> totals = new HashMap<>();
                for (JsonNode contributor : body) {
                    String login = text(contributor.path("author"), "login");
                    if (login.isBlank()) {
                        continue;
                    }
                    int additions = 0;
                    int deletions = 0;
                    for (JsonNode week : contributor.path("weeks")) {
                        additions += week.path("a").asInt(0);
                        deletions += week.path("d").asInt(0);
                    }
                    totals.put(login, new int[] {additions, deletions});
                }
                return totals;
            }
        } catch (GitHubException ex) {
            return Map.of();
        }
        return Map.of();
    }

    private static void pauseForStats(BooleanSupplier cancelled) {
        try {
            Thread.sleep(1200);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new QueryCancelledException();
        }
        ensureActive(cancelled);
    }

    private Page readPages(String firstUrl, int maxPages, boolean emptyOnConflict, BooleanSupplier cancelled) {
        List<JsonNode> nodes = new ArrayList<>();
        String url = firstUrl;
        int page = 0;
        while (url != null && page < maxPages) {
            ensureActive(cancelled);
            HttpResponse<String> response = send(url, emptyOnConflict);
            if (response.statusCode() == 409 || response.statusCode() == 204
                    || response.body() == null || response.body().isBlank()) {
                return new Page(nodes, false);
            }
            JsonNode body = parse(response.body());
            if (!body.isArray()) {
                throw error(response.statusCode(), response.body());
            }
            body.forEach(nodes::add);
            url = LinkHeader.next(response.headers().firstValue("Link").orElse(null));
            page++;
            if (page >= maxPages && url != null) {
                return new Page(nodes, true);
            }
        }
        return new Page(nodes, false);
    }

    private HttpResponse<String> send(String url, boolean emptyOnConflict) {
        return send(url, emptyOnConflict, false);
    }

    private HttpResponse<String> send(String url, boolean emptyOnConflict, boolean allowAccepted) {
        if (token.isBlank()) {
            throw new GitHubException(401, "El token está vacío.");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "GitObserver")
                .GET()
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            updateRateLimit(response);
            int status = response.statusCode();
            if (status == 409 && emptyOnConflict) {
                return response;
            }
            if (status == 202 && allowAccepted) {
                return response;
            }
            if (status >= 400) {
                throw error(status, response.body());
            }
            return response;
        } catch (GitHubException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new QueryCancelledException();
        } catch (IOException ex) {
            throw new GitHubException(0, "No se pudo conectar con GitHub. Revisa la red e inténtalo de nuevo.");
        }
    }

    private void updateRateLimit(HttpResponse<?> response) {
        if (rateLimits == null) {
            return;
        }
        var remaining = response.headers().firstValue("X-RateLimit-Remaining");
        var limit = response.headers().firstValue("X-RateLimit-Limit");
        if (remaining.isPresent() && limit.isPresent()) {
            rateLimits.update(remaining.get(), limit.get());
        }
    }

    private GitHubException error(int status, String body) {
        String apiMessage = "";
        try {
            if (body != null && !body.isBlank()) {
                apiMessage = parse(body).path("message").asText("");
            }
        } catch (GitHubException ignored) {
            apiMessage = "";
        }
        String lower = apiMessage.toLowerCase();
        String message = switch (status) {
            case 401 -> "El token no es válido, expiró o no tiene permiso para entrar a la cuenta.";
            case 403 -> lower.contains("rate limit") || lower.contains("secondary rate")
                    ? "Se alcanzó el límite de consultas de la API de GitHub. Espera unos minutos y vuelve a intentar."
                    : "GitHub rechazó la consulta. Revisa que el token tenga permiso de lectura del contenido.";
            case 404 -> "No se encontró el recurso. El token puede no tener acceso a ese repositorio.";
            default -> "GitHub respondió con un error (" + status + ").";
        };
        if (!apiMessage.isBlank() && status != 401) {
            message = message + " " + apiMessage;
        }
        return new GitHubException(status, message);
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body == null ? "" : body);
        } catch (IOException ex) {
            throw new GitHubException(0, "GitHub devolvió una respuesta que no se pudo leer.");
        }
    }

    private static RepoRef toRepo(JsonNode node) {
        String fullName = text(node, "full_name");
        String owner = text(node.path("owner"), "login");
        if (owner.isBlank() && fullName.contains("/")) {
            owner = fullName.substring(0, fullName.indexOf('/'));
        }
        return new RepoRef(
                owner,
                text(node, "name"),
                fullName,
                node.path("private").asBoolean(false),
                blankToEmpty(text(node, "language")),
                text(node, "html_url"),
                parseInstant(text(node, "pushed_at")),
                text(node, "description")
        );
    }

    private static CommitRecord toCommit(String accountAlias, String repository, JsonNode node) {
        String whenText = text(node.path("commit").path("committer"), "date");
        if (whenText.isBlank()) {
            whenText = text(node.path("commit").path("author"), "date");
        }
        Instant when = parseInstant(whenText);
        if (when == null) {
            return null;
        }
        String author = text(node.path("author"), "login");
        if (author.isBlank()) {
            author = text(node.path("committer"), "login");
        }
        if (author.isBlank()) {
            author = text(node.path("commit").path("author"), "name");
        }
        if (author.isBlank()) {
            author = "Desconocido";
        }
        int additions = -1;
        int deletions = -1;
        JsonNode stats = node.path("stats");
        if (stats.hasNonNull("additions") || stats.hasNonNull("deletions")) {
            additions = stats.path("additions").asInt(0);
            deletions = stats.path("deletions").asInt(0);
        }
        List<FileChange> files = new ArrayList<>();
        JsonNode fileNodes = node.path("files");
        if (fileNodes.isArray()) {
            for (JsonNode file : fileNodes) {
                String patch = text(file, "patch");
                if (patch.length() > 3500) {
                    patch = patch.substring(0, 3500) + "\n…";
                }
                files.add(new FileChange(
                        text(file, "filename"),
                        text(file, "status"),
                        file.path("additions").asInt(0),
                        file.path("deletions").asInt(0),
                        patch
                ));
            }
        }
        return new CommitRecord(
                accountAlias,
                repository,
                text(node, "sha"),
                text(node, "html_url"),
                author,
                text(node.path("commit"), "message"),
                when,
                additions,
                deletions,
                files
        );
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.asText("");
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void ensureActive(BooleanSupplier cancelled) {
        if ((cancelled != null && cancelled.getAsBoolean()) || Thread.currentThread().isInterrupted()) {
            throw new QueryCancelledException();
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String instantParam(Instant instant) {
        return encode(DateTimeFormatter.ISO_INSTANT.format(instant));
    }

    public record CommitBatch(List<CommitRecord> commits, boolean truncated) {
    }

    public record ContributorBatch(List<ContributorRef> contributors, boolean truncated) {
    }

    private record Page(List<JsonNode> nodes, boolean truncated) {
    }
}
