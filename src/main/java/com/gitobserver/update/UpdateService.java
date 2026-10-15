package com.gitobserver.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Servicio para verificar y obtener actualizaciones de GitObserver publicadas en GitHub Releases.
 */
public final class UpdateService {
    public static final String DEFAULT_REPO = "milton2037/GitObserver";
    private static final String RELEASES_LATEST_API = "https://api.github.com/repos/%s/releases/latest";
    private static final String RELEASES_LIST_API = "https://api.github.com/repos/%s/releases?per_page=5";
    private static final String REPO_INFO_API = "https://api.github.com/repos/%s";
    private static final Duration TIMEOUT = Duration.ofSeconds(12);
    private final String repository;
    private final HttpClient client;
    private final ObjectMapper mapper;

    public enum UpdateStatus {
        UPDATE_AVAILABLE,
        UP_TO_DATE,
        NO_RELEASES_YET,
        REPO_NOT_FOUND_OR_PRIVATE,
        RATE_LIMITED,
        ERROR
    }

    public UpdateService() {
        this(DEFAULT_REPO, HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), new ObjectMapper());
    }

    public UpdateService(String repository, HttpClient client, ObjectMapper mapper) {
        this.repository = repository;
        this.client = client;
        this.mapper = mapper;
    }

    public String getRepository() {
        return repository;
    }

    public ReleaseInfo check(String currentVersion, String token) {
        List<String> tokens = new ArrayList<>();
        if (token != null && !token.isBlank()) {
            tokens.add(token.strip());
        }
        return check(currentVersion, tokens);
    }

    public ReleaseInfo check(String currentVersion, List<String> candidateTokens) {
        List<String> attempts = new ArrayList<>();
        if (candidateTokens != null) {
            for (String t : candidateTokens) {
                if (t != null && !t.isBlank() && !attempts.contains(t.strip())) {
                    attempts.add(t.strip());
                }
            }
        }
        if (attempts.isEmpty()) {
            attempts.add(null);
        }

        ReleaseInfo bestResult = null;

        for (String token : attempts) {
            ReleaseInfo info = checkWithToken(currentVersion, token);
            if (info.status() == UpdateStatus.UPDATE_AVAILABLE || info.status() == UpdateStatus.UP_TO_DATE) {
                return info;
            }
            if (bestResult == null || info.status() == UpdateStatus.NO_RELEASES_YET) {
                bestResult = info;
            }
        }

        return bestResult != null ? bestResult : new ReleaseInfo(
                UpdateStatus.ERROR,
                false,
                currentVersion,
                currentVersion,
                null,
                null,
                "https://github.com/" + repository + "/releases",
                null,
                "No se pudo consultar GitHub Releases."
        );
    }

    private ReleaseInfo checkWithToken(String currentVersion, String token) {
        String repoReleasesUrl = "https://github.com/" + repository + "/releases";

        try {
            // 1. Probar primero el endpoint directo /releases/latest
            HttpResponse<String> response = sendGet(String.format(RELEASES_LATEST_API, repository), token, currentVersion);
            if (response.statusCode() == 200) {
                return parseReleaseNode(response.body(), currentVersion, repoReleasesUrl);
            }

            if (response.statusCode() == 403 || response.statusCode() == 429) {
                return new ReleaseInfo(
                        UpdateStatus.RATE_LIMITED,
                        false,
                        currentVersion,
                        currentVersion,
                        null,
                        null,
                        repoReleasesUrl,
                        null,
                        "Límite de solicitudes de la API de GitHub excedido (código " + response.statusCode() + ")."
                );
            }

            // 2. Si /releases/latest dio 404, verificar /releases (lista todos los lanzamientos, incluyendo pre-releases o drafts)
            HttpResponse<String> listResponse = sendGet(String.format(RELEASES_LIST_API, repository), token, currentVersion);
            if (listResponse.statusCode() == 200) {
                JsonNode listNode = mapper.readTree(listResponse.body());
                if (listNode.isArray() && !listNode.isEmpty()) {
                    return parseReleaseNode(listNode.get(0).toString(), currentVersion, repoReleasesUrl);
                } else {
                    return new ReleaseInfo(
                            UpdateStatus.NO_RELEASES_YET,
                            false,
                            currentVersion,
                            currentVersion,
                            "Sin versiones publicadas",
                            "Aún no se ha publicado ningún lanzamiento en GitHub Releases.",
                            repoReleasesUrl,
                            null,
                            "El repositorio existe en GitHub pero todavía no tiene lanzamientos en Releases."
                    );
                }
            }

            // 3. Si /releases también dio 404, comprobar si el repositorio es privado o no accesible
            if (listResponse.statusCode() == 404 || response.statusCode() == 404) {
                HttpResponse<String> repoResponse = sendGet(String.format(REPO_INFO_API, repository), token, currentVersion);
                if (repoResponse.statusCode() == 404) {
                    return new ReleaseInfo(
                            UpdateStatus.REPO_NOT_FOUND_OR_PRIVATE,
                            false,
                            currentVersion,
                            currentVersion,
                            "Repositorio no accesible",
                            "El repositorio '" + repository + "' es privado o no se encontró en GitHub.",
                            repoReleasesUrl,
                            null,
                            "El repositorio '" + repository + "' es privado o no se encontró en GitHub.\n"
                                    + "Para que cualquier usuario pueda actualizar la aplicación automáticamente, haz el repositorio público en GitHub o añade una cuenta con acceso a dicho repositorio."
                    );
                }
            }

            return new ReleaseInfo(
                    UpdateStatus.ERROR,
                    false,
                    currentVersion,
                    currentVersion,
                    null,
                    null,
                    repoReleasesUrl,
                    null,
                    "GitHub respondió con código HTTP " + response.statusCode()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ReleaseInfo(
                    UpdateStatus.ERROR,
                    false,
                    currentVersion,
                    currentVersion,
                    null,
                    null,
                    repoReleasesUrl,
                    null,
                    "Búsqueda de actualizaciones cancelada."
            );
        } catch (Exception e) {
            return new ReleaseInfo(
                    UpdateStatus.ERROR,
                    false,
                    currentVersion,
                    currentVersion,
                    null,
                    null,
                    repoReleasesUrl,
                    null,
                    e.getMessage() != null ? e.getMessage() : "Error de red o conexión al contactar GitHub."
            );
        }
    }

    private ReleaseInfo parseReleaseNode(String jsonString, String currentVersion, String fallbackUrl) throws IOException {
        JsonNode root = mapper.readTree(jsonString);
        String tagName = root.path("tag_name").asText("");
        String title = root.path("name").asText(tagName);
        String releaseNotes = root.path("body").asText("");
        String htmlUrl = root.path("html_url").asText(fallbackUrl);

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
        UpdateStatus status = updateAvailable ? UpdateStatus.UPDATE_AVAILABLE : UpdateStatus.UP_TO_DATE;
        String latestClean = cleanVersion(tagName);
        String message = updateAvailable
                ? "¡Hay una nueva versión disponible! (v" + latestClean + ")"
                : "Tienes instalada la versión más reciente (v" + currentVersion + ").";

        return new ReleaseInfo(
                status,
                updateAvailable,
                latestClean,
                currentVersion,
                title,
                releaseNotes,
                htmlUrl,
                downloadUrl,
                message
        );
    }

    private HttpResponse<String> sendGet(String url, String token, String currentVersion) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "GitObserver-App/" + currentVersion);

        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token.strip());
        }

        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    public void checkSilentlyAsync(String currentVersion, String token, Consumer<ReleaseInfo> onResult) {
        List<String> tokens = new ArrayList<>();
        if (token != null && !token.isBlank()) {
            tokens.add(token.strip());
        }
        checkSilentlyAsync(currentVersion, tokens, onResult);
    }

    public void checkSilentlyAsync(String currentVersion, List<String> candidateTokens, Consumer<ReleaseInfo> onResult) {
        Thread thread = new Thread(() -> {
            try {
                ReleaseInfo info = check(currentVersion, candidateTokens);
                if (info != null && onResult != null) {
                    onResult.accept(info);
                }
            } catch (Exception ignored) {
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
            UpdateStatus status,
            boolean updateAvailable,
            String latestVersion,
            String currentVersion,
            String title,
            String releaseNotes,
            String releaseUrl,
            String downloadUrl,
            String message
    ) {}
}
