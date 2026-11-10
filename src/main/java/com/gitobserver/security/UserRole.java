package com.gitobserver.security;

/**
 * Roles de negocio de GitObserver.
 * El administrador crea usuarios y les asigna uno de estos tres roles.
 */
public enum UserRole {
    ADMINISTRADOR("Administrador"),
    ANALISTA("Analista"),
    CONSULTOR("Consultor");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String description() {
        return switch (this) {
            case ADMINISTRADOR -> "Crea usuarios, asigna roles, consulta auditoría y configura seguridad, tokens y base de datos.";
            case ANALISTA -> "Opera el monitor: repositorios, estadísticas, contribuciones, actividad y seguimiento de issues y pull requests.";
            case CONSULTOR -> "Consulta tableros e informes de repositorios, estadísticas, contribuciones y actividad, sin cambiar la configuración.";
        };
    }

    public boolean allows(Permission permission) {
        if (permission == null) {
            return false;
        }
        return switch (this) {
            case ADMINISTRADOR -> true;
            case ANALISTA -> permission == Permission.VIEW_HOME
                    || permission == Permission.VIEW_STATISTICS
                    || permission == Permission.VIEW_CONTRIBUTIONS
                    || permission == Permission.VIEW_ACTIVITY
                    || permission == Permission.VIEW_FOLLOW_UP
                    || permission == Permission.CHANGE_OWN_PASSWORD;
            case CONSULTOR -> permission == Permission.VIEW_HOME
                    || permission == Permission.VIEW_STATISTICS
                    || permission == Permission.VIEW_CONTRIBUTIONS
                    || permission == Permission.VIEW_ACTIVITY
                    || permission == Permission.CHANGE_OWN_PASSWORD;
        };
    }
}
