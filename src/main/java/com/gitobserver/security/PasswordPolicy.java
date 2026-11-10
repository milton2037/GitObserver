package com.gitobserver.security;

import java.util.Optional;

/**
 * Reglas de contraseña segura para usuarios locales de GitObserver.
 */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 8;
    public static final int MIN_USERNAME_LENGTH = 3;

    private PasswordPolicy() {
    }

    public static String rulesDescription() {
        return "Mínimo " + MIN_LENGTH + " caracteres, con al menos una letra, un número y un símbolo especial.";
    }

    public static Optional<String> validateUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.of("Escribe un nombre de usuario.");
        }
        String value = username.strip();
        if (value.length() < MIN_USERNAME_LENGTH) {
            return Optional.of("El nombre de usuario debe tener al menos " + MIN_USERNAME_LENGTH + " caracteres.");
        }
        if (value.contains(" ")) {
            return Optional.of("El nombre de usuario no puede contener espacios.");
        }
        return Optional.empty();
    }

    public static Optional<String> validate(String password) {
        if (password == null || password.isBlank()) {
            return Optional.of("Escribe una contraseña.");
        }
        if (password.length() < MIN_LENGTH) {
            return Optional.of("La contraseña debe tener al menos " + MIN_LENGTH + " caracteres.");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        boolean hasSymbol = false;
        for (int index = 0; index < password.length(); index++) {
            char character = password.charAt(index);
            if (Character.isLetter(character)) {
                hasLetter = true;
            } else if (Character.isDigit(character)) {
                hasDigit = true;
            } else {
                hasSymbol = true;
            }
        }
        if (!hasLetter) {
            return Optional.of("La contraseña debe incluir al menos una letra.");
        }
        if (!hasDigit) {
            return Optional.of("La contraseña debe incluir al menos un número.");
        }
        if (!hasSymbol) {
            return Optional.of("La contraseña debe incluir al menos un símbolo especial.");
        }
        return Optional.empty();
    }
}
