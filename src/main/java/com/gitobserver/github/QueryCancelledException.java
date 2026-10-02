package com.gitobserver.github;

public final class QueryCancelledException extends RuntimeException {
    public QueryCancelledException() {
        super("Consulta cancelada.");
    }
}
