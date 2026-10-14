package com.gitobserver.ui;

import com.gitobserver.AppContext;
import com.gitobserver.GitObserverApp;
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

/**
 * Diálogo modal para configurar o actualizar el usuario y la contraseña de la aplicación.
 */
public final class SecurityDialog extends Dialog<SecurityDialog.Result> {
    private final AppContext context;
    private final boolean editing;
    private final PasswordField currentPassword = new PasswordField();
    private final TextField currentPasswordVisible = new TextField();
    private final TextField username = new TextField();
    private final PasswordField password = new PasswordField();
    private final TextField passwordVisible = new TextField();
    private final PasswordField confirmPassword = new PasswordField();
    private final TextField confirmPasswordVisible = new TextField();
    private final CheckBox showPasswords = new CheckBox("Mostrar contraseñas");
    private final Label status = new Label();
    private boolean syncing;

    public SecurityDialog(Window owner, AppContext context, boolean editing) {
        this.context = context;
        this.editing = editing;
        setTitle(editing ? "Cambiar usuario y contraseña" : "Configurar usuario y contraseña");
        if (owner != null) {
            initOwner(owner);
        }
        initModality(Modality.APPLICATION_MODAL);
        setHeaderText(editing ? "Actualizar credenciales de acceso" : "Proteger aplicación con contraseña");

        username.setPromptText("Por ejemplo, admin o tu nombre");
        if (editing && context.security().getUsername() != null) {
            username.setText(context.security().getUsername());
        }

        setupVisibility(currentPassword, currentPasswordVisible);
        setupVisibility(password, passwordVisible);
        setupVisibility(confirmPassword, confirmPasswordVisible);

        showPasswords.selectedProperty().addListener((obs, oldValue, selected) -> toggleVisibility(selected));

        status.setWrapText(true);
        status.getStyleClass().add("muted");

        VBox form = new VBox(10);
        form.setPadding(new Insets(8, 0, 0, 0));
        form.setPrefWidth(460);

        if (editing) {
            form.getChildren().addAll(
                    fieldLabel("Contraseña actual"),
                    currentPassword,
                    currentPasswordVisible
            );
        }

        form.getChildren().addAll(
                fieldLabel("Nombre de usuario"),
                username,
                fieldLabel(editing ? "Nueva contraseña" : "Contraseña"),
                password,
                passwordVisible,
                fieldLabel(editing ? "Confirmar nueva contraseña" : "Confirmar contraseña"),
                confirmPassword,
                confirmPasswordVisible,
                showPasswords,
                status
        );

        getDialogPane().setContent(form);

        ButtonType saveType = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        Button save = (Button) getDialogPane().lookupButton(saveType);
        save.getStyleClass().add("primary");
        save.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (!validate()) {
                event.consume();
            }
        });
        setResultConverter(button -> button == saveType ? new Result(username.getText().strip(), currentSecret(password, passwordVisible)) : null);

        var stylesheet = GitObserverApp.class.getResource(GitObserverApp.STYLESHEET);
        if (stylesheet != null) {
            getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
        }
    }

    private void setupVisibility(PasswordField pf, TextField tf) {
        tf.setVisible(false);
        tf.setManaged(false);
        pf.textProperty().addListener((obs, o, n) -> {
            if (!syncing) {
                syncing = true;
                tf.setText(n);
                syncing = false;
            }
        });
        tf.textProperty().addListener((obs, o, n) -> {
            if (!syncing) {
                syncing = true;
                pf.setText(n);
                syncing = false;
            }
        });
    }

    private void toggleVisibility(boolean visible) {
        toggleField(currentPassword, currentPasswordVisible, visible);
        toggleField(password, passwordVisible, visible);
        toggleField(confirmPassword, confirmPasswordVisible, visible);
    }

    private void toggleField(PasswordField pf, TextField tf, boolean visible) {
        if (visible) {
            tf.setText(pf.getText());
            pf.setVisible(false);
            pf.setManaged(false);
            tf.setVisible(true);
            tf.setManaged(true);
        } else {
            pf.setText(tf.getText());
            tf.setVisible(false);
            tf.setManaged(false);
            pf.setVisible(true);
            pf.setManaged(true);
        }
    }

    private String currentSecret(PasswordField pf, TextField tf) {
        String val = showPasswords.isSelected() ? tf.getText() : pf.getText();
        return val == null ? "" : val;
    }

    private boolean validate() {
        if (editing) {
            String current = currentSecret(currentPassword, currentPasswordVisible);
            if (current.isEmpty()) {
                showStatus("Ingresa tu contraseña actual.", false);
                return false;
            }
            if (!context.verifyCurrentPassword(current)) {
                showStatus("La contraseña actual no es correcta.", false);
                return false;
            }
        }

        String user = username.getText() == null ? "" : username.getText().strip();
        if (user.isBlank()) {
            showStatus("Escribe un nombre de usuario.", false);
            return false;
        }
        if (user.length() < 2) {
            showStatus("El nombre de usuario debe tener al menos 2 caracteres.", false);
            return false;
        }

        String pass = currentSecret(password, passwordVisible);
        String confirm = currentSecret(confirmPassword, confirmPasswordVisible);

        if (pass.isEmpty()) {
            showStatus("Escribe una contraseña.", false);
            return false;
        }
        if (pass.length() < 4) {
            showStatus("La contraseña debe tener al menos 4 caracteres.", false);
            return false;
        }
        if (!pass.equals(confirm)) {
            showStatus("Las contraseñas no coinciden.", false);
            return false;
        }
        return true;
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

    public record Result(String username, String password) {
    }
}
