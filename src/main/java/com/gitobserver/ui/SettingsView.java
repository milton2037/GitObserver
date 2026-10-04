package com.gitobserver.ui;

import com.gitobserver.AppContext;
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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;

public final class SettingsView {
    private final AppContext context;
    private final VBox root = new VBox(14);
    private final TableView<Account> table = new TableView<>();
    private final Button editButton = Ui.secondary("Editar");
    private final Button testButton = Ui.secondary("Probar");
    private final Button deleteButton = Ui.danger("Eliminar");
    private final BooleanProperty testing = new SimpleBooleanProperty(false);

    public SettingsView(AppContext context) {
        this.context = context;
        build();
    }

    public Node root() {
        return root;
    }

    private void build() {
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

        table.setItems(context.accounts());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(Ui.muted("No hay cuentas. Agrega el primer token."));
        table.setPrefHeight(280);
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
        VBox tableCard = new VBox(12, actions, table);
        tableCard.getStyleClass().add("card");
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        root.setPadding(Ui.pagePadding());
        root.getChildren().addAll(helpCard, tableCard);
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
