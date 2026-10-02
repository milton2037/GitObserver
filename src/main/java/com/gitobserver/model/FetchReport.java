package com.gitobserver.model;

import java.util.List;

public record FetchReport<T>(List<T> items, List<String> errors, boolean cancelled) {
    public FetchReport {
        items = List.copyOf(items);
        errors = List.copyOf(errors);
    }
}
