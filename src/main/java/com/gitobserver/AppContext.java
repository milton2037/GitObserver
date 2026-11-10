package com.gitobserver;

import com.gitobserver.config.AppConfig;
import com.gitobserver.config.ConfigStore;
import com.gitobserver.config.SecurityConfig;
import com.gitobserver.github.MonitorService;
import com.gitobserver.github.RateLimitTracker;
import com.gitobserver.github.RepositoryCache;
import com.gitobserver.model.AccessLogEntry;
import com.gitobserver.model.Account;
import com.gitobserver.model.AppUser;
import com.gitobserver.model.RepoRef;
import com.gitobserver.security.PasswordHasher;
import com.gitobserver.security.PasswordPolicy;
import com.gitobserver.security.Permission;
import com.gitobserver.security.UserRole;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AppContext implements RepositoryCache {
    private final ConfigStore store;
    private final AppConfig config;
    private final ObservableList<Account> accounts = FXCollections.observableArrayList();
    private final ObjectProperty<Account> activeAccount = new SimpleObjectProperty<>();
    private final ObjectProperty<AppUser> currentUser = new SimpleObjectProperty<>();
    private final StringProperty status = new SimpleStringProperty("Listo");
    private final StringProperty rateLimitText = new SimpleStringProperty("Cuota de la API: —");
    private final BooleanProperty securityEnabled = new SimpleBooleanProperty();
    private final RateLimitTracker rateLimits = new RateLimitTracker();
    private final MonitorService monitor;
    private final com.gitobserver.database.DatabaseService databaseService;
    private final Map<String, List<RepoRef>> repoCache = new LinkedHashMap<>();
    private final AtomicBoolean busy = new AtomicBoolean();
    private boolean loading = true;

    public AppContext(ConfigStore store, AppConfig config) {
        this.store = store;
        this.config = config;
        config.normalize();
        this.databaseService = new com.gitobserver.database.DatabaseService(config.getDatabase());
        this.databaseService.initAsync();
        this.monitor = new MonitorService(rateLimits, this, databaseService);
        accounts.setAll(config.getAccounts());
        activeAccount.set(find(config.getActiveAccountId()));
        securityEnabled.set(config.getSecurity() != null && config.getSecurity().isEnabled());
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
        if (repos != null) {
            return List.copyOf(repos);
        }
        List<RepoRef> fromDb = databaseService.getCachedRepositories();
        if (fromDb != null && !fromDb.isEmpty()) {
            repoCache.put(accountId, fromDb);
            return fromDb;
        }
        return null;
    }

    @Override
    public void cacheRepos(String accountId, List<RepoRef> repos) {
        repoCache.put(accountId, List.copyOf(repos));
        databaseService.saveRepositoriesAsync(repos);
    }

    public com.gitobserver.database.DatabaseService database() {
        return databaseService;
    }

    public void updateDatabaseConfig(com.gitobserver.database.DatabaseConfig newConfig) {
        config.setDatabase(newConfig);
        databaseService.reconfigure(newConfig);
        persist();
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

    public BooleanProperty securityEnabledProperty() {
        return securityEnabled;
    }

    public boolean isSecurityEnabled() {
        return securityEnabled.get();
    }

    public SecurityConfig security() {
        return config.getSecurity();
    }

    public ObjectProperty<AppUser> currentUserProperty() {
        return currentUser;
    }

    public AppUser currentUser() {
        return currentUser.get();
    }

    public UserRole currentRole() {
        AppUser user = currentUser.get();
        return user == null ? null : user.getRole();
    }

    public boolean hasPermission(Permission permission) {
        if (!isSecurityEnabled()) {
            return permission != Permission.MANAGE_USERS && permission != Permission.VIEW_AUDIT;
        }
        AppUser user = currentUser.get();
        if (user == null || !user.isEnabled()) {
            return false;
        }
        return user.getRole().allows(permission);
    }

    public boolean canAccessPage(String pageId) {
        if (pageId == null) {
            return false;
        }
        return switch (pageId) {
            case "home" -> hasPermission(Permission.VIEW_HOME);
            case "statistics" -> hasPermission(Permission.VIEW_STATISTICS);
            case "contributions" -> hasPermission(Permission.VIEW_CONTRIBUTIONS);
            case "activity" -> hasPermission(Permission.VIEW_ACTIVITY);
            case "follow-up" -> hasPermission(Permission.VIEW_FOLLOW_UP);
            case "settings" -> hasPermission(Permission.VIEW_SETTINGS);
            case "users" -> hasPermission(Permission.MANAGE_USERS);
            case "audit" -> hasPermission(Permission.VIEW_AUDIT);
            case "credits" -> true;
            default -> false;
        };
    }

    public List<AppUser> users() {
        return List.copyOf(security().getUsers());
    }

    public List<AccessLogEntry> accessLog() {
        return List.copyOf(security().getAccessLog());
    }

    public int idleTimeoutMinutes() {
        return security().getIdleTimeoutMinutes();
    }

    public void setIdleTimeoutMinutes(int minutes) {
        int value = Math.max(0, Math.min(SecurityConfig.MAX_IDLE_MINUTES, minutes));
        security().setIdleTimeoutMinutes(value);
        persist();
    }

    public boolean login(String username, String password) {
        if (!isSecurityEnabled()) {
            return true;
        }
        String user = username == null ? "" : username.strip();
        String display = user.isBlank() ? "(vacío)" : user;
        AppUser found = findUser(user);
        if (found == null) {
            recordAccess(display, false, "Intento fallido: usuario no encontrado");
            return false;
        }
        if (!found.isEnabled()) {
            recordAccess(found.getUsername(), false, "Intento fallido: usuario deshabilitado");
            return false;
        }
        if (!PasswordHasher.verify(password, found.getSalt(), found.getPasswordHash())) {
            recordAccess(found.getUsername(), false, "Intento fallido: contraseña incorrecta");
            return false;
        }
        currentUser.set(found);
        recordAccess(found.getUsername(), true, "Acceso correcto · " + found.getRole().getDisplayName());
        return true;
    }

    public void logout() {
        currentUser.set(null);
    }

    public boolean checkCredentials(String username, String password) {
        SecurityConfig sec = config.getSecurity();
        if (sec == null || !sec.isEnabled()) {
            return true;
        }
        AppUser found = findUser(username);
        if (found == null || !found.isEnabled()) {
            return false;
        }
        return PasswordHasher.verify(password, found.getSalt(), found.getPasswordHash());
    }

    public boolean verifyCurrentPassword(String password) {
        SecurityConfig sec = config.getSecurity();
        if (sec == null || !sec.isEnabled()) {
            return true;
        }
        AppUser user = currentUser.get();
        if (user == null) {
            List<AppUser> users = sec.getUsers();
            if (users.size() == 1) {
                user = users.get(0);
            } else if (sec.getPasswordHash() != null && sec.getSalt() != null) {
                return PasswordHasher.verify(password, sec.getSalt(), sec.getPasswordHash());
            } else {
                return false;
            }
        }
        return PasswordHasher.verify(password, user.getSalt(), user.getPasswordHash());
    }

    public void updateSecurity(boolean enabled, String username, String newPassword) {
        SecurityConfig sec = config.getSecurity();
        if (sec == null) {
            sec = new SecurityConfig();
            config.setSecurity(sec);
        }
        if (!enabled) {
            disableSecurity();
            return;
        }
        sec.setEnabled(true);
        String name = username == null ? "" : username.strip();
        AppUser user = findUser(name);
        if (user == null) {
            user = AppUser.create(name, null, null, UserRole.ADMINISTRADOR);
            sec.getUsers().add(user);
        } else {
            user.setUsername(name);
        }
        if (newPassword != null && !newPassword.isBlank()) {
            String salt = PasswordHasher.generateSalt();
            user.setSalt(salt);
            user.setPasswordHash(PasswordHasher.hash(newPassword, salt));
        }
        user.setEnabled(true);
        sec.normalize();
        securityEnabled.set(sec.isEnabled());
        persist();
    }

    public Optional<String> enableSecurity(String username, String password) {
        Optional<String> userError = PasswordPolicy.validateUsername(username);
        if (userError.isPresent()) {
            return userError;
        }
        Optional<String> passError = PasswordPolicy.validate(password);
        if (passError.isPresent()) {
            return passError;
        }
        updateSecurity(true, username.strip(), password);
        AppUser admin = findUser(username);
        if (admin != null) {
            currentUser.set(admin);
        }
        return Optional.empty();
    }

    public void disableSecurity() {
        SecurityConfig sec = config.getSecurity();
        if (sec != null) {
            sec.setEnabled(false);
            sec.getUsers().clear();
            sec.setUsername(null);
            sec.setPasswordHash(null);
            sec.setSalt(null);
        }
        currentUser.set(null);
        securityEnabled.set(false);
        persist();
    }

    public Optional<String> changeOwnPassword(String currentPassword, String newPassword) {
        if (!hasPermission(Permission.CHANGE_OWN_PASSWORD) && isSecurityEnabled()) {
            return Optional.of("No tienes permiso para cambiar la contraseña.");
        }
        AppUser user = currentUser.get();
        if (user == null) {
            return Optional.of("No hay una sesión activa.");
        }
        if (!PasswordHasher.verify(currentPassword, user.getSalt(), user.getPasswordHash())) {
            return Optional.of("La contraseña actual no es correcta.");
        }
        Optional<String> passError = PasswordPolicy.validate(newPassword);
        if (passError.isPresent()) {
            return passError;
        }
        String salt = PasswordHasher.generateSalt();
        user.setSalt(salt);
        user.setPasswordHash(PasswordHasher.hash(newPassword, salt));
        persist();
        return Optional.empty();
    }

    public Optional<String> createUser(String username, String password, UserRole role) {
        if (!hasPermission(Permission.MANAGE_USERS)) {
            return Optional.of("Solo el administrador puede crear usuarios.");
        }
        Optional<String> userError = PasswordPolicy.validateUsername(username);
        if (userError.isPresent()) {
            return userError;
        }
        Optional<String> passError = PasswordPolicy.validate(password);
        if (passError.isPresent()) {
            return passError;
        }
        if (findUser(username) != null) {
            return Optional.of("Ya existe un usuario con ese nombre.");
        }
        UserRole assigned = role == null ? UserRole.CONSULTOR : role;
        String salt = PasswordHasher.generateSalt();
        AppUser user = AppUser.create(username.strip(), PasswordHasher.hash(password, salt), salt, assigned);
        security().getUsers().add(user);
        persist();
        return Optional.empty();
    }

    public Optional<String> updateUser(String userId, String username, String newPassword, UserRole role, boolean enabled) {
        if (!hasPermission(Permission.MANAGE_USERS)) {
            return Optional.of("Solo el administrador puede editar usuarios.");
        }
        AppUser user = findUserById(userId);
        if (user == null) {
            return Optional.of("El usuario no existe.");
        }
        Optional<String> userError = PasswordPolicy.validateUsername(username);
        if (userError.isPresent()) {
            return userError;
        }
        AppUser duplicate = findUser(username);
        if (duplicate != null && !duplicate.getId().equals(userId)) {
            return Optional.of("Ya existe un usuario con ese nombre.");
        }
        UserRole assigned = role == null ? user.getRole() : role;
        if (user.getRole() == UserRole.ADMINISTRADOR && assigned != UserRole.ADMINISTRADOR && enabledAdmins() <= 1) {
            return Optional.of("Debe quedar al menos un administrador activo.");
        }
        if (user.getRole() == UserRole.ADMINISTRADOR && !enabled && enabledAdmins() <= 1) {
            return Optional.of("No se puede deshabilitar al único administrador.");
        }
        if (newPassword != null && !newPassword.isBlank()) {
            Optional<String> passError = PasswordPolicy.validate(newPassword);
            if (passError.isPresent()) {
                return passError;
            }
            String salt = PasswordHasher.generateSalt();
            user.setSalt(salt);
            user.setPasswordHash(PasswordHasher.hash(newPassword, salt));
        }
        user.setUsername(username.strip());
        user.setRole(assigned);
        user.setEnabled(enabled);
        AppUser session = currentUser.get();
        if (session != null && session.getId().equals(user.getId())) {
            currentUser.set(user);
        }
        persist();
        return Optional.empty();
    }

    public Optional<String> deleteUser(String userId) {
        if (!hasPermission(Permission.MANAGE_USERS)) {
            return Optional.of("Solo el administrador puede eliminar usuarios.");
        }
        AppUser user = findUserById(userId);
        if (user == null) {
            return Optional.of("El usuario no existe.");
        }
        AppUser session = currentUser.get();
        if (session != null && session.getId().equals(userId)) {
            return Optional.of("No puedes eliminar tu propio usuario.");
        }
        if (user.getRole() == UserRole.ADMINISTRADOR && enabledAdmins() <= 1) {
            return Optional.of("No se puede eliminar al único administrador.");
        }
        security().getUsers().removeIf(candidate -> userId.equals(candidate.getId()));
        persist();
        return Optional.empty();
    }

    private int enabledAdmins() {
        int count = 0;
        for (AppUser user : security().getUsers()) {
            if (user.isEnabled() && user.getRole() == UserRole.ADMINISTRADOR) {
                count++;
            }
        }
        return count;
    }

    private AppUser findUser(String username) {
        if (username == null) {
            return null;
        }
        String needle = username.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return null;
        }
        for (AppUser user : security().getUsers()) {
            if (user.getUsername() != null && user.getUsername().strip().toLowerCase(Locale.ROOT).equals(needle)) {
                return user;
            }
        }
        return null;
    }

    private AppUser findUserById(String id) {
        if (id == null) {
            return null;
        }
        for (AppUser user : security().getUsers()) {
            if (id.equals(user.getId())) {
                return user;
            }
        }
        return null;
    }

    private void recordAccess(String username, boolean success, String detail) {
        SecurityConfig sec = security();
        List<AccessLogEntry> log = sec.getAccessLog();
        log.add(0, new AccessLogEntry(username, success, detail));
        while (log.size() > SecurityConfig.MAX_ACCESS_LOG) {
            log.remove(log.size() - 1);
        }
        persist();
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
