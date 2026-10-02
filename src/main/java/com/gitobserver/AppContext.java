package com.gitobserver;

import com.gitobserver.config.AppConfig;
import com.gitobserver.config.ConfigStore;
import com.gitobserver.github.MonitorService;
import com.gitobserver.github.RateLimitTracker;
import com.gitobserver.github.RepositoryCache;
import com.gitobserver.model.Account;
import com.gitobserver.model.RepoRef;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AppContext implements RepositoryCache {
    private final ConfigStore store;
    private final AppConfig config;
    private final ObservableList<Account> accounts = FXCollections.observableArrayList();
    private final ObjectProperty<Account> activeAccount = new SimpleObjectProperty<>();
    private final StringProperty status = new SimpleStringProperty("Listo");
    private final StringProperty rateLimitText = new SimpleStringProperty("Cuota de la API: —");
    private final RateLimitTracker rateLimits = new RateLimitTracker();
    private final MonitorService monitor;
    private final Map<String, List<RepoRef>> repoCache = new LinkedHashMap<>();
    private final AtomicBoolean busy = new AtomicBoolean();
    private boolean loading = true;

    public AppContext(ConfigStore store, AppConfig config) {
        this.store = store;
        this.config = config;
        this.monitor = new MonitorService(rateLimits, this);
        config.normalize();
        accounts.setAll(config.getAccounts());
        activeAccount.set(find(config.getActiveAccountId()));
        loading = false;
    }

    public ObservableList<Account> accounts() {
        return accounts;
    }

    public ObjectProperty<Account> activeAccountProperty() {
        return activeAccount;
    }

    public Account activeAccount() {
        return activeAccount.get();
    }

    public StringProperty statusProperty() {
        return status;
    }

    public StringProperty rateLimitProperty() {
        return rateLimitText;
    }

    public RateLimitTracker rateLimits() {
        return rateLimits;
    }

    public MonitorService monitor() {
        return monitor;
    }

    public ConfigStore store() {
        return store;
    }

    public void refreshRateLimit() {
        rateLimitText.set(rateLimits.text());
    }

    public boolean tryAcquire() {
        return busy.compareAndSet(false, true);
    }

    public void release() {
        busy.set(false);
    }

    public void setActiveAccount(Account account) {
        if (activeAccount.get() == account) {
            return;
        }
        activeAccount.set(account);
        persist();
    }

    public void saveAccount(Account account) {
        int index = indexOf(account.getId());
        if (index >= 0) {
            accounts.set(index, account);
        } else {
            accounts.add(account);
        }
        invalidateRepos(account.getId());
        Account current = activeAccount.get();
        if (current == null || current.getId().equals(account.getId())) {
            activeAccount.set(account);
        }
        persist();
    }

    public void deleteAccount(Account account) {
        accounts.remove(account);
        invalidateRepos(account.getId());
        Account current = activeAccount.get();
        if (current != null && current.getId().equals(account.getId())) {
            activeAccount.set(accounts.isEmpty() ? null : accounts.get(0));
        }
        persist();
    }

    public boolean aliasExists(String alias, String ignoreId) {
        String needle = alias == null ? "" : alias.strip().toLowerCase(Locale.ROOT);
        return accounts.stream().anyMatch(account -> account.getAlias() != null
                && account.getAlias().strip().toLowerCase(Locale.ROOT).equals(needle)
                && (ignoreId == null || !ignoreId.equals(account.getId())));
    }

    @Override
    public List<RepoRef> cachedRepos(String accountId) {
        List<RepoRef> repos = repoCache.get(accountId);
        return repos == null ? null : List.copyOf(repos);
    }

    @Override
    public void cacheRepos(String accountId, List<RepoRef> repos) {
        repoCache.put(accountId, List.copyOf(repos));
    }

    @Override
    public void invalidateRepos(String accountId) {
        repoCache.remove(accountId);
    }

    private Account find(String id) {
        if (id == null) {
            return accounts.isEmpty() ? null : accounts.get(0);
        }
        return accounts.stream().filter(account -> id.equals(account.getId())).findFirst()
                .orElse(accounts.isEmpty() ? null : accounts.get(0));
    }

    private int indexOf(String id) {
        for (int index = 0; index < accounts.size(); index++) {
            if (accounts.get(index).getId().equals(id)) {
                return index;
            }
        }
        return -1;
    }

    private void persist() {
        if (loading) {
            return;
        }
        config.setAccounts(new ArrayList<>(accounts));
        Account active = activeAccount.get();
        config.setActiveAccountId(active == null ? null : active.getId());
        store.save(config);
    }
}
