package com.gitobserver.security;

import com.gitobserver.AppContext;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Scene;
import javafx.scene.input.InputEvent;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Cierra la aplicación si no hay uso durante el tiempo parametrizado (en minutos).
 */
public final class IdleWatch {
    private final AppContext context;
    private final Stage stage;
    private final Timeline timeline;
    private long lastActivityMillis = System.currentTimeMillis();
    private boolean closing;

    public IdleWatch(AppContext context, Stage stage) {
        this.context = context;
        this.stage = stage;
        this.timeline = new Timeline(new KeyFrame(Duration.seconds(10), event -> check()));
        this.timeline.setCycleCount(Animation.INDEFINITE);
    }

    public void attach(Scene scene) {
        scene.addEventFilter(InputEvent.ANY, event -> touch());
        timeline.play();
    }

    public void touch() {
        lastActivityMillis = System.currentTimeMillis();
    }

    private void check() {
        int minutes = context.idleTimeoutMinutes();
        if (minutes <= 0 || closing) {
            return;
        }
        if (context.isSecurityEnabled() && context.currentUser() == null) {
            return;
        }
        long idleMillis = System.currentTimeMillis() - lastActivityMillis;
        if (idleMillis >= minutes * 60_000L) {
            closing = true;
            timeline.stop();
            context.logout();
            stage.close();
        }
    }
}
