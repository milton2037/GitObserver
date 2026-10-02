package com.gitobserver.github;

import java.util.concurrent.atomic.AtomicReference;

public final class RateLimitTracker {
    private final AtomicReference<String> text = new AtomicReference<>("Cuota de la API: —");

    public void update(String remaining, String limit) {
        text.set("Cuota de la API: " + remaining + " / " + limit);
    }

    public String text() {
        return text.get();
    }
}
