package com.gitobserver.ui;

import com.gitobserver.model.CommitRecord;
import com.gitobserver.model.FileChange;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class ChangeDetailPane {
    private final VBox root = new VBox(8);
    private final Label summary = Ui.muted("Selecciona un cambio para ver archivos y diff.");
    private final ListView<FileChange> files = new ListView<>();
    private final TextArea patch = new TextArea();
    private FileChange currentFile;

    public ChangeDetailPane() {
        Label heading = new Label("Archivos del cambio");
        heading.getStyleClass().add("section-title");
        summary.setWrapText(true);
        summary.setMaxHeight(88);
        files.setPrefHeight(128);
        files.setPlaceholder(Ui.muted("Sin archivos."));
        files.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(FileChange item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(item.statusLabel() + "    " + item.filename() + "    " + Ui.lineDelta(item.additions(), item.deletions()));
            }
        });
        files.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, file) -> showPatch(file));
        patch.setEditable(false);
        patch.setWrapText(false);
        patch.getStyleClass().addAll("patch-view", "patch-preview");
        patch.setPromptText("El diff del archivo aparece aquí. Clic para ampliarlo.");
        patch.setCursor(Cursor.HAND);
        patch.addEventFilter(MouseEvent.MOUSE_CLICKED, this::openWhenClicked);
        VBox.setVgrow(patch, Priority.ALWAYS);
        Label hint = Ui.muted("Clic en el recuadro para ver el archivo más grande.");
        root.getChildren().addAll(heading, summary, files, patch, hint);
    }

    public Node root() {
        return root;
    }

    public void showStatus(String message) {
        summary.setText(message);
        files.getItems().clear();
        currentFile = null;
        patch.clear();
    }

    public void showCommit(CommitRecord commit) {
        if (commit == null) {
            showStatus("Selecciona un cambio para ver archivos y diff.");
            return;
        }
        String message = commit.message() == null ? "" : commit.message().strip();
        String lines = commit.hasLineStats() ? " · " + Ui.lineDelta(commit.additions(), commit.deletions()) : "";
        summary.setText(message + "\n" + Ui.formatInstant(commit.committedAt()) + " · " + commit.repository()
                + lines + " · " + commit.files().size() + " archivos");
        files.getItems().setAll(commit.files());
        if (commit.files().isEmpty()) {
            patch.setText("GitHub no devolvió la lista de archivos de este commit.");
            return;
        }
        files.getSelectionModel().selectFirst();
    }

    private void showPatch(FileChange file) {
        currentFile = file;
        if (file == null) {
            patch.clear();
            return;
        }
        if (file.patch().isBlank()) {
            patch.setText(file.statusLabel() + "  " + file.filename()
                    + "\nGitHub no incluyó el diff. Suele pasar en archivos binarios o cambios muy grandes.");
            return;
        }
        patch.setText(file.patch());
    }

    private void openWhenClicked(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        String content = patch.getText();
        if (content == null || content.isBlank()) {
            return;
        }
        event.consume();
        openExpanded(content);
    }

    private void openExpanded(String content) {
        Window owner = root.getScene() == null ? null : root.getScene().getWindow();
        String filename = currentFile == null || currentFile.filename().isBlank()
                ? "Contenido del archivo"
                : currentFile.filename();
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(filename);

        Label heading = new Label(filename);
        heading.getStyleClass().add("title");
        heading.setWrapText(true);
        String meta = currentFile == null
                ? ""
                : currentFile.statusLabel() + "  ·  " + Ui.lineDelta(currentFile.additions(), currentFile.deletions());
        Label details = Ui.muted(meta);

        TextArea area = new TextArea(content);
        area.setEditable(false);
        area.setWrapText(false);
        area.getStyleClass().add("patch-view");
        VBox.setVgrow(area, Priority.ALWAYS);

        Button close = Ui.primary("Cerrar");
        close.setOnAction(event -> stage.close());
        VBox box = new VBox(12, heading, details, area, close);
        box.setPadding(new Insets(20));
        box.getStyleClass().add("app-root");

        var bounds = Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(box, Math.min(1100, bounds.getWidth() * 0.86), Math.min(760, bounds.getHeight() * 0.86));
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });
        stage.setScene(scene);
        stage.setMinWidth(720);
        stage.setMinHeight(480);
        stage.showAndWait();
    }
}
