package com.gitobserver.config;

import com.gitobserver.model.Account;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigStoreTest {
    @TempDir
    Path folder;

    @Test
    void roundTripsAccountsWithoutLosingTheToken() {
        ConfigStore store = new ConfigStore(folder.resolve("accounts.json"));
        Account account = new Account();
        account.setId("cuenta-1");
        account.setAlias("Cuenta principal");
        account.setToken("github_pat_ejemplo");
        account.setLogin("octocat");
        AppConfig config = new AppConfig();
        config.getAccounts().add(account);
        config.setActiveAccountId("cuenta-1");

        store.save(config);
        ConfigStore.LoadResult loaded = store.load();

        assertEquals(1, loaded.config().getAccounts().size());
        Account stored = loaded.config().getAccounts().get(0);
        assertEquals("Cuenta principal", stored.getAlias());
        assertEquals("github_pat_ejemplo", stored.getToken());
        assertEquals("octocat", stored.getLogin());
        assertEquals("cuenta-1", loaded.config().getActiveAccountId());
    }

    @Test
    void assignsAnIdWhenTheFileDoesNotHaveOne() throws Exception {
        Path file = folder.resolve("accounts.json");
        Files.writeString(file, """
                {"accounts":[{"alias":"Personal","token":"ghp_1234567890"}]}
                """, StandardCharsets.UTF_8);

        ConfigStore.LoadResult loaded = new ConfigStore(file).load();

        Account account = loaded.config().getAccounts().get(0);
        assertNotNull(account.getId());
        assertFalseBlank(account.getId());
        assertEquals(account.getId(), loaded.config().getActiveAccountId());
    }

    @Test
    void startsEmptyWhenTheFileIsCorrupt() throws Exception {
        Path file = folder.resolve("accounts.json");
        Files.writeString(file, "esto no es json", StandardCharsets.UTF_8);

        ConfigStore.LoadResult loaded = new ConfigStore(file).load();

        assertNotNull(loaded.warning());
        assertTrue(loaded.config().getAccounts().isEmpty());
        assertTrue(Files.exists(folder.resolve("accounts.json.bak")));
    }

    @Test
    void roundTripsSecurityConfig() {
        ConfigStore store = new ConfigStore(folder.resolve("accounts.json"));
        AppConfig config = new AppConfig();
        SecurityConfig sec = new SecurityConfig(true, "admin", "fakehash123", "fakesalt456");
        config.setSecurity(sec);

        store.save(config);
        ConfigStore.LoadResult loaded = store.load();

        SecurityConfig loadedSec = loaded.config().getSecurity();
        assertNotNull(loadedSec);
        assertTrue(loadedSec.isEnabled());
        assertEquals("admin", loadedSec.getUsername());
        assertEquals("fakehash123", loadedSec.getPasswordHash());
        assertEquals("fakesalt456", loadedSec.getSalt());
    }

    @Test
    void defaultsSecurityToDisabledWhenMissingInJson() throws Exception {
        Path file = folder.resolve("accounts.json");
        Files.writeString(file, """
                {"accounts":[{"alias":"Personal","token":"ghp_1234567890"}]}
                """, StandardCharsets.UTF_8);

        ConfigStore.LoadResult loaded = new ConfigStore(file).load();

        SecurityConfig sec = loaded.config().getSecurity();
        assertNotNull(sec);
        org.junit.jupiter.api.Assertions.assertFalse(sec.isEnabled());
    }

    @Test
    void normalizesInvalidSecurityConfig() {
        AppConfig config = new AppConfig();
        SecurityConfig sec = new SecurityConfig(true, "   ", "hash", "salt");
        config.setSecurity(sec);
        config.normalize();

        org.junit.jupiter.api.Assertions.assertFalse(config.getSecurity().isEnabled());
    }

    private static void assertFalseBlank(String value) {
        assertTrue(value != null && !value.isBlank());
    }
}
