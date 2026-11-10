package com.gitobserver.ui;

import com.gitobserver.AppContext;
import com.gitobserver.model.AccessLogEntry;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class AuditView {
    private final AppContext context;
    private final VBox content = new VBox(14);
    private final ObservableList<AccessLogEntry> rows = FXCollections.observableArrayList();
    private final TableView<AccessLogEntry> table = new TableView<>(rows);

    public AuditView(AppContext context) {
        this.context = context;
        build();
    }

    public Node root() {
        return content;
    }

    public void onShow() {
        rows.setAll(context.accessLog());
        table.refresh();
    }

    private void build() {
        content.setPadding(Ui.pagePadding());
        Label help = Ui.muted("Registro de inicios de sesión correctos e intentos fallidos, con fecha, hora y usuario. "
                + "Solo el administrador puede consultar esta auditoría.");

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(Ui.muted("No hay registros de acceso."));
        table.setPrefHeight(420);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
        TableColumn<AccessLogEntry, String> timeCol = new TableColumn<>("Fecha y hora");
        timeCol.setCellValueFactory(data -> {
            var instant = data.getValue().instant();
            return new ReadOnlyStringWrapper(instant == null ? "—" : formatter.format(instant));
        });
        TableColumn<AccessLogEntry, String> userCol = new TableColumn<>("Usuario");
        userCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(nullToDash(data.getValue().getUsername())));
        TableColumn<AccessLogEntry, String> resultCol = new TableColumn<>("Resultado");
        resultCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().isSuccess() ? "Acceso" : "Intento fallido"));
        TableColumn<AccessLogEntry, String> detailCol = new TableColumn<>("Detalle");
        detailCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(nullToDash(data.getValue().getDetail())));
        table.getColumns().setAll(List.of(timeCol, userCol, resultCol, detailCol));

        Label title = new Label("Auditoría de acceso");
        title.getStyleClass().add("section-title");
        VBox card = new VBox(12, title, help, table);
        card.getStyleClass().add("card");
        VBox.setVgrow(table, Priority.ALWAYS);
        content.getChildren().add(card);
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
