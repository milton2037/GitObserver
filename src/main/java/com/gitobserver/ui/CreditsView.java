package com.gitobserver.ui;

import com.gitobserver.AppContext;
import com.gitobserver.AppInfo;
import com.gitobserver.update.UpdateService;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class CreditsView {
    private final AppContext context;
    private final VBox root = new VBox(14);

    public CreditsView() {
        this(null);
    }

    public CreditsView(AppContext context) {
        this.context = context;
        build();
    }

    public Node root() {
        return root;
    }

    private void build() {
        root.setPadding(Ui.pagePadding());
        root.setAlignment(Pos.TOP_CENTER);
        root.setFillWidth(true);

        ImageView logoView = new ImageView();
        var stream = getClass().getResourceAsStream("/icons/app-icon-128.png");
        if (stream != null) {
            logoView.setImage(new Image(stream));
            logoView.setFitWidth(52);
            logoView.setFitHeight(52);
            logoView.setPreserveRatio(true);
            logoView.setSmooth(true);
        }

        Label gitLabel = new Label("Git");
        gitLabel.setStyle("-fx-text-fill: #0f172a; -fx-font-size: 24px; -fx-font-weight: 800;");
        Label observerLabel = new Label("Observer");
        observerLabel.setStyle("-fx-text-fill: #0284c7; -fx-font-size: 24px; -fx-font-weight: 800;");
        HBox brandText = new HBox(0, gitLabel, observerLabel);
        brandText.setAlignment(Pos.CENTER_LEFT);

        Label version = new Label("Versión " + AppInfo.VERSION);
        version.getStyleClass().add("credit-version");

        VBox brandInfo = new VBox(2, brandText, version);
        HBox heading = new HBox(14, logoView, brandInfo);
        heading.setAlignment(Pos.CENTER_LEFT);

        Label summary = Ui.muted(AppInfo.DESCRIPTION);
        summary.setWrapText(true);

        VBox identity = Ui.card(heading, summary);
        identity.setMaxWidth(640);
        identity.setAlignment(Pos.CENTER_LEFT);

        VBox updates = buildUpdateCard();

        VBox facts = Ui.card(
                section("Autor"),
                fact("Nombre", AppInfo.AUTHOR),
                fact("GitHub", AppInfo.GITHUB_USER),
                fact("Año", AppInfo.YEAR),
                fact("Versión", AppInfo.VERSION),
                fact("Aplicación", AppInfo.NAME)
        );
        facts.setMaxWidth(640);

        VBox stack = Ui.card(
                section("Componentes"),
                fact("Plataforma", "Java 21"),
                fact("Interfaz", "JavaFX 21.0.6"),
                fact("Datos", "Jackson 2.18.3"),
                fact("Reportes PDF", "Apache PDFBox 3.0.5"),
                fact("Servicio", "API REST de GitHub")
        );
        stack.setMaxWidth(640);

        root.getChildren().addAll(identity, updates, facts, stack);
    }

    private VBox buildUpdateCard() {
        Label title = section("Actualizaciones");
        Label statusLabel = Ui.muted("Comprueba si hay una nueva versión de GitObserver en GitHub Releases.");
        statusLabel.setWrapText(true);

        Label releaseDetails = new Label();
        releaseDetails.getStyleClass().addAll("mono", "muted");
        releaseDetails.setWrapText(true);
        releaseDetails.setVisible(false);
        releaseDetails.setManaged(false);

        Button checkBtn = Ui.primary("Buscar actualizaciones");
        Button downloadBtn = Ui.secondary("Descargar nueva versión");
        downloadBtn.setVisible(false);
        downloadBtn.setManaged(false);

        Button openReleasesBtn = Ui.secondary("Ver Releases en GitHub");
        openReleasesBtn.setOnAction(e -> Ui.openUrl("https://github.com/" + UpdateService.DEFAULT_REPO + "/releases"));

        checkBtn.setOnAction(event -> {
            checkBtn.setDisable(true);
            statusLabel.setText("Consultando GitHub Releases...");
            statusLabel.getStyleClass().setAll("muted");

            java.util.List<String> tokens = new java.util.ArrayList<>();
            if (context != null) {
                if (context.activeAccount() != null && context.activeAccount().getToken() != null) {
                    tokens.add(context.activeAccount().getToken());
                }
                for (com.gitobserver.model.Account acc : context.accounts()) {
                    if (acc.getToken() != null && !acc.getToken().isBlank() && !tokens.contains(acc.getToken())) {
                        tokens.add(acc.getToken());
                    }
                }
            }

            new Thread(() -> {
                try {
                    UpdateService.ReleaseInfo info = new UpdateService().check(AppInfo.VERSION, tokens);
                    Platform.runLater(() -> {
                        checkBtn.setDisable(false);
                        switch (info.status()) {
                            case UPDATE_AVAILABLE -> {
                                statusLabel.setText("¡Hay una nueva versión disponible! (v" + info.latestVersion() + ")");
                                statusLabel.getStyleClass().setAll("status-ok");
                                if (info.releaseNotes() != null && !info.releaseNotes().isBlank()) {
                                    releaseDetails.setText(info.title() + "\n" + info.releaseNotes());
                                    releaseDetails.setVisible(true);
                                    releaseDetails.setManaged(true);
                                }
                                downloadBtn.setText("Descargar v" + info.latestVersion() + " (.exe)");
                                downloadBtn.setVisible(true);
                                downloadBtn.setManaged(true);
                                downloadBtn.setOnAction(e -> Ui.openUrl(info.downloadUrl()));
                                openReleasesBtn.setOnAction(e -> Ui.openUrl(info.releaseUrl()));
                            }
                            case UP_TO_DATE -> {
                                statusLabel.setText("Tienes la versión más reciente instalada (v" + AppInfo.VERSION + ").");
                                statusLabel.getStyleClass().setAll("muted");
                                releaseDetails.setVisible(false);
                                releaseDetails.setManaged(false);
                                downloadBtn.setVisible(false);
                                downloadBtn.setManaged(false);
                                openReleasesBtn.setOnAction(e -> Ui.openUrl(info.releaseUrl()));
                            }
                            case NO_RELEASES_YET -> {
                                statusLabel.setText("El repositorio " + UpdateService.DEFAULT_REPO + " fue localizado en GitHub, pero aún no tiene ningún lanzamiento publicado en Releases.");
                                statusLabel.getStyleClass().setAll("muted");
                                releaseDetails.setVisible(false);
                                releaseDetails.setManaged(false);
                                downloadBtn.setVisible(false);
                                downloadBtn.setManaged(false);
                                openReleasesBtn.setOnAction(e -> Ui.openUrl(info.releaseUrl()));
                            }
                            case REPO_NOT_FOUND_OR_PRIVATE -> {
                                statusLabel.setText("No se pudo acceder a GitHub Releases (HTTP 404).\n"
                                        + "El repositorio '" + UpdateService.DEFAULT_REPO + "' es privado o la cuenta activa no tiene permisos sobre él.\n\n"
                                        + "💡 Para que cualquier persona pueda actualizar la app con solo el instalador, haz el repositorio público en GitHub.");
                                statusLabel.getStyleClass().setAll("status-error");
                                releaseDetails.setVisible(false);
                                releaseDetails.setManaged(false);
                                downloadBtn.setVisible(false);
                                downloadBtn.setManaged(false);
                                openReleasesBtn.setOnAction(e -> Ui.openUrl("https://github.com/" + UpdateService.DEFAULT_REPO));
                            }
                            case RATE_LIMITED -> {
                                statusLabel.setText("Se alcanzó el límite de solicitudes de la API de GitHub. Intenta nuevamente más tarde o agrega un token en Configuración.");
                                statusLabel.getStyleClass().setAll("status-error");
                                releaseDetails.setVisible(false);
                                releaseDetails.setManaged(false);
                                downloadBtn.setVisible(false);
                                downloadBtn.setManaged(false);
                            }
                            case ERROR -> {
                                statusLabel.setText("No se pudo comprobar en GitHub: " + info.message());
                                statusLabel.getStyleClass().setAll("status-error");
                                releaseDetails.setVisible(false);
                                releaseDetails.setManaged(false);
                                downloadBtn.setVisible(false);
                                downloadBtn.setManaged(false);
                            }
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        checkBtn.setDisable(false);
                        statusLabel.setText("No se pudo comprobar en GitHub: " + Ui.messageOf(ex));
                        statusLabel.getStyleClass().setAll("status-error");
                    });
                }
            }, "check-updates").start();
        });

        HBox actions = new HBox(10, checkBtn, downloadBtn, openReleasesBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = Ui.card(title, statusLabel, releaseDetails, actions);
        card.setMaxWidth(640);
        return card;
    }

    private static Label section(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private static HBox fact(String caption, String value) {
        Label name = new Label(caption);
        name.getStyleClass().add("field-label");
        name.setMinWidth(140);
        Label detail = new Label(value);
        detail.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, name, detail);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
