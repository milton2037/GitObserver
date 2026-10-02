package com.gitobserver.ui;

import com.gitobserver.AppContext;
import com.gitobserver.GitObserverApp;
import com.gitobserver.github.GitHubClient;
import com.gitobserver.model.Account;
import com.gitobserver.model.UserProfile;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.UUID;
import java.util.function.Predicate;

public final class AccountDialog extends Dialog<Account> {
    private final AppContext context;
    private final Account existing;
    private final TextField alias = new TextField();
    private final PasswordField token = new PasswordField();
    private final TextField tokenVisible = new TextField();
    private final CheckBox showToken = new CheckBox("Mostrar token");
    private final Label status = new Label();
    private final Button testButton = Ui.secondary("Probar conexión");
    private UserProfile verified;
    private boolean syncing;

    public AccountDialog(Window owner, AppContext context, Account existing, Predicate<String> aliasTaken) {
        this.context = context;
        this.existing = existing;
        setTitle(existing == null ? "Agregar cuenta" : "Editar cuenta");
        if (owner != null) {
            initOwner(owner);
        }
        initModality(Modality.APPLICATION_MODAL);
        setHeaderText(existing == null ? "Nueva cuenta de GitHub" : "Editar cuenta");

        alias.setPromptText("Por ejemplo, Trabajo o Personal");
        String prompt = existing == null
                ? "ghp_... o github_pat_..."
                : "Déjalo vacío para conservar el token actual";
        token.setPromptText(prompt);
        tokenVisible.setPromptText(prompt);
        tokenVisible.setVisible(false);
        tokenVisible.setManaged(false);
        if (existing != null) {
            alias.setText(existing.getAlias());
        }

        token.textProperty().addListener((obs, oldValue, newValue) -> {
            if (!syncing) {
                verified = null;
            }
        });
        tokenVisible.textProperty().addListener((obs, oldValue, newValue) -> {
            if (!syncing) {
                verified = null;
            }
        });
        showToken.selectedProperty().addListener((obs, oldValue, selected) -> toggleToken(selected));

        status.setWrapText(true);
        status.getStyleClass().add("muted");
        testButton.setOnAction(event -> testConnection());

        VBox form = new VBox(10,
                fieldLabel("Alias"),
                alias,
                fieldLabel("Token de acceso personal"),
                token,
                tokenVisible,
                showToken,
                testButton,
                status
        );
        form.setPadding(new Insets(8, 0, 0, 0));
        form.setPrefWidth(460);
        getDialogPane().setContent(form);

        ButtonType saveType = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        Button save = (Button) getDialogPane().lookupButton(saveType);
        save.getStyleClass().add("primary");
        save.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (!validate(aliasTaken)) {
                event.consume();
            }
        });
        setResultConverter(button -> button == saveType ? buildAccount() : null);

        var stylesheet = GitObserverApp.class.getResource(GitObserverApp.STYLESHEET);
        if (stylesheet != null) {
            getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
        }
    }

    private void toggleToken(boolean visible) {
        syncing = true;
        if (visible) {
            tokenVisible.setText(token.getText());
            token.setVisible(false);
            token.setManaged(false);
            tokenVisible.setVisible(true);
            tokenVisible.setManaged(true);
        } else {
            token.setText(tokenVisible.getText());
            tokenVisible.setVisible(false);
            tokenVisible.setManaged(false);
            token.setVisible(true);
            token.setManaged(true);
        }
        syncing = false;
    }

    private boolean validate(Predicate<String> aliasTaken) {
        String aliasText = alias.getText() == null ? "" : alias.getText().strip();
        if (aliasText.isBlank()) {
            showStatus("Escribe un alias para identificar la cuenta.", false);
            return false;
        }
        if (aliasTaken.test(aliasText)) {
            showStatus("Ya existe una cuenta con ese alias.", false);
            return false;
        }
        String typed = currentToken();
        if (typed.isEmpty() && existing == null) {
            showStatus("Pega el token de acceso personal.", false);
            return false;
        }
        if (!typed.isEmpty() && typed.length() < 10) {
            showStatus("El token parece incompleto.", false);
            return false;
        }
        return true;
    }

    private void testConnection() {
        String value = tokenOrExisting();
        if (value == null) {
            showStatus("Escribe un token para probar la conexión.", false);
            return;
        }
        if (!context.tryAcquire()) {
            showStatus("Espera a que termine la consulta en curso.", false);
            return;
        }
        testButton.setDisable(true);
        showStatus("Comprobando token...", true);
        Task<UserProfile> task = new Task<>() {
            @Override
            protected UserProfile call() {
                return new GitHubClient(value, context.rateLimits()).authenticatedUser();
            }
        };
        task.setOnSucceeded(event -> {
            context.release();
            context.refreshRateLimit();
            testButton.setDisable(false);
            verified = task.getValue();
            String name = verified.name() == null || verified.name().isBlank() ? "" : " (" + verified.name() + ")";
            showStatus("Conectado como @" + verified.login() + name, true);
        });
        task.setOnFailed(event -> {
            context.release();
            context.refreshRateLimit();
            testButton.setDisable(false);
            verified = null;
            showStatus(Ui.messageOf(task.getException()), false);
        });
        Thread thread = new Thread(task, "gitobserver-auth");
        thread.setDaemon(true);
        thread.start();
    }

    private Account buildAccount() {
        Account account = new Account();
        account.setId(existing == null ? UUID.randomUUID().toString() : existing.getId());
        account.setAlias(alias.getText().strip());
        String typed = currentToken();
        if (typed.isEmpty() && existing != null) {
            account.setToken(existing.getToken());
            account.setLogin(existing.getLogin());
            account.setDisplayName(existing.getDisplayName());
        } else {
            account.setToken(typed);
        }
        if (verified != null) {
            account.setLogin(verified.login());
            account.setDisplayName(verified.name());
        }
        return account;
    }

    private String currentToken() {
        String value = showToken.isSelected() ? tokenVisible.getText() : token.getText();
        return value == null ? "" : value.strip();
    }

    private String tokenOrExisting() {
        String typed = currentToken();
        if (!typed.isEmpty()) {
            return typed;
        }
        if (existing != null && existing.getToken() != null && !existing.getToken().isBlank()) {
            return existing.getToken();
        }
        return null;
    }

    private void showStatus(String message, boolean ok) {
        status.setText(message);
        status.getStyleClass().removeAll("muted", "status-ok", "status-error");
        status.getStyleClass().add(ok ? "status-ok" : "status-error");
    }

    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }
}
