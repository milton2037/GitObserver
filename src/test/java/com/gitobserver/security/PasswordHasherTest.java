package com.gitobserver.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    @Test
    void generatesValidHexSalt() {
        String salt1 = PasswordHasher.generateSalt();
        String salt2 = PasswordHasher.generateSalt();

        assertNotNull(salt1);
        assertNotNull(salt2);
        assertEquals(32, salt1.length());
        assertEquals(32, salt2.length());
        assertNotEquals(salt1, salt2);
    }

    @Test
    void hashesDeterministicallyWithSameSalt() {
        String salt = PasswordHasher.generateSalt();
        String hash1 = PasswordHasher.hash("MiContraseña123", salt);
        String hash2 = PasswordHasher.hash("MiContraseña123", salt);

        assertNotNull(hash1);
        assertEquals(hash1, hash2);
    }

    @Test
    void producesDifferentHashesForDifferentSalts() {
        String salt1 = PasswordHasher.generateSalt();
        String salt2 = PasswordHasher.generateSalt();

        String hash1 = PasswordHasher.hash("MiContraseña123", salt1);
        String hash2 = PasswordHasher.hash("MiContraseña123", salt2);

        assertNotEquals(hash1, hash2);
    }

    @Test
    void verifiesPasswordSuccessfully() {
        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hash("SuperClaveSegura!", salt);

        assertTrue(PasswordHasher.verify("SuperClaveSegura!", salt, hash));
        assertFalse(PasswordHasher.verify("OtraClaveErronea", salt, hash));
        assertFalse(PasswordHasher.verify("superclavesegura!", salt, hash)); // sensible a mayúsculas
    }

    @Test
    void handlesNullGracefully() {
        assertFalse(PasswordHasher.verify(null, "salt", "hash"));
        assertFalse(PasswordHasher.verify("pass", null, "hash"));
        assertFalse(PasswordHasher.verify("pass", "salt", null));
    }
}
