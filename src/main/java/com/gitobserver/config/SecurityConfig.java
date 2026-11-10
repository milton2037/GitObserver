package com.gitobserver.config;

import com.gitobserver.model.AccessLogEntry;
import com.gitobserver.model.AppUser;
import com.gitobserver.security.UserRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Configuración de seguridad: usuarios, roles, auditoría y cierre por inactividad.
 */
public final class SecurityConfig {
    public static final int MAX_ACCESS_LOG = 500;
    public static final int MAX_IDLE_MINUTES = 240;

    private boolean enabled;
    private String username;
    private String passwordHash;
    private String salt;
    private int idleTimeoutMinutes;
    private List<AppUser> users = new ArrayList<>();
    private List<AccessLogEntry> accessLog = new ArrayList<>();

    public SecurityConfig() {
    }

    public SecurityConfig(boolean enabled, String username, String passwordHash, String salt) {
        this.enabled = enabled;
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public int getIdleTimeoutMinutes() {
        return idleTimeoutMinutes;
    }

    public void setIdleTimeoutMinutes(int idleTimeoutMinutes) {
        this.idleTimeoutMinutes = idleTimeoutMinutes;
    }

    public List<AppUser> getUsers() {
        if (users == null) {
            users = new ArrayList<>();
        }
        return users;
    }

    public void setUsers(List<AppUser> users) {
        this.users = users == null ? new ArrayList<>() : users;
    }

    public List<AccessLogEntry> getAccessLog() {
        if (accessLog == null) {
            accessLog = new ArrayList<>();
        }
        return accessLog;
    }

    public void setAccessLog(List<AccessLogEntry> accessLog) {
        this.accessLog = accessLog == null ? new ArrayList<>() : accessLog;
    }

    public void normalize() {
        if (users == null) {
            users = new ArrayList<>();
        }
        if (accessLog == null) {
            accessLog = new ArrayList<>();
        }
        migrateLegacyUser();
        users.removeIf(user -> user == null);
        for (AppUser user : users) {
            user.normalize();
        }
        if (idleTimeoutMinutes < 0) {
            idleTimeoutMinutes = 0;
        }
        if (idleTimeoutMinutes > MAX_IDLE_MINUTES) {
            idleTimeoutMinutes = MAX_IDLE_MINUTES;
        }
        while (accessLog.size() > MAX_ACCESS_LOG) {
            accessLog.remove(accessLog.size() - 1);
        }
        syncLegacyFields();
        if (enabled && users.isEmpty()) {
            enabled = false;
        }
    }

    private void migrateLegacyUser() {
        if (!users.isEmpty()) {
            return;
        }
        if (username == null || username.isBlank()
                || passwordHash == null || passwordHash.isBlank()
                || salt == null || salt.isBlank()) {
            return;
        }
        AppUser admin = new AppUser();
        admin.setId(UUID.randomUUID().toString());
        admin.setUsername(username.strip());
        admin.setPasswordHash(passwordHash.strip());
        admin.setSalt(salt.strip());
        admin.setRole(UserRole.ADMINISTRADOR);
        admin.setEnabled(true);
        users.add(admin);
    }

    private void syncLegacyFields() {
        if (users.isEmpty()) {
            if (!enabled) {
                username = null;
                passwordHash = null;
                salt = null;
            }
            return;
        }
        AppUser first = users.get(0);
        username = first.getUsername();
        passwordHash = first.getPasswordHash();
        salt = first.getSalt();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SecurityConfig that)) {
            return false;
        }
        return enabled == that.enabled
                && idleTimeoutMinutes == that.idleTimeoutMinutes
                && Objects.equals(username, that.username)
                && Objects.equals(passwordHash, that.passwordHash)
                && Objects.equals(salt, that.salt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, username, passwordHash, salt, idleTimeoutMinutes);
    }
}
