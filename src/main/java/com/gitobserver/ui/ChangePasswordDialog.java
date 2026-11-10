package com.gitobserver.ui;

import com.gitobserver.AppContext;
import com.gitobserver.GitObserverApp;
import com.gitobserver.security.PasswordPolicy;
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

public final class ChangePasswordDialog extends Dialog<Boolean> {
    private final AppContext context;
    private final PasswordField currentPassword = new PasswordField();
    private final TextField currentPasswordVisible = new TextField();
    private final PasswordField password = new PasswordField();
    private final TextField passwordVisible = new TextField();
    private final PasswordField confirmPassword = new PasswordField();
    private final TextField confirmPasswordVisible = new TextField();
    private final CheckBox showPasswords = new CheckBox("Mostrar contraseñas");
    private final Label status = new Label();
    private boolean syncing;

    public ChangePasswordDialog(Window owner, AppContext context) {
        this.context = context;
        setTitle("Cambiar contraseña");
        if (owner != null) {
            initOwner(owner);
        }
        initModality(Modality.APPLICATION_MODAL);
        setHeaderText("Actualiza tu contraseña de acceso");

        setupVisibility(currentPassword, currentPasswordVisible);
        setupVisibility(password, passwordVisible);
        setupVisibility(confirmPassword, confirmPasswordVisible);
        showPasswords.selectedProperty().addListener((obs, oldValue, selected) -> toggleVisibility(selected));

        status.setWrapText(true);
        status.getStyleClass().add("muted");
        Label rules = Ui.muted(PasswordPolicy.rulesDescription());

        VBox form = new VBox(10,
                fieldLabel("Contraseña actual"),
                currentPassword,
                currentPasswordVisible,
                fieldLabel("Nueva contraseña"),
                password,
                passwordVisible,
                fieldLabel("Confirmar nueva contraseña"),
                confirmPassword,
                confirmPasswordVisible,
                showPasswords,
                rules,
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
            if (!submit()) {
                event.consume();
            }
        });
        setResultConverter(button -> button == saveType ? Boolean.TRUE : null);

        var stylesheet = GitObserverApp.class.getResource(GitObserverApp.STYLESHEET);
        if (stylesheet != null) {
            getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
        }
    }

    private boolean submit() {
        String current = secret(currentPassword, currentPasswordVisible);
        String pass = secret(password, passwordVisible);
        String confirm = secret(confirmPassword, confirmPasswordVisible);
        if (!pass.equals(confirm)) {
            showStatus("Las contraseñas no coinciden.");
            return false;
        }
        var error = context.changeOwnPassword(current, pass);
        if (error.isPresent()) {
            showStatus(error.get());
            return false;
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
        toggleField(currentPassword, currentPasswordVisible, visible);
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
}
