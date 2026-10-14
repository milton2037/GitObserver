package com.gitobserver.ui;

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
 * Diálogo modal para solicitar y validar la contraseña actual antes de operaciones sensibles.
 */
public final class PasswordPromptDialog extends Dialog<String> {
    private final PasswordField password = new PasswordField();
    private final TextField passwordVisible = new TextField();
    private final CheckBox showPassword = new CheckBox("Mostrar contraseña");
    private boolean syncing;

    public PasswordPromptDialog(Window owner, String title, String headerText, String promptMessage) {
        setTitle(title);
        if (owner != null) {
            initOwner(owner);
        }
        initModality(Modality.APPLICATION_MODAL);
        setHeaderText(headerText);

        passwordVisible.setVisible(false);
        passwordVisible.setManaged(false);

        password.textProperty().addListener((obs, o, n) -> {
            if (!syncing) {
                syncing = true;
                passwordVisible.setText(n);
                syncing = false;
            }
        });
        passwordVisible.textProperty().addListener((obs, o, n) -> {
            if (!syncing) {
                syncing = true;
                password.setText(n);
                syncing = false;
            }
        });
        showPassword.selectedProperty().addListener((obs, o, selected) -> {
            if (selected) {
                passwordVisible.setText(password.getText());
                password.setVisible(false);
                password.setManaged(false);
                passwordVisible.setVisible(true);
                passwordVisible.setManaged(true);
            } else {
                password.setText(passwordVisible.getText());
                passwordVisible.setVisible(false);
                passwordVisible.setManaged(false);
                password.setVisible(true);
                password.setManaged(true);
            }
        });

        Label message = new Label(promptMessage);
        message.setWrapText(true);

        VBox form = new VBox(10, message, password, passwordVisible, showPassword);
        form.setPadding(new Insets(8, 0, 0, 0));
        form.setPrefWidth(420);
        getDialogPane().setContent(form);

        ButtonType confirmType = new ButtonType("Confirmar", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(confirmType, ButtonType.CANCEL);
        Button confirm = (Button) getDialogPane().lookupButton(confirmType);
        confirm.getStyleClass().add("primary");

        setResultConverter(button -> button == confirmType ? (showPassword.isSelected() ? passwordVisible.getText() : password.getText()) : null);

        var stylesheet = GitObserverApp.class.getResource(GitObserverApp.STYLESHEET);
        if (stylesheet != null) {
            getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
        }
    }
}
