package com.gitobserver;

import com.gitobserver.config.ConfigStore;
import com.gitobserver.ui.MainShell;
import com.gitobserver.ui.Ui;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

public final class GitObserverApp extends Application {
    public static final String STYLESHEET = "/styles/app.css";

    @Override
    public void start(Stage stage) {
        ConfigStore store = new ConfigStore();
        ConfigStore.LoadResult loaded = store.load();
        AppContext context = new AppContext(store, loaded.config());
        MainShell shell = new MainShell(context);

        Scene scene = new Scene(shell.root(), 1200, 760);
        var stylesheet = GitObserverApp.class.getResource(STYLESHEET);
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }
        stage.setTitle("GitObserver");
        stage.setMinWidth(980);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();

        if (loaded.warning() != null) {
            Ui.alert(stage, Alert.AlertType.WARNING, "Configuración", loaded.warning());
        }
    }
}
