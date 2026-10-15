package com.gitobserver.config;

import com.gitobserver.model.Account;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AppConfig {
    private List<Account> accounts = new ArrayList<>();
    private String activeAccountId;
    private SecurityConfig security = new SecurityConfig();
    private com.gitobserver.database.DatabaseConfig database = new com.gitobserver.database.DatabaseConfig();

    public List<Account> getAccounts() {
        if (accounts == null) {
            accounts = new ArrayList<>();
        }
        return accounts;
    }

    public void setAccounts(List<Account> accounts) {
        this.accounts = accounts == null ? new ArrayList<>() : accounts;
    }

    public String getActiveAccountId() {
        return activeAccountId;
    }

    public void setActiveAccountId(String activeAccountId) {
        this.activeAccountId = activeAccountId;
    }

    public SecurityConfig getSecurity() {
        if (security == null) {
            security = new SecurityConfig();
        }
        return security;
    }

    public void setSecurity(SecurityConfig security) {
        this.security = security == null ? new SecurityConfig() : security;
    }

    public com.gitobserver.database.DatabaseConfig getDatabase() {
        if (database == null) {
            database = new com.gitobserver.database.DatabaseConfig();
        }
        return database;
    }

    public void setDatabase(com.gitobserver.database.DatabaseConfig database) {
        this.database = database == null ? new com.gitobserver.database.DatabaseConfig() : database;
    }

    public void normalize() {
        if (accounts == null) {
            accounts = new ArrayList<>();
        }
        accounts.removeIf(account -> account == null
                || account.getToken() == null
                || account.getToken().isBlank()
                || account.getAlias() == null
                || account.getAlias().isBlank());
        for (Account account : accounts) {
            account.setAlias(account.getAlias().strip());
            account.setToken(account.getToken().strip());
            if (account.getId() == null || account.getId().isBlank()) {
                account.setId(UUID.randomUUID().toString());
            }
        }
        boolean activeExists = accounts.stream().anyMatch(account -> account.getId().equals(activeAccountId));
        if (!activeExists) {
            activeAccountId = accounts.isEmpty() ? null : accounts.get(0).getId();
        }
        if (security == null) {
            security = new SecurityConfig();
        }
        security.normalize();
    }
}
