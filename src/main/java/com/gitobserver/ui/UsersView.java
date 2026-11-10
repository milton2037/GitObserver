package com.gitobserver.ui;

import com.gitobserver.AppContext;
import com.gitobserver.model.AppUser;
import com.gitobserver.security.UserRole;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;

public final class UsersView {
    private final AppContext context;
    private final VBox content = new VBox(14);
    private final ObservableList<AppUser> rows = FXCollections.observableArrayList();
    private final TableView<AppUser> table = new TableView<>(rows);

    public UsersView(AppContext context) {
        this.context = context;
        build();
    }

    public Node root() {
        return content;
    }

    public void onShow() {
        refresh();
    }

    private void build() {
        content.setPadding(Ui.pagePadding());
        Label help = Ui.muted("El administrador crea usuarios de la aplicación y les asigna un rol de negocio. "
                + "Administrador: seguridad y configuración. Analista: operación del monitor. Consultor: consulta de tableros.");

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(Ui.muted("No hay usuarios."));
        table.setPrefHeight(360);

        TableColumn<AppUser, String> userCol = new TableColumn<>("Usuario");
        userCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getUsername()));
        TableColumn<AppUser, String> roleCol = new TableColumn<>("Rol");
        roleCol.setCellValueFactory(data -> {
            UserRole role = data.getValue().getRole();
            return new ReadOnlyStringWrapper(role == null ? "" : role.getDisplayName());
        });
        TableColumn<AppUser, String> stateCol = new TableColumn<>("Estado");
        stateCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().isEnabled() ? "Activo" : "Deshabilitado"));
        table.getColumns().setAll(List.of(userCol, roleCol, stateCol));

        Button addButton = Ui.primary("Nuevo usuario");
        Button editButton = Ui.secondary("Editar");
        Button deleteButton = Ui.danger("Eliminar");
        addButton.setOnAction(event -> editUser(null));
        editButton.setOnAction(event -> editUser(selected()));
        deleteButton.setOnAction(event -> deleteSelected());
        editButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        deleteButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        HBox actions = new HBox(10, addButton, editButton, deleteButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Usuarios y roles");
        title.getStyleClass().add("section-title");
        VBox card = new VBox(12, title, help, actions, table);
        card.getStyleClass().add("card");
        VBox.setVgrow(table, Priority.ALWAYS);
        content.getChildren().add(card);
    }

    private void editUser(AppUser existing) {
        UserDialog dialog = new UserDialog(window(), existing);
        dialog.showAndWait().ifPresent(result -> {
            Optional<String> error;
            if (existing == null) {
                error = context.createUser(result.username(), result.password(), result.role());
            } else {
                error = context.updateUser(existing.getId(), result.username(), result.password(), result.role(), result.enabled());
            }
            if (error.isPresent()) {
                Ui.alert(window(), Alert.AlertType.ERROR, "Usuarios", error.get());
                return;
            }
            refresh();
            context.statusProperty().set(existing == null ? "Usuario creado." : "Usuario actualizado.");
        });
    }

    private void deleteSelected() {
        AppUser user = selected();
        if (user == null) {
            return;
        }
        boolean accepted = Ui.confirm(window(), "Eliminar usuario",
                "Se eliminará el usuario " + user.getUsername() + " de este equipo.");
        if (!accepted) {
            return;
        }
        Optional<String> error = context.deleteUser(user.getId());
        if (error.isPresent()) {
            Ui.alert(window(), Alert.AlertType.ERROR, "Usuarios", error.get());
            return;
        }
        refresh();
        context.statusProperty().set("Usuario eliminado.");
    }

    private void refresh() {
        rows.setAll(context.users());
        table.refresh();
    }

    private AppUser selected() {
        return table.getSelectionModel().getSelectedItem();
    }

    private Window window() {
        return content.getScene() == null ? null : content.getScene().getWindow();
    }
}
