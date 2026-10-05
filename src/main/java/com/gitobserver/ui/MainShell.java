package com.gitobserver.ui;

import com.gitobserver.AppContext;
import com.gitobserver.AppInfo;
import com.gitobserver.model.Account;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

public final class MainShell implements Navigator {
    private final AppContext context;
    private final BorderPane root = new BorderPane();
    private final StackPane stack = new StackPane();
    private final Label pageTitle = new Label("Inicio");
    private final Label pageSubtitle = new Label();
    private final ComboBox<Account> accounts = new ComboBox<>();
    private final DashboardView dashboard;
    private final StatisticsView statistics;
    private final ContributorsView contributors;
    private final ActivityView activity;
    private final SeguimientoView followUp;
    private final SettingsView settings;
    private final CreditsView credits;
    private boolean updatingCombo;

    public MainShell(AppContext context) {
        this.context = context;
        this.dashboard = new DashboardView(context, this);
        this.statistics = new StatisticsView(context);
        this.contributors = new ContributorsView(context);
        this.activity = new ActivityView(context);
        this.followUp = new SeguimientoView(context);
        this.settings = new SettingsView(context);
        this.credits = new CreditsView();
        buildSidebar();
        buildHeader();
        buildStatusBar();
        register(Pages.HOME, dashboard.root());
        register(Pages.STATISTICS, statistics.root());
        register(Pages.CONTRIBUTIONS, contributors.root());
        register(Pages.ACTIVITY, activity.root());
        register(Pages.FOLLOW_UP, followUp.root());
        register(Pages.SETTINGS, settings.root());
        register(Pages.CREDITS, credits.root());
        root.setCenter(stack);
        root.getStyleClass().add("app-root");
        showPage(Pages.HOME);
    }

    public Parent root() {
        return root;
    }

    @Override
    public void showPage(String pageId) {
        for (Node node : stack.getChildren()) {
            boolean active = pageId.equals(node.getUserData());
            node.setVisible(active);
            node.setManaged(active);
            node.setMouseTransparent(!active);
        }
        pageTitle.setText(titleOf(pageId));
        pageSubtitle.setText(subtitleOf(pageId));
        for (Node node : ((VBox) root.getLeft()).getChildren()) {
            if (node instanceof Button button) {
                button.getStyleClass().remove("active");
                if (pageId.equals(button.getUserData())) {
                    button.getStyleClass().add("active");
                }
            }
        }
        switch (pageId) {
            case Pages.HOME -> dashboard.onShow();
            case Pages.STATISTICS -> statistics.onShow();
            case Pages.CONTRIBUTIONS -> contributors.onShow();
            case Pages.ACTIVITY -> activity.onShow();
            case Pages.FOLLOW_UP -> followUp.onShow();
            default -> {
            }
        }
    }

    private void buildSidebar() {
        Label brand = new Label("GitObserver");
        brand.getStyleClass().add("brand");
        Region mark = new Region();
        mark.getStyleClass().add("brand-mark");
        mark.setMinSize(12, 12);
        mark.setPrefSize(12, 12);
        mark.setMaxSize(12, 12);
        HBox brandRow = new HBox(mark, brand);
        brandRow.getStyleClass().add("brand-row");
        Label subtitle = new Label("Monitor de GitHub");
        subtitle.getStyleClass().add("brand-sub");
        VBox header = new VBox(4, brandRow, subtitle);
        header.setPadding(new Insets(4, 8, 12, 8));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Button version = new Button("Versión " + AppInfo.VERSION);
        version.setUserData(Pages.CREDITS);
        version.getStyleClass().add("version-link");
        version.setMaxWidth(Double.MAX_VALUE);
        version.setAlignment(Pos.CENTER_LEFT);
        version.setOnAction(event -> showPage(Pages.CREDITS));

        VBox sidebar = new VBox(6,
                header,
                new Separator(),
                navButton("Inicio", Pages.HOME),
                navButton("Estadísticas", Pages.STATISTICS),
                navButton("Contribuciones", Pages.CONTRIBUTIONS),
                navButton("Actividad", Pages.ACTIVITY),
                navButton("Seguimiento", Pages.FOLLOW_UP),
                navButton("Configuración", Pages.SETTINGS),
                spacer,
                version
        );
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(236);
        sidebar.setMinWidth(236);
        root.setLeft(sidebar);
    }

    private Button navButton(String text, String pageId) {
        Button button = new Button(text);
        button.setUserData(pageId);
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnAction(event -> showPage(pageId));
        return button;
    }

    private void buildHeader() {
        pageTitle.getStyleClass().add("title");
        pageSubtitle.getStyleClass().add("muted");
        VBox titles = new VBox(2, pageTitle, pageSubtitle);

        accounts.setItems(context.accounts());
        accounts.setPrefWidth(260);
        accounts.setPromptText("Sin cuentas");
        accounts.setConverter(new StringConverter<>() {
            @Override
            public String toString(Account account) {
                return account == null ? "" : account.label();
            }

            @Override
            public Account fromString(String value) {
                return null;
            }
        });
        updatingCombo = true;
        accounts.setValue(context.activeAccount());
        updatingCombo = false;
        accounts.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (updatingCombo) {
                return;
            }
            if (newValue == null) {
                Account active = context.activeAccount();
                if (active != null && context.accounts().contains(active)) {
                    javafx.application.Platform.runLater(() -> {
                        updatingCombo = true;
                        accounts.setValue(active);
                        updatingCombo = false;
                    });
                }
                return;
            }
            if (newValue != context.activeAccount()) {
                context.setActiveAccount(newValue);
            }
        });
        context.activeAccountProperty().addListener((obs, oldValue, newValue) -> {
            if (accounts.getValue() != newValue) {
                updatingCombo = true;
                accounts.setValue(newValue);
                updatingCombo = false;
            }
        });

        Label accountLabel = new Label("Cuenta");
        accountLabel.getStyleClass().add("field-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(titles, spacer, accountLabel, accounts);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("top-bar");
        root.setTop(header);
    }

    private void buildStatusBar() {
        Label status = new Label();
        status.textProperty().bind(context.statusProperty());
        status.getStyleClass().add("muted");
        Label rate = new Label();
        rate.textProperty().bind(context.rateLimitProperty());
        rate.getStyleClass().add("muted");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(12, status, spacer, rate);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("status-bar");
        root.setBottom(bar);
    }

    private void register(String pageId, Node node) {
        node.setUserData(pageId);
        node.setVisible(false);
        node.setManaged(false);
        node.setMouseTransparent(true);
        if (node instanceof Region region) {
            region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        }
        stack.getChildren().add(node);
    }

    private static String titleOf(String pageId) {
        return switch (pageId) {
            case Pages.STATISTICS -> "Estadísticas";
            case Pages.CONTRIBUTIONS -> "Contribuciones";
            case Pages.ACTIVITY -> "Actividad";
            case Pages.FOLLOW_UP -> "Seguimiento";
            case Pages.SETTINGS -> "Configuración";
            case Pages.CREDITS -> "Créditos";
            default -> "Inicio";
        };
    }

    private static String subtitleOf(String pageId) {
        return switch (pageId) {
            case Pages.STATISTICS -> "Parámetros del periodo y resumen de commits, colaboradores y horarios.";
            case Pages.CONTRIBUTIONS -> "Commits, líneas y archivos que cambió cada colaborador.";
            case Pages.ACTIVITY -> "Quién subió cambios en una fecha y hora, con archivos y diff.";
            case Pages.FOLLOW_UP -> "Issues, comentarios y revisiones para uno o varios repositorios.";
            case Pages.SETTINGS -> "Tokens de acceso para una o varias cuentas de GitHub.";
            case Pages.CREDITS -> "Autor, versión y componentes de la aplicación.";
            default -> "Repositorios que la cuenta activa puede ver.";
        };
    }
}
