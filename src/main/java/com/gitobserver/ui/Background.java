package com.gitobserver.ui;

import com.gitobserver.AppContext;
import com.gitobserver.github.QueryCancelledException;
import javafx.concurrent.Task;

import java.util.function.Consumer;

public final class Background {
    private Background() {
    }

    public static <T> void run(
            AppContext context,
            Task<T> task,
            Runnable onStart,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure
    ) {
        if (!context.tryAcquire()) {
            Ui.alert(
                    null,
                    javafx.scene.control.Alert.AlertType.WARNING,
                    "Consulta en curso",
                    "Espera a que termine la consulta actual."
            );
            return;
        }
        onStart.run();
        task.setOnSucceeded(event -> {
            context.release();
            context.refreshRateLimit();
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            context.release();
            context.refreshRateLimit();
            Throwable error = task.getException();
            onFailure.accept(error == null ? new IllegalStateException("Ocurrió un error inesperado.") : error);
        });
        task.setOnCancelled(event -> {
            context.release();
            context.refreshRateLimit();
            onFailure.accept(new QueryCancelledException());
        });
        Thread thread = new Thread(task, "gitobserver");
        thread.setDaemon(true);
        thread.start();
    }
}
