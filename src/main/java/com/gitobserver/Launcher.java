package com.gitobserver;

import javafx.application.Application;

/** Arranque para el editor. Esta clase no hereda de Application, así JavaFX puede cargarse desde el classpath. */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(GitObserverApp.class, args);
    }
}
