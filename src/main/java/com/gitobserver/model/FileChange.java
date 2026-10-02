package com.gitobserver.model;

public record FileChange(String filename, String status, int additions, int deletions, String patch) {
    public FileChange {
        filename = filename == null ? "" : filename;
        status = status == null ? "" : status;
        patch = patch == null ? "" : patch;
    }

    public String statusLabel() {
        return switch (status) {
            case "added" -> "Añadido";
            case "removed" -> "Eliminado";
            case "modified" -> "Modificado";
            case "renamed" -> "Renombrado";
            case "copied" -> "Copiado";
            case "changed" -> "Modificado";
            default -> status.isBlank() ? "Cambio" : status;
        };
    }
}
