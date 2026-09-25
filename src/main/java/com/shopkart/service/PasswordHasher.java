package com.shopkart.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Salted PBKDF2-HMAC-SHA256 password hashing. Stored format:
 * {@code pbkdf2$<iterations>$<base64 salt>$<base64 hash>}.
 */
public final class PasswordHasher {

    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() { }

    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] dk = derive(password.toCharArray(), salt, ITERATIONS);
        Base64.Encoder b64 = Base64.getEncoder();
        return "pbkdf2$" + ITERATIONS + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(dk);
    }

    public static boolean verify(String password, String stored) {
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
            return false;
        }
        int iterations = Integer.parseInt(parts[1]);
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] expected = Base64.getDecoder().decode(parts[3]);
        byte[] actual = derive(password.toCharArray(), salt, iterations);
        return MessageDigest.isEqual(expected, actual); // constant-time comparison
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        }
    }
}
