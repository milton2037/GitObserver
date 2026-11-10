package com.gitobserver;

import com.gitobserver.config.AppConfig;
import com.gitobserver.config.ConfigStore;
import com.gitobserver.security.Permission;
import com.gitobserver.security.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppContextAccessControlTest {

    @TempDir
    Path tempFolder;

    @Test
    void recordsSuccessfulAndFailedLogins() {
        AppContext context = newContext();
        context.updateSecurity(true, "admin", "Admin123!");

        assertFalse(context.login("admin", "clave-mala"));
        assertTrue(context.login("admin", "Admin123!"));
        assertEquals("admin", context.currentUser().getUsername());

        assertEquals(2, context.accessLog().size());
        assertTrue(context.accessLog().get(0).isSuccess());
        assertFalse(context.accessLog().get(1).isSuccess());
        assertEquals("admin", context.accessLog().get(0).getUsername());
        assertTrue(context.accessLog().get(0).getTimestamp() != null && !context.accessLog().get(0).getTimestamp().isBlank());
    }

    @Test
    void administratorCreatesUsersWithRolesAndPermissions() {
        AppContext context = newContext();
        context.updateSecurity(true, "admin", "Admin123!");
        context.login("admin", "Admin123!");

        assertTrue(context.createUser("ana", "AnaPass1!", UserRole.ANALISTA).isEmpty());
        assertTrue(context.createUser("carlos", "Carlos#1", UserRole.CONSULTOR).isEmpty());
        assertTrue(context.createUser("ana", "AnaPass1!", UserRole.ANALISTA).isPresent());

        context.logout();
        assertTrue(context.login("ana", "AnaPass1!"));
        assertTrue(context.canAccessPage("follow-up"));
        assertFalse(context.canAccessPage("users"));
        assertFalse(context.canAccessPage("audit"));
        assertFalse(context.canAccessPage("settings"));
        assertTrue(context.hasPermission(Permission.CHANGE_OWN_PASSWORD));
        assertTrue(context.changeOwnPassword("AnaPass1!", "AnaPass2!").isEmpty());

        context.logout();
        assertTrue(context.login("carlos", "Carlos#1"));
        assertTrue(context.canAccessPage("home"));
        assertTrue(context.canAccessPage("statistics"));
        assertFalse(context.canAccessPage("follow-up"));
        assertFalse(context.canAccessPage("users"));
        assertTrue(context.createUser("otro", "Otro123!", UserRole.CONSULTOR).isPresent());
    }

    @Test
    void persistsIdleTimeoutAndUsers() {
        AppContext context = newContext();
        context.updateSecurity(true, "admin", "Admin123!");
        context.login("admin", "Admin123!");
        context.setIdleTimeoutMinutes(20);
        context.createUser("ana", "AnaPass1!", UserRole.ANALISTA);

        ConfigStore store = new ConfigStore(tempFolder.resolve("accounts.json"));
        AppContext reloaded = new AppContext(store, store.load().config());
        assertEquals(20, reloaded.idleTimeoutMinutes());
        assertEquals(2, reloaded.users().size());
        assertTrue(reloaded.login("ana", "AnaPass1!"));
        assertEquals(UserRole.ANALISTA, reloaded.currentRole());
    }

    @Test
    void hidesUserAdminModulesWhenSecurityIsDisabled() {
        AppContext context = newContext();
        assertTrue(context.canAccessPage("settings"));
        assertFalse(context.canAccessPage("users"));
        assertFalse(context.canAccessPage("audit"));
    }

    private AppContext newContext() {
        ConfigStore store = new ConfigStore(tempFolder.resolve("accounts.json"));
        return new AppContext(store, new AppConfig());
    }
}
