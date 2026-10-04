package com.gitobserver.model;

import java.time.Instant;

public record ReviewNote(long id, String author, String state, String body, Instant submittedAt) {
    public String stateLabel() {
        if (state == null) {
            return "Revisión";
        }
        return switch (state) {
            case "APPROVED" -> "Aprobada";
            case "CHANGES_REQUESTED" -> "Cambios pedidos";
            case "COMMENTED" -> "Comentario";
            case "DISMISSED" -> "Descartada";
            case "PENDING" -> "Pendiente";
            default -> state;
        };
    }
}
