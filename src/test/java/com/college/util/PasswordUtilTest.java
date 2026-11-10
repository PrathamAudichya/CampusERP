package com.college.util;

import com.college.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [T25] BCrypt password hashing & verification tests for {@link PasswordUtil}.
 */
public class PasswordUtilTest {

    @Test
    @DisplayName("Verify BCrypt hash generation and format")
    void testHashPasswordFormat() {
        String hash = PasswordUtil.hashPassword("mySecretPassword123");
        assertNotNull(hash);
        assertTrue(hash.startsWith("$2a$"), "BCrypt hash must start with $2a$ prefix");
        assertEquals(60, hash.length(), "Standard BCrypt hash length is 60 characters");
    }

    @Test
    @DisplayName("Verify verification matches correct password and rejects wrong password")
    void testVerifyPassword() {
        String raw = "StrongP@ssw0rd!";
        String hash = PasswordUtil.hashPassword(raw);

        assertTrue(PasswordUtil.verifyPassword(raw, hash), "Correct password must verify to true");
        assertFalse(PasswordUtil.verifyPassword("WrongPassword", hash), "Wrong password must verify to false");
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.verifyPassword("", hash));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.verifyPassword(null, hash));
    }

    @Test
    @DisplayName("Verify BCrypt salting produces distinct hashes for the same plaintext password")
    void testSaltUniqueness() {
        String pass = "IdenticalPlaintext";
        String hash1 = PasswordUtil.hashPassword(pass);
        String hash2 = PasswordUtil.hashPassword(pass);

        assertNotEquals(hash1, hash2, "BCrypt must generate a unique cryptographic salt per invocation");
        assertTrue(PasswordUtil.verifyPassword(pass, hash1));
        assertTrue(PasswordUtil.verifyPassword(pass, hash2));
    }

    @Test
    @DisplayName("Verify hashing null or empty password throws ValidationException or IllegalArgumentException")
    void testHashNullOrEmptyRejection() {
        assertThrows(Exception.class, () -> PasswordUtil.hashPassword(null));
        assertThrows(Exception.class, () -> PasswordUtil.hashPassword(""));
    }
}
