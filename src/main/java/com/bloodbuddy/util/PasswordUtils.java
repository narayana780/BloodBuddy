package com.bloodbuddy.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.HexFormat;

/**
 * PasswordUtils provides enterprise-grade PBKDF2 password hashing and salt generation
 * using standard Java standard library cryptography (zero external dependencies).
 */
public final class PasswordUtils {

    private static final int ITERATIONS = 10000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtils() {
        // Utility class
    }

    /**
     * Generates a cryptographically strong random salt.
     */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    /**
     * Hashes a password using PBKDF2WithHmacSHA256.
     */
    public static String hashPassword(String password, String saltHex) {
        if (password == null || saltHex == null) {
            throw new IllegalArgumentException("Password and salt must not be null");
        }
        try {
            byte[] salt = HexFormat.of().parseHex(saltHex);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            // Fallback to SHA-256 digest if PBKDF2 is unavailable
            return fallbackSha256(password, saltHex);
        }
    }

    /**
     * Constant-time verification of a plaintext password against a stored salt and hash.
     */
    public static boolean verifyPassword(String password, String saltHex, String expectedHashHex) {
        if (password == null || saltHex == null || expectedHashHex == null) {
            return false;
        }
        String computedHashHex = hashPassword(password, saltHex);
        byte[] a = HexFormat.of().parseHex(computedHashHex);
        byte[] b = HexFormat.of().parseHex(expectedHashHex);
        return MessageDigest.isEqual(a, b);
    }

    private static String fallbackSha256(String password, String saltHex) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(HexFormat.of().parseHex(saltHex));
            byte[] digest = md.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
