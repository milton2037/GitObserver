package com.gitobserver.model;

import com.gitobserver.security.UserRole;

import java.util.Objects;
import java.util.UUID;

public final class AppUser {
    private String id;
    private String username;
    private String passwordHash;
    private String salt;
    private UserRole role = UserRole.CONSULTOR;
    private boolean enabled = true;

    public AppUser() {
    }

    public static AppUser create(String username, String passwordHash, String salt, UserRole role) {
        AppUser user = new AppUser();
        user.id = UUID.randomUUID().toString();
        user.username = username == null ? "" : username.strip();
        user.passwordHash = passwordHash;
        user.salt = salt;
        user.role = role == null ? UserRole.CONSULTOR : role;
        user.enabled = true;
        return user;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public UserRole getRole() {
        return role == null ? UserRole.CONSULTOR : role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void normalize() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (username != null) {
            username = username.strip();
        }
        if (role == null) {
            role = UserRole.CONSULTOR;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AppUser user) || id == null || user.id == null) {
            return false;
        }
        return id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
