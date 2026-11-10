package com.gitobserver.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserRoleTest {

    @Test
    void administratorHasEveryPermission() {
        for (Permission permission : Permission.values()) {
            assertTrue(UserRole.ADMINISTRADOR.allows(permission));
        }
    }

    @Test
    void analystOperatesMonitorButNotAdminModules() {
        assertTrue(UserRole.ANALISTA.allows(Permission.VIEW_FOLLOW_UP));
        assertTrue(UserRole.ANALISTA.allows(Permission.VIEW_STATISTICS));
        assertTrue(UserRole.ANALISTA.allows(Permission.CHANGE_OWN_PASSWORD));
        assertFalse(UserRole.ANALISTA.allows(Permission.MANAGE_USERS));
        assertFalse(UserRole.ANALISTA.allows(Permission.VIEW_AUDIT));
        assertFalse(UserRole.ANALISTA.allows(Permission.VIEW_SETTINGS));
        assertFalse(UserRole.ANALISTA.allows(Permission.CONFIGURE_SECURITY));
    }

    @Test
    void consultantIsReadOnlyOnDashboards() {
        assertTrue(UserRole.CONSULTOR.allows(Permission.VIEW_HOME));
        assertTrue(UserRole.CONSULTOR.allows(Permission.VIEW_ACTIVITY));
        assertFalse(UserRole.CONSULTOR.allows(Permission.VIEW_FOLLOW_UP));
        assertFalse(UserRole.CONSULTOR.allows(Permission.VIEW_SETTINGS));
        assertFalse(UserRole.CONSULTOR.allows(Permission.MANAGE_USERS));
        assertFalse(UserRole.CONSULTOR.allows(Permission.VIEW_AUDIT));
    }
}
