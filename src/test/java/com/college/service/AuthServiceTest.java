package com.college.service;

import com.college.TestDBHelper;
import com.college.config.DBConnection;
import com.college.config.DatabaseInitializer;
import com.college.dao.UserDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.ValidationException;
import com.college.model.Role;
import com.college.model.User;
import com.college.util.PasswordUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [T14] Authentication, authorization, and mandatory password change tests for {@link AuthService}.
 */
public class AuthServiceTest {

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

    private User createTestUser(Connection conn, String username, String rawPassword, Role role, boolean mustChange) {
        String hash = PasswordUtil.hashPassword(rawPassword);
        User u = new User(username, hash, role);
        u.setMustChangePassword(mustChange);
        u.setActive(true);
        return userDAO.insert(conn, u);
    }

    @Test
    @DisplayName("Verify valid credentials authenticate successfully")
    void testAuthenticateSuccess() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            createTestUser(conn, "faculty1", "secret123", Role.FACULTY, false);

            User authenticated = authService.login("faculty1", "secret123");
            assertNotNull(authenticated);
            assertEquals("faculty1", authenticated.getUsername());
            assertEquals(Role.FACULTY, authenticated.getRole());
        }
    }

    @Test
    @DisplayName("Verify invalid password or username throws AuthenticationException")
    void testAuthenticateInvalidCredentials() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            createTestUser(conn, "student1", "secret123", Role.STUDENT, false);

            // Wrong password
            assertThrows(AuthenticationException.class, () -> authService.login("student1", "wrongpass"));

            // Non-existent user
            assertThrows(AuthenticationException.class, () -> authService.login("nobody", "secret123"));
        }
    }

    @Test
    @DisplayName("Verify inactive user authentication is rejected")
    void testAuthenticateInactiveUser() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User u = createTestUser(conn, "inactive1", "pass123", Role.STUDENT, false);
            userDAO.setActive(conn, u.getId(), false);

            assertThrows(AuthenticationException.class, () -> authService.login("inactive1", "pass123"));
        }
    }

    @Test
    @DisplayName("Verify requireRole enforces role-based access control")
    void testRequireRoleEnforcement() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createTestUser(conn, "admin1", "pass", Role.ADMIN, false);
            User student = createTestUser(conn, "stu1", "pass", Role.STUDENT, false);

            // Matching role succeeds
            assertDoesNotThrow(() -> authService.requireRole(admin, Role.ADMIN));
            assertDoesNotThrow(() -> authService.requireRole(student, Role.STUDENT, Role.ADMIN));

            // Mismatched role fails
            assertThrows(AuthenticationException.class, () -> authService.requireRole(student, Role.ADMIN));
            assertThrows(AuthenticationException.class, () -> authService.requireRole(student, Role.FACULTY));
        }
    }

    @Test
    @DisplayName("Verify requireRole blocks users with must_change_password active (SEC-08)")
    void testRequireRoleBlocksForcedPasswordChange() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User forcedUser = createTestUser(conn, "newadmin", "temp123", Role.ADMIN, true);

            // Attempting any service action requiring role must be blocked until password is changed
            assertThrows(AuthenticationException.class, () -> authService.requireRole(forcedUser, Role.ADMIN));
        }
    }

    @Test
    @DisplayName("Verify changePassword flow: updates hash, clears flag, rejects invalid old or same password")
    void testChangePasswordFlow() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User u = createTestUser(conn, "user_chg", "initial123", Role.STUDENT, true);

            // Wrong current password rejected
            assertThrows(ValidationException.class, () -> authService.changePassword(u, "wrong", "NewPass@123"));

            // Same password rejected
            assertThrows(ValidationException.class, () -> authService.changePassword(u, "initial123", "initial123"));

            // Successful password change with strong password policy compliance
            authService.changePassword(u, "initial123", "NewPass@123");
            assertFalse(u.isMustChangePassword(), "In-memory flag must be cleared");

            // Verify in DB
            User reloaded = userDAO.findById(u.getId());
            assertFalse(reloaded.isMustChangePassword());
            assertTrue(PasswordUtil.verifyPassword("NewPass@123", reloaded.getPasswordHash()));

            // Now requireRole succeeds
            assertDoesNotThrow(() -> authService.requireRole(u, Role.STUDENT));
        }
    }

    @Test
    @DisplayName("Verify secure first-run administrator provisioning and duplicate setup prevention")
    void testFirstRunAdminProvisioningWorkflow() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            // Initially no admin exists in cleaned DB
            assertFalse(authService.hasAdminUser());

            // Weak password rejected
            assertThrows(ValidationException.class, () -> authService.provisionInitialAdmin("sysadmin", "short"));
            assertThrows(ValidationException.class, () -> authService.provisionInitialAdmin("sysadmin", "alllowercase123"));
            assertThrows(ValidationException.class, () -> authService.provisionInitialAdmin("sysadmin", "NOLOWERCASE123!"));
            assertThrows(ValidationException.class, () -> authService.provisionInitialAdmin("sysadmin", "NoDigitsSpecial!"));

            // Valid initial admin provisioning
            User newAdmin = authService.provisionInitialAdmin("sysadmin", "Admin@Secure2026");
            assertNotNull(newAdmin);
            assertEquals("sysadmin", newAdmin.getUsername());
            assertEquals(Role.ADMIN, newAdmin.getRole());
            assertFalse(newAdmin.isMustChangePassword());
            assertTrue(newAdmin.isActive());
            assertTrue(authService.hasAdminUser());

            // Duplicate provisioning attempt must be strictly blocked
            assertThrows(IllegalStateException.class,
                    () -> authService.provisionInitialAdmin("anotheradmin", "Admin@Secure2026"));

            // Login with provisioned credentials succeeds
            User authenticated = authService.login("sysadmin", "Admin@Secure2026");
            assertNotNull(authenticated);
            assertEquals(Role.ADMIN, authenticated.getRole());
        }
    }

    @Test
    @DisplayName("Verify concurrent initial Admin provisioning allows exactly one and safely rejects the other")
    void testConcurrentInitialAdminProvisioningAllowsExactlyOne() throws Exception {
        String prevProp = System.getProperty("college.db.path");
        Path tempDb = Files.createTempFile("disposable_admin_concurrency_", ".db");
        String tempDbPath = tempDb.toAbsolutePath().toString();
        System.setProperty("college.db.path", tempDbPath);

        try {
            // 1. Initialize clean schema on disposable database
            try (Connection conn = DBConnection.getConnection(tempDbPath)) {
                DatabaseInitializer.initializeSchemaOnly(conn);
            }

            // 2. Prepare 2 distinct AuthService instances
            AuthService service1 = new AuthService();
            AuthService service2 = new AuthService();

            ExecutorService executor = Executors.newFixedThreadPool(2);
            CyclicBarrier barrier = new CyclicBarrier(2);

            Callable<User> task1 = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return service1.provisionInitialAdmin("admin_alpha", "AlphaAdmin@2026");
            };

            Callable<User> task2 = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return service2.provisionInitialAdmin("admin_beta", "BetaAdmin@2026");
            };

            Future<User> future1 = executor.submit(task1);
            Future<User> future2 = executor.submit(task2);

            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

            User user1 = null;
            User user2 = null;
            Throwable error1 = null;
            Throwable error2 = null;

            try {
                user1 = future1.get();
            } catch (ExecutionException ee) {
                error1 = ee.getCause();
            }

            try {
                user2 = future2.get();
            } catch (ExecutionException ee) {
                error2 = ee.getCause();
            }

            // Exactly one must succeed
            boolean oneSucceeded = (user1 != null && user2 == null) || (user1 == null && user2 != null);
            assertTrue(oneSucceeded, "Exactly one provisioning attempt must succeed. user1=" + user1 + ", user2=" + user2);

            // The other must be rejected safely
            Throwable rejectedError = (user1 != null) ? error2 : error1;
            assertNotNull(rejectedError, "The failing thread must record an exception");
            assertTrue(rejectedError instanceof IllegalStateException
                    || rejectedError.getMessage().contains("already provisioned")
                    || rejectedError.getMessage().contains("locked"),
                    "Rejected thread should fail safely with already provisioned or lock exception: " + rejectedError);

            // Verify in database: exactly one admin user exists
            try (Connection conn = DBConnection.getConnection(tempDbPath)) {
                UserDAO dao = new UserDAO();
                int adminCount = dao.countActiveAdmins(conn);
                assertEquals(1, adminCount, "Exactly one active initial admin must exist in the database");

                String successfulUsername = (user1 != null) ? "admin_alpha" : "admin_beta";
                User dbAdmin = dao.findByUsername(conn, successfulUsername);
                assertNotNull(dbAdmin, "Persisted admin must match the successful thread");
                assertEquals(Role.ADMIN, dbAdmin.getRole());
                assertTrue(dbAdmin.isActive());
                assertFalse(dbAdmin.isMustChangePassword());
            }
        } finally {
            if (prevProp != null) {
                System.setProperty("college.db.path", prevProp);
            } else {
                System.clearProperty("college.db.path");
            }
            try {
                Files.deleteIfExists(tempDb);
                Files.deleteIfExists(Path.of(tempDbPath + "-wal"));
                Files.deleteIfExists(Path.of(tempDbPath + "-shm"));
            } catch (Exception ignored) {}
        }
    }
}

