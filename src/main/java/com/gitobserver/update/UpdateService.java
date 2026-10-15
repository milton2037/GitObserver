package com.gitobserver.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Servicio para verificar y obtener actualizaciones de GitObserver publicadas en GitHub Releases.
 */
public final class UpdateService {
    public static final String DEFAULT_REPO = "milton2037/GitObserver";
    private static final String RELEASES_API = "https://api.github.com/repos/%s/releases/latest";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private final String repository;
    private final HttpClient client;
    private final ObjectMapper mapper;

    public UpdateService() {
        this(DEFAULT_REPO, HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), new ObjectMapper());
    }

    public UpdateService(String repository, HttpClient client, ObjectMapper mapper) {
        this.repository = repository;
        this.client = client;
        this.mapper = mapper;
    }

    public ReleaseInfo check(String currentVersion, String token) throws IOException, InterruptedException {
        String url = String.format(RELEASES_API, repository);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "GitObserver-App/" + currentVersion);

        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token.strip());
        }

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            return new ReleaseInfo(false, currentVersion, currentVersion, "Sin lanzamientos", "Aún no hay versiones publicadas en GitHub.", null, null);
        }

        if (response.statusCode() != 200) {
            throw new IOException("GitHub respondió con código HTTP " + response.statusCode());
        }

        JsonNode root = mapper.readTree(response.body());
        String tagName = root.path("tag_name").asText("");
        String title = root.path("name").asText(tagName);
        String releaseNotes = root.path("body").asText("");
        String htmlUrl = root.path("html_url").asText("");

        String downloadUrl = null;
        JsonNode assets = root.path("assets");
        if (assets.isArray()) {
            for (JsonNode asset : assets) {
                String assetName = asset.path("name").asText("").toLowerCase();
                if (assetName.endsWith(".exe") || assetName.endsWith(".msi") || assetName.endsWith(".zip")) {
                    downloadUrl = asset.path("browser_download_url").asText(null);
                    if (assetName.endsWith(".exe")) {
                        break;
                    }
                }
            }
        }
        if (downloadUrl == null || downloadUrl.isBlank()) {
            downloadUrl = htmlUrl;
        }

        boolean updateAvailable = isNewerVersion(tagName, currentVersion);
        return new ReleaseInfo(updateAvailable, cleanVersion(tagName), currentVersion, title, releaseNotes, htmlUrl, downloadUrl);
    }

    public void checkSilentlyAsync(String currentVersion, String token, Consumer<ReleaseInfo> onResult) {
        Thread thread = new Thread(() -> {
            try {
                ReleaseInfo info = check(currentVersion, token);
                if (info != null && onResult != null) {
                    onResult.accept(info);
                }
            } catch (Exception ignored) {
                // Silencioso en segundo plano si no hay conexión o no hay lanzamientos
            }
        }, "gitobserver-update-check");
        thread.setDaemon(true);
        thread.start();
    }

    public static boolean isNewerVersion(String remoteTag, String currentVersion) {
        if (remoteTag == null || remoteTag.isBlank() || currentVersion == null || currentVersion.isBlank()) {
            return false;
        }
        String remote = cleanVersion(remoteTag);
        String current = cleanVersion(currentVersion);

        String[] remoteParts = remote.split("\\.");
        String[] currentParts = current.split("\\.");

        int length = Math.max(remoteParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            int r = i < remoteParts.length ? parsePart(remoteParts[i]) : 0;
            int c = i < currentParts.length ? parsePart(currentParts[i]) : 0;
            if (r > c) {
                return true;
            }
            if (r < c) {
                return false;
            }
        }
        return false;
    }

    public static String cleanVersion(String version) {
        if (version == null) {
            return "";
        }
        String clean = version.strip();
        if (clean.startsWith("v") || clean.startsWith("V")) {
            clean = clean.substring(1).strip();
        }
        return clean;
    }

    private static int parsePart(String part) {
        try {
            int dashIndex = part.indexOf('-');
            if (dashIndex > 0) {
                part = part.substring(0, dashIndex);
            }
            return Integer.parseInt(part.replaceAll("\\D+", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    public record ReleaseInfo(
            boolean updateAvailable,
            String latestVersion,
            String currentVersion,
            String title,
            String releaseNotes,
            String releaseUrl,
            String downloadUrl
    ) {}
}
