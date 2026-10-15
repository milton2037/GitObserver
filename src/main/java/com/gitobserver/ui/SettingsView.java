package com.gitobserver.ui;

import com.gitobserver.AppContext;
import com.gitobserver.database.DatabaseConfig;
import com.gitobserver.database.DatabaseManager;
import com.gitobserver.github.GitHubClient;
import com.gitobserver.github.QueryCancelledException;
import com.gitobserver.model.Account;
import com.gitobserver.model.UserProfile;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;

public final class SettingsView {
    private final AppContext context;
    private final VBox content = new VBox(14);
    private final ScrollPane root = new ScrollPane(content);
    private final TableView<Account> table = new TableView<>();
    private final Button editButton = Ui.secondary("Editar");
    private final Button testButton = Ui.secondary("Probar");
    private final Button deleteButton = Ui.danger("Eliminar");
    private final BooleanProperty testing = new SimpleBooleanProperty(false);

    private final Label securityStatusBadge = new Label();
    private final Label securityStatusDesc = Ui.muted("");
    private final Button setupSecurityBtn = Ui.primary("Configurar usuario y contraseña");
    private final Button changeCredentialsBtn = Ui.secondary("Cambiar credenciales");
    private final Button disableSecurityBtn = Ui.danger("Desactivar protección");

    private final Label dbStatusBadge = new Label();
    private final Label dbStatusDesc = Ui.muted("");
    private final Label dbStatsLabel = new Label();
    private final Button configDbBtn = Ui.primary("Configurar base de datos");
    private final Button clearCacheBtn = Ui.secondary("Limpiar caché local");

    public SettingsView(AppContext context) {
        this.context = context;
        build();
        context.securityEnabledProperty().addListener((obs, oldValue, newValue) -> refreshSecurityCard());
    }

    public Node root() {
        return root;
    }

    private void build() {
        root.setFitToWidth(true);
        root.getStyleClass().add("stats-scroll");
        content.setPadding(Ui.pagePadding());
        content.setFillWidth(true);

        Label help = Ui.muted("""
                Cada token corresponde a una cuenta y solo ve los repositorios a los que esa cuenta tiene acceso.
                Para commits basta Contents: Read. Para issues, comentarios y revisiones, \
                el token fino también necesita Issues y Pull requests en lectura y escritura.
                En un token clásico, el alcance repo cubre privados, issues y pull requests.
                Los tokens se guardan solo en este equipo.""");
        Label path = new Label(context.store().file().toString());
        path.getStyleClass().add("mono");
        path.setWrapText(true);
        Button openFolder = Ui.secondary("Abrir carpeta");
        openFolder.setOnAction(event -> Ui.openFolder(context.store().file()));
        VBox helpCard = Ui.card(new Label("Cómo conectar una cuenta"), help, path, openFolder);
        ((Label) helpCard.getChildren().get(0)).getStyleClass().add("section-title");

        VBox securityCard = buildSecurityCard();

        table.setItems(context.accounts());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(Ui.muted("No hay cuentas. Agrega el primer token."));
        table.setPrefHeight(280);
        table.setMinHeight(200);
        TableColumn<Account, String> alias = new TableColumn<>("Alias");
        alias.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getAlias()));
        TableColumn<Account, String> login = new TableColumn<>("Usuario");
        login.setCellValueFactory(data -> new ReadOnlyStringWrapper(displayLogin(data.getValue())));
        TableColumn<Account, String> name = new TableColumn<>("Nombre");
        name.setCellValueFactory(data -> new ReadOnlyStringWrapper(displayName(data.getValue())));
        table.getColumns().setAll(List.of(alias, login, name));

        Button addButton = Ui.primary("Agregar cuenta");
        addButton.setOnAction(event -> editAccount(null));
        editButton.setOnAction(event -> editAccount(selected()));
        testButton.setOnAction(event -> testSelected());
        deleteButton.setOnAction(event -> deleteSelected());
        editButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        testButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull().or(testing));
        deleteButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        HBox actions = new HBox(10, addButton, editButton, testButton, deleteButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        Label accountsTitle = new Label("Cuentas de GitHub");
        accountsTitle.getStyleClass().add("section-title");
        VBox tableCard = new VBox(12, accountsTitle, actions, table);
        tableCard.getStyleClass().add("card");
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox databaseCard = buildDatabaseCard();

        content.getChildren().addAll(helpCard, securityCard, databaseCard, tableCard);
    }

    private VBox buildDatabaseCard() {
        Label title = new Label("Base de datos y caché de consultas");
        title.getStyleClass().add("section-title");

        Label intro = Ui.muted("Almacena un historial y caché local de repositorios, commits, pull requests e issues para acelerar la aplicación y permitir navegación sin conexión. Soporta SQLite local por defecto o conexión a servidores remotos PostgreSQL y MySQL.");
        intro.setWrapText(true);

        configDbBtn.setOnAction(event -> openDatabaseDialog());
        clearCacheBtn.setOnAction(event -> clearDatabaseCache());

        HBox statusRow = new HBox(10, dbStatusBadge, dbStatusDesc);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        dbStatsLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2563eb;");

        HBox actionRow = new HBox(10, configDbBtn, clearCacheBtn);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        refreshDatabaseCard();

        return Ui.card(title, intro, statusRow, dbStatsLabel, actionRow);
    }

    private void refreshDatabaseCard() {
        var cfg = context.database().getManager().getConfig();
        dbStatusBadge.setText(cfg.getType().getDisplayName());
        dbStatusBadge.getStyleClass().setAll("badge-public");
        dbStatusDesc.setText(cfg.getSummary());

        Task<DatabaseManager.CacheStats> statsTask = new Task<>() {
            @Override
            protected DatabaseManager.CacheStats call() {
                return context.database().getStats();
            }
        };
        statsTask.setOnSucceeded(e -> {
            var stats = statsTask.getValue();
            dbStatsLabel.setText(String.format("📊 En caché: %d repositorios · %d commits · %d pull requests · %d issues",
                    stats.repositories(), stats.commits(), stats.pullRequests(), stats.issues()));
        });
        Thread t = new Thread(statsTask, "db-stats-worker");
        t.setDaemon(true);
        t.start();
    }

    private void openDatabaseDialog() {
        var current = context.database().getManager().getConfig();
        DatabaseConfigDialog dialog = new DatabaseConfigDialog(window(), current);
        Optional<DatabaseConfig> result = dialog.showAndWait();
        result.ifPresent(newConfig -> {
            context.updateDatabaseConfig(newConfig);
            refreshDatabaseCard();
            Ui.alert(window(), Alert.AlertType.INFORMATION, "Base de datos",
                    "Configuración de base de datos actualizada exitosamente a " + newConfig.getType().getDisplayName());
        });
    }

    private void clearDatabaseCache() {
        try {
            context.database().clearCache();
            refreshDatabaseCard();
            Ui.alert(window(), Alert.AlertType.INFORMATION, "Caché limpiada",
                    "Se vaciaron los datos locales en caché exitosamente.");
        } catch (Exception ex) {
            Ui.alert(window(), Alert.AlertType.ERROR, "Error",
                    "No se pudo limpiar la caché: " + ex.getMessage());
        }
    }

    private VBox buildSecurityCard() {
        Label title = new Label("Seguridad y acceso a la aplicación");
        title.getStyleClass().add("section-title");

        Label intro = Ui.muted("Protege el acceso a GitObserver mediante un usuario y una contraseña al abrir la aplicación. Esta opción es opcional y se puede activar o desactivar en cualquier momento.");
        intro.setWrapText(true);

        setupSecurityBtn.setOnAction(event -> openSecurityDialog(false));
        changeCredentialsBtn.setOnAction(event -> openSecurityDialog(true));
        disableSecurityBtn.setOnAction(event -> disableSecurity());

        HBox statusRow = new HBox(10, securityStatusBadge, securityStatusDesc);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        HBox actionRow = new HBox(10, setupSecurityBtn, changeCredentialsBtn, disableSecurityBtn);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        refreshSecurityCard();

        return Ui.card(title, intro, statusRow, actionRow);
    }

    private void refreshSecurityCard() {
        boolean enabled = context.isSecurityEnabled();
        if (enabled) {
            securityStatusBadge.setText("Protección activada");
            securityStatusBadge.getStyleClass().setAll("badge-public");
            String user = context.security().getUsername();
            securityStatusDesc.setText("Usuario: @" + (user == null ? "" : user) + " · Se solicitará inicio de sesión al abrir o bloquear la aplicación.");

            setupSecurityBtn.setVisible(false);
            setupSecurityBtn.setManaged(false);

            changeCredentialsBtn.setVisible(true);
            changeCredentialsBtn.setManaged(true);

            disableSecurityBtn.setVisible(true);
            disableSecurityBtn.setManaged(true);
        } else {
            securityStatusBadge.setText("Protección desactivada");
            securityStatusBadge.getStyleClass().setAll("badge-closed");
            securityStatusDesc.setText("Acceso directo sin contraseña al abrir GitObserver.");

            setupSecurityBtn.setVisible(true);
            setupSecurityBtn.setManaged(true);

            changeCredentialsBtn.setVisible(false);
            changeCredentialsBtn.setManaged(false);

            disableSecurityBtn.setVisible(false);
            disableSecurityBtn.setManaged(false);
        }
    }

    private void openSecurityDialog(boolean editing) {
        Window owner = window();
        SecurityDialog dialog = new SecurityDialog(owner, context, editing);
        dialog.showAndWait().ifPresent(res -> {
            context.updateSecurity(true, res.username(), res.password());
            refreshSecurityCard();
            context.statusProperty().set(editing ? "Credenciales de acceso actualizadas." : "Protección de acceso activada.");
        });
    }

    private void disableSecurity() {
        Window owner = window();
        PasswordPromptDialog prompt = new PasswordPromptDialog(
                owner,
                "Desactivar protección",
                "Confirmar desactivación",
                "Ingresa tu contraseña actual para confirmar y desactivar la protección por contraseña:"
        );
        prompt.showAndWait().ifPresent(pass -> {
            if (context.verifyCurrentPassword(pass)) {
                context.disableSecurity();
                refreshSecurityCard();
                context.statusProperty().set("Protección de acceso desactivada.");
            } else {
                Ui.alert(owner, Alert.AlertType.ERROR, "Contraseña incorrecta", "La contraseña ingresada no es válida. La protección sigue activa.");
            }
        });
    }

    private void editAccount(Account existing) {
        Window owner = window();
        AccountDialog dialog = new AccountDialog(
                owner,
                context,
                existing,
                alias -> context.aliasExists(alias, existing == null ? null : existing.getId())
        );
        dialog.showAndWait().ifPresent(account -> {
            context.saveAccount(account);
            table.refresh();
            context.statusProperty().set("Cuenta guardada: " + account.getAlias());
        });
    }

    private void testSelected() {
        Account account = selected();
        if (account == null) {
            return;
        }
        if (!context.tryAcquire()) {
            Ui.alert(window(), javafx.scene.control.Alert.AlertType.WARNING, "Consulta en curso",
                    "Espera a que termine la consulta actual.");
            return;
        }
        testing.set(true);
        context.statusProperty().set("Comprobando " + account.getAlias() + "...");
        Task<UserProfile> task = new Task<>() {
            @Override
            protected UserProfile call() {
                return new GitHubClient(account.getToken(), context.rateLimits()).authenticatedUser();
            }
        };
        task.setOnSucceeded(event -> {
            context.release();
            context.refreshRateLimit();
            testing.set(false);
            UserProfile profile = task.getValue();
            if (context.accounts().contains(account)) {
                account.setLogin(profile.login());
                account.setDisplayName(profile.name());
                context.saveAccount(account);
                table.refresh();
            }
            context.statusProperty().set("Conectado como @" + profile.login());
        });
        task.setOnFailed(event -> {
            context.release();
            context.refreshRateLimit();
            testing.set(false);
            Throwable error = task.getException();
            if (error instanceof QueryCancelledException) {
                context.statusProperty().set("Consulta cancelada.");
                return;
            }
            Ui.alert(window(), javafx.scene.control.Alert.AlertType.ERROR, "El token no respondió", Ui.messageOf(error));
        });
        Thread thread = new Thread(task, "gitobserver-auth");
        thread.setDaemon(true);
        thread.start();
    }

    private void deleteSelected() {
        Account account = selected();
        if (account == null) {
            return;
        }
        boolean accepted = Ui.confirm(
                window(),
                "Eliminar cuenta",
                "Se va a quitar la cuenta " + account.getAlias() + " de este equipo. El token dejará de usarse en GitObserver."
        );
        if (!accepted) {
            return;
        }
        context.deleteAccount(account);
        context.statusProperty().set("Cuenta eliminada.");
    }

    private Account selected() {
        return table.getSelectionModel().getSelectedItem();
    }

    private Window window() {
        return root.getScene() == null ? null : root.getScene().getWindow();
    }

    private static String displayLogin(Account account) {
        return account.getLogin() == null || account.getLogin().isBlank() ? "Sin comprobar" : "@" + account.getLogin();
    }

    private static String displayName(Account account) {
        return account.getDisplayName() == null || account.getDisplayName().isBlank() ? "—" : account.getDisplayName();
    }
}
