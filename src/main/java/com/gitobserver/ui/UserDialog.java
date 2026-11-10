package com.gitobserver.ui;

import com.gitobserver.GitObserverApp;
import com.gitobserver.model.AppUser;
import com.gitobserver.security.PasswordPolicy;
import com.gitobserver.security.UserRole;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.StringConverter;

public final class UserDialog extends Dialog<UserDialog.Result> {
    private final boolean editing;
    private final TextField username = new TextField();
    private final ComboBox<UserRole> role = new ComboBox<>();
    private final CheckBox enabled = new CheckBox("Usuario activo");
    private final PasswordField password = new PasswordField();
    private final TextField passwordVisible = new TextField();
    private final PasswordField confirmPassword = new PasswordField();
    private final TextField confirmPasswordVisible = new TextField();
    private final CheckBox showPasswords = new CheckBox("Mostrar contraseñas");
    private final Label status = new Label();
    private boolean syncing;

    public UserDialog(Window owner, AppUser existing) {
        this.editing = existing != null;
        setTitle(editing ? "Editar usuario" : "Nuevo usuario");
        if (owner != null) {
            initOwner(owner);
        }
        initModality(Modality.APPLICATION_MODAL);
        setHeaderText(editing ? "Actualizar usuario y rol" : "Crear usuario y asignar rol");

        username.setPromptText("Nombre de usuario");
        role.getItems().setAll(UserRole.values());
        role.setConverter(new StringConverter<>() {
            @Override
            public String toString(UserRole value) {
                return value == null ? "" : value.getDisplayName();
            }

            @Override
            public UserRole fromString(String value) {
                return null;
            }
        });
        role.setValue(UserRole.ANALISTA);
        enabled.setSelected(true);

        if (existing != null) {
            username.setText(existing.getUsername());
            role.setValue(existing.getRole());
            enabled.setSelected(existing.isEnabled());
        }

        setupVisibility(password, passwordVisible);
        setupVisibility(confirmPassword, confirmPasswordVisible);
        showPasswords.selectedProperty().addListener((obs, oldValue, selected) -> toggleVisibility(selected));

        status.setWrapText(true);
        status.getStyleClass().add("muted");
        Label rules = Ui.muted(editing
                ? "Deja la contraseña en blanco para conservarla. " + PasswordPolicy.rulesDescription()
                : PasswordPolicy.rulesDescription());
        Label roleHelp = Ui.muted("Administrador gestiona usuarios y auditoría. Analista opera el monitor. Consultor solo consulta tableros.");

        VBox form = new VBox(10,
                fieldLabel("Nombre de usuario"),
                username,
                fieldLabel("Rol"),
                role,
                roleHelp,
                enabled,
                fieldLabel(editing ? "Nueva contraseña (opcional)" : "Contraseña"),
                password,
                passwordVisible,
                fieldLabel("Confirmar contraseña"),
                confirmPassword,
                confirmPasswordVisible,
                showPasswords,
                rules,
                status
        );
        form.setPadding(new Insets(8, 0, 0, 0));
        form.setPrefWidth(480);
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
        setResultConverter(button -> button == saveType
                ? new Result(username.getText(), role.getValue(), enabled.isSelected(), secret(password, passwordVisible))
                : null);

        var stylesheet = GitObserverApp.class.getResource(GitObserverApp.STYLESHEET);
        if (stylesheet != null) {
            getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
        }
    }

    private boolean validate() {
        var userError = PasswordPolicy.validateUsername(username.getText());
        if (userError.isPresent()) {
            showStatus(userError.get());
            return false;
        }
        if (role.getValue() == null) {
            showStatus("Selecciona un rol.");
            return false;
        }
        String pass = secret(password, passwordVisible);
        String confirm = secret(confirmPassword, confirmPasswordVisible);
        if (!editing || !pass.isBlank()) {
            var passError = PasswordPolicy.validate(pass);
            if (passError.isPresent()) {
                showStatus(passError.get());
                return false;
            }
            if (!pass.equals(confirm)) {
                showStatus("Las contraseñas no coinciden.");
                return false;
            }
        }
        return true;
    }

    private void setupVisibility(PasswordField hidden, TextField visible) {
        visible.setVisible(false);
        visible.setManaged(false);
        hidden.textProperty().addListener((obs, oldValue, value) -> {
            if (!syncing) {
                syncing = true;
                visible.setText(value);
                syncing = false;
            }
        });
        visible.textProperty().addListener((obs, oldValue, value) -> {
            if (!syncing) {
                syncing = true;
                hidden.setText(value);
                syncing = false;
            }
        });
    }

    private void toggleVisibility(boolean visible) {
        toggleField(password, passwordVisible, visible);
        toggleField(confirmPassword, confirmPasswordVisible, visible);
    }

    private static void toggleField(PasswordField hidden, TextField visible, boolean showPlain) {
        if (showPlain) {
            visible.setText(hidden.getText());
            hidden.setVisible(false);
            hidden.setManaged(false);
            visible.setVisible(true);
            visible.setManaged(true);
        } else {
            hidden.setText(visible.getText());
            visible.setVisible(false);
            visible.setManaged(false);
            hidden.setVisible(true);
            hidden.setManaged(true);
        }
    }

    private String secret(PasswordField hidden, TextField visible) {
        String value = showPasswords.isSelected() ? visible.getText() : hidden.getText();
        return value == null ? "" : value;
    }

    private void showStatus(String message) {
        status.setText(message);
        status.getStyleClass().removeAll("muted", "status-ok", "status-error");
        status.getStyleClass().add("status-error");
    }

    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    public record Result(String username, UserRole role, boolean enabled, String password) {
    }
}
