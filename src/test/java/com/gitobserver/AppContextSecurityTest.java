package com.gitobserver;

import com.gitobserver.config.AppConfig;
import com.gitobserver.config.ConfigStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppContextSecurityTest {

    @TempDir
    Path tempFolder;

    @Test
    void securityIsDisabledByDefault() {
        ConfigStore store = new ConfigStore(tempFolder.resolve("accounts.json"));
        AppContext context = new AppContext(store, new AppConfig());

        assertFalse(context.isSecurityEnabled());
        assertFalse(context.securityEnabledProperty().get());
        assertTrue(context.checkCredentials("cualquiera", "cualquiera"));
    }

    @Test
    void updatesAndVerifiesSecurityCredentials() {
        ConfigStore store = new ConfigStore(tempFolder.resolve("accounts.json"));
        AppContext context = new AppContext(store, new AppConfig());

        context.updateSecurity(true, "Milton", "MiPasswordSeguro123");

        assertTrue(context.isSecurityEnabled());
        assertTrue(context.securityEnabledProperty().get());
        assertEquals("Milton", context.security().getUsername());

        // Credenciales correctas (usuario insensible a mayúsculas/minúsculas)
        assertTrue(context.checkCredentials("Milton", "MiPasswordSeguro123"));
        assertTrue(context.checkCredentials("milton", "MiPasswordSeguro123"));
        assertTrue(context.checkCredentials("  MILTON  ", "MiPasswordSeguro123"));

        // Verificación de contraseña actual
        assertTrue(context.verifyCurrentPassword("MiPasswordSeguro123"));
        assertFalse(context.verifyCurrentPassword("ClaveEquivocada"));

        // Contraseña incorrecta
        assertFalse(context.checkCredentials("milton", "PasswordErroneo"));

        // Usuario incorrecto
        assertFalse(context.checkCredentials("otro_usuario", "MiPasswordSeguro123"));

        // Prueba de persistencia recargando del archivo
        ConfigStore.LoadResult reloaded = store.load();
        AppContext contextReloaded = new AppContext(store, reloaded.config());
        assertTrue(contextReloaded.isSecurityEnabled());
        assertTrue(contextReloaded.checkCredentials("milton", "MiPasswordSeguro123"));
    }

    @Test
    void disablesSecurityProperly() {
        ConfigStore store = new ConfigStore(tempFolder.resolve("accounts.json"));
        AppContext context = new AppContext(store, new AppConfig());

        context.updateSecurity(true, "admin", "clave1234");
        assertTrue(context.isSecurityEnabled());

        context.disableSecurity();
        assertFalse(context.isSecurityEnabled());
        assertFalse(context.securityEnabledProperty().get());
        assertNull(context.security().getUsername());
        assertNull(context.security().getPasswordHash());
        assertNull(context.security().getSalt());

        // Al estar desactivado, checkCredentials permite acceso
        assertTrue(context.checkCredentials("admin", "clave1234"));
        assertTrue(context.checkCredentials("nadie", ""));
    }
}
