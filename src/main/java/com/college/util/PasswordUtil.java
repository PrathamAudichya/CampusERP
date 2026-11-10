package com.college.util;

import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;

/**
 * Utility class for BCrypt password hashing and verification,
 * and cryptographically secure temporary password generation.
 * Uses jBCrypt library with a work factor of 12 for secure password storage.
 */
public final class PasswordUtil {

    /** BCrypt work factor (log2 of iteration count). 12 = 4096 iterations. */
    private static final int BCRYPT_WORK_FACTOR = 12;

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIAL = "!@#$%&*-_=+";
    private static final String ALL_CHARS = UPPER + LOWER + DIGITS + SPECIAL;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordUtil() {
        // Utility class — prevent instantiation
    }

    /**
     * Generates a cryptographically secure random temporary password meeting
     * enterprise complexity requirements (12 characters, including uppercase,
     * lowercase, digits, and special characters, excluding ambiguous characters).
     *
     * @return cryptographically strong 12-character temporary password
     */
    public static String generateTemporaryPassword() {
        char[] password = new char[12];
        // Ensure at least 2 of each required category
        password[0] = UPPER.charAt(SECURE_RANDOM.nextInt(UPPER.length()));
        password[1] = UPPER.charAt(SECURE_RANDOM.nextInt(UPPER.length()));
        password[2] = LOWER.charAt(SECURE_RANDOM.nextInt(LOWER.length()));
        password[3] = LOWER.charAt(SECURE_RANDOM.nextInt(LOWER.length()));
        password[4] = DIGITS.charAt(SECURE_RANDOM.nextInt(DIGITS.length()));
        password[5] = DIGITS.charAt(SECURE_RANDOM.nextInt(DIGITS.length()));
        password[6] = SPECIAL.charAt(SECURE_RANDOM.nextInt(SPECIAL.length()));
        password[7] = SPECIAL.charAt(SECURE_RANDOM.nextInt(SPECIAL.length()));

        // Remaining 4 characters from all character pools
        for (int i = 8; i < 12; i++) {
            password[i] = ALL_CHARS.charAt(SECURE_RANDOM.nextInt(ALL_CHARS.length()));
        }

        // Fisher-Yates shuffle
        for (int i = password.length - 1; i > 0; i--) {
            int j = SECURE_RANDOM.nextInt(i + 1);
            char temp = password[i];
            password[i] = password[j];
            password[j] = temp;
        }

        String result = new String(password);
        ValidationUtil.validateStrongPassword(result);
        return result;
    }

    /**
     * Hashes a plaintext password using BCrypt with the configured work factor.
     *
     * @param plainPassword the plaintext password to hash
     * @return the BCrypt hash string (includes salt and algorithm identifier)
     * @throws IllegalArgumentException if plainPassword is null or empty
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty.");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_WORK_FACTOR));
    }

    /**
     * Verifies a plaintext password against a stored BCrypt hash.
     *
     * @param plainPassword the plaintext password to verify
     * @param hashedPassword the stored BCrypt hash to check against
     * @return true if the password matches the hash, false otherwise
     * @throws IllegalArgumentException if either argument is null or empty
     */
    public static boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty.");
        }
        if (hashedPassword == null || hashedPassword.isEmpty()) {
            throw new IllegalArgumentException("Hashed password cannot be null or empty.");
        }
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (IllegalArgumentException e) {
            // Corrupted or malformed salt/hash format fails verification safely
            return false;
        }
    }
}

