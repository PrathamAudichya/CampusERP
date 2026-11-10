package com.college.config;

import com.college.App;
import com.college.TestDBHelper;
import com.college.dao.UserDAO;
import com.college.exception.ValidationException;
import com.college.model.Role;
import com.college.model.User;
import com.college.service.AuthService;
import com.college.util.PasswordUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression and security verification tests for initial administrator provisioning,
 * CLI argument safety, and credential leakage prevention.
 */
public class AdminCliProvisioningTest {

    private static AuthService authService;
    private static UserDAO userDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        authService = new AuthService();
        userDAO = new UserDAO();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    @Test
    @DisplayName("Verify CLI arguments cannot supply password and strictly reject inline secrets")
    void testCliPasswordArgumentStrictlyRejected() {
        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(errCapture));
            String secretPass = "TopSecret@Pass2026";
            String[] cliArgs = new String[]{"--setup-admin", "admin", secretPass};

            int exitCode = App.handleSetupAdminCli(cliArgs, null);

            assertEquals(1, exitCode, "Must return non-zero exit code when password is provided as argument");
            String errOut = errCapture.toString();
            assertTrue(errOut.contains("Supplying passwords via command-line arguments is strictly prohibited"),
                    "Error must explicitly explain prohibition");
            assertFalse(errOut.contains(secretPass),
                    "Secret password must never appear in application error output");
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    @DisplayName("Verify CLI rejects missing username argument")
    void testCliMissingUsernameRejected() {
        int exitCode = App.handleSetupAdminCli(new String[]{"--setup-admin"}, null);
        assertEquals(1, exitCode);

        int exitCodeBlank = App.handleSetupAdminCli(new String[]{"--setup-admin", "   "}, null);
        assertEquals(1, exitCodeBlank);
    }

    @Test
    @DisplayName("Verify CLI fails safely when Console is unavailable and directs to graphical setup")
    void testCliNullConsoleFailsSafely() {
        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(errCapture));
            int exitCode = App.handleSetupAdminCli(new String[]{"--setup-admin", "admin"}, null);

            assertEquals(1, exitCode);
            String errOut = errCapture.toString();
            assertTrue(errOut.contains("Secure interactive console is unavailable"));
            assertTrue(errOut.contains("graphical first-run administrator setup"));
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    @DisplayName("Verify initial Admin provisioning succeeds and sets correct state")
    void testFirstAdminProvisioningSucceeds() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            assertFalse(authService.hasAdminUser(), "No admin should exist initially");

            User admin = authService.provisionInitialAdmin("rootadmin", "Root@Admin2026!");
            assertNotNull(admin);
            assertEquals("rootadmin", admin.getUsername());
            assertEquals(Role.ADMIN, admin.getRole());
            assertFalse(admin.isMustChangePassword(), "Initial provisioned admin should not be forced to change password");
            assertTrue(admin.isActive());

            // Verify in database
            User persisted = userDAO.findById(admin.getId());
            assertNotNull(persisted);
            assertTrue(PasswordUtil.verifyPassword("Root@Admin2026!", persisted.getPasswordHash()));

            assertTrue(authService.hasAdminUser(), "Admin presence check must now return true");
        }
    }

    @Test
    @DisplayName("Verify weak passwords are systematically rejected by complexity policy")
    void testWeakPasswordsRejected() {
        assertThrows(ValidationException.class, () -> authService.provisionInitialAdmin("admin", "short"));
        assertThrows(ValidationException.class, () -> authService.provisionInitialAdmin("admin", "alllowercase123!"));
        assertThrows(ValidationException.class, () -> authService.provisionInitialAdmin("admin", "ALLUPPERCASE123!"));
        assertThrows(ValidationException.class, () -> authService.provisionInitialAdmin("admin", "NoNumbersSpecial!"));
        assertThrows(ValidationException.class, () -> authService.provisionInitialAdmin("admin", "NoSpecialCharacters123"));
    }

    @Test
    @DisplayName("Verify duplicate administrator provisioning is strictly blocked")
    void testDuplicateProvisioningRejected() {
        authService.provisionInitialAdmin("primaryadmin", "Primary@Admin2026!");
        assertTrue(authService.hasAdminUser());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> authService.provisionInitialAdmin("secondaryadmin", "Second@Admin2026!"));
        assertTrue(ex.getMessage().contains("already provisioned"));
    }

    @Test
    @DisplayName("Verify passwords never appear in standard output or standard error streams")
    void testPasswordsNeverAppearInApplicationOutput() {
        ByteArrayOutputStream outCapture = new ByteArrayOutputStream();
        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;

        String secretPassword = "SuperSecretPassword@2026";
        try {
            System.setOut(new PrintStream(outCapture));
            System.setErr(new PrintStream(errCapture));

            // Provisioning
            User admin = authService.provisionInitialAdmin("safeadmin", secretPassword);
            assertNotNull(admin);

            // Authentication
            User loggedIn = authService.login("safeadmin", secretPassword);
            assertNotNull(loggedIn);

            // Failed authentication attempt with another password
            String wrongPass = "WrongPassword@2026";
            try {
                authService.login("safeadmin", wrongPass);
            } catch (Exception ignored) {
            }

            String capturedOut = outCapture.toString();
            String capturedErr = errCapture.toString();

            assertFalse(capturedOut.contains(secretPassword), "Secret password must not appear in stdout");
            assertFalse(capturedErr.contains(secretPassword), "Secret password must not appear in stderr");
            assertFalse(capturedOut.contains(wrongPass), "Attempted password must not appear in stdout");
            assertFalse(capturedErr.contains(wrongPass), "Attempted password must not appear in stderr");
        } finally {
            System.setOut(origOut);
            System.setErr(origErr);
        }
    }
}

