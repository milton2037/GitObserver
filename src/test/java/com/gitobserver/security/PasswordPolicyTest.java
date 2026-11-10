package com.gitobserver.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PasswordPolicyTest {

    @Test
    void rejectsShortPasswordsWithoutComplexity() {
        assertTrue(PasswordPolicy.validate(null).isPresent());
        assertTrue(PasswordPolicy.validate("").isPresent());
        assertTrue(PasswordPolicy.validate("Ab1!").isPresent());
        assertTrue(PasswordPolicy.validate("abcdefgh").isPresent());
        assertTrue(PasswordPolicy.validate("abcd1234").isPresent());
        assertTrue(PasswordPolicy.validate("abcd!!!!").isPresent());
        assertTrue(PasswordPolicy.validate("1234!!!!").isPresent());
    }

    @Test
    void acceptsPasswordWithLetterNumberAndSymbol() {
        assertTrue(PasswordPolicy.validate("Clave#2024").isEmpty());
        assertTrue(PasswordPolicy.validate("Admin123!").isEmpty());
    }

    @Test
    void validatesUsername() {
        assertTrue(PasswordPolicy.validateUsername("ab").isPresent());
        assertTrue(PasswordPolicy.validateUsername("ana maria").isPresent());
        assertTrue(PasswordPolicy.validateUsername("analista").isEmpty());
        assertEquals("Escribe un nombre de usuario.", PasswordPolicy.validateUsername("  ").orElse(""));
    }
}
