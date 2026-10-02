package com.gitobserver.ui;

import com.gitobserver.GitObserverApp;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.awt.Desktop;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class Ui {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private Ui() {
    }

    public static Label title(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("title");
        return label;
    }

    public static Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        label.setWrapText(true);
        return label;
    }

    public static Button primary(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("primary");
        return button;
    }

    public static Button secondary(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary");
        return button;
    }

    public static Button danger(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("danger");
        return button;
    }

    public static VBox card(Node... children) {
        VBox box = new VBox(8, children);
        box.getStyleClass().add("card");
        return box;
    }

    public static String lineDelta(int additions, int deletions) {
        if (additions < 0 || deletions < 0) {
            return "—";
        }
        return "+" + additions + "  -" + deletions;
    }

    public static String formatInstant(Instant instant) {
        if (instant == null) {
            return "—";
        }
        return DATE_TIME.format(instant);
    }

    public static String messageOf(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return "Ocurrió un error inesperado.";
    }

    public static void alert(Window owner, Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle("GitObserver");
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.getDialogPane().setMinWidth(420);
        applyStyles(alert.getDialogPane());
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.showAndWait();
    }

    public static boolean confirm(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("GitObserver");
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.getDialogPane().setMinWidth(460);
        applyStyles(alert.getDialogPane());
        if (owner != null) {
            alert.initOwner(owner);
        }
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    public static void openUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ex) {
            alert(null, Alert.AlertType.ERROR, "No se pudo abrir el enlace", messageOf(ex));
        }
    }

    public static void openFolder(java.nio.file.Path path) {
        try {
            java.nio.file.Path folder = java.nio.file.Files.isDirectory(path) ? path : path.getParent();
            if (folder != null && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(folder.toFile());
            }
        } catch (Exception ex) {
            alert(null, Alert.AlertType.ERROR, "No se pudo abrir la carpeta", messageOf(ex));
        }
    }

    public static Insets pagePadding() {
        return new Insets(8, 22, 22, 22);
    }

    private static void applyStyles(javafx.scene.control.DialogPane pane) {
        var stylesheet = GitObserverApp.class.getResource(GitObserverApp.STYLESHEET);
        if (stylesheet != null) {
            pane.getStylesheets().add(stylesheet.toExternalForm());
        }
    }
}
