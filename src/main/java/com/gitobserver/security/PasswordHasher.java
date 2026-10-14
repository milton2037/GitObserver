package com.gitobserver.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.HexFormat;

/**
 * Utilidad criptográfica para derivación y verificación segura de contraseñas
 * mediante PBKDF2 con HmacSHA256 y sales aleatorias de 128 bits.
 */
public final class PasswordHasher {
    private static final int ITERATIONS = 65_536;
    private static final int KEY_LENGTH = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String generateSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    public static String hash(String password, String saltHex) {
        if (password == null || saltHex == null) {
            throw new IllegalArgumentException("Password y sal no pueden ser nulos");
        }
        try {
            byte[] salt = HexFormat.of().parseHex(saltHex);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Error al generar el hash seguro de la contraseña", e);
        }
    }

    public static boolean verify(String password, String saltHex, String expectedHashHex) {
        if (password == null || saltHex == null || expectedHashHex == null) {
            return false;
        }
        try {
            String actualHashHex = hash(password, saltHex);
            byte[] actual = HexFormat.of().parseHex(actualHashHex);
            byte[] expected = HexFormat.of().parseHex(expectedHashHex);
            return MessageDigest.isEqual(actual, expected);
        } catch (Exception e) {
            return false;
        }
    }
}
