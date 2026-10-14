package com.gitobserver.config;

import java.util.Objects;

/**
 * Configuración opcional de seguridad y control de acceso para la aplicación GitObserver.
 */
public final class SecurityConfig {
    private boolean enabled;
    private String username;
    private String passwordHash;
    private String salt;

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

    public void normalize() {
        if (username != null) {
            username = username.strip();
        }
        if (passwordHash != null) {
            passwordHash = passwordHash.strip();
        }
        if (salt != null) {
            salt = salt.strip();
        }
        if (enabled && (username == null || username.isBlank() || passwordHash == null || passwordHash.isBlank() || salt == null || salt.isBlank())) {
            enabled = false;
        }
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
                && Objects.equals(username, that.username)
                && Objects.equals(passwordHash, that.passwordHash)
                && Objects.equals(salt, that.salt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, username, passwordHash, salt);
    }
}
