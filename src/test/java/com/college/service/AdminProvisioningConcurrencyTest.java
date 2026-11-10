package com.college.service;

import com.college.config.DBConnection;
import com.college.config.DatabaseInitializer;
import com.college.dao.UserDAO;
import com.college.model.Role;
import com.college.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rigorous multi-threaded and cross-process concurrency tests for initial administrator provisioning.
 * Verifies that SQLite's TransactionMode.IMMEDIATE locking prevents race conditions both
 * within a single JVM and across distinct OS processes against disposable databases.
 */
public class AdminProvisioningConcurrencyTest {

    @RepeatedTest(5)
    @DisplayName("Verify in-JVM concurrent initial admin provisioning allows exactly one (5 repetitions)")
    void testConcurrentInitialAdminProvisioningAllowsExactlyOneRepeatedly() throws Exception {
        String prevProp = System.getProperty("college.db.path");
        Path tempDb = Files.createTempFile("disposable_rep_concurrency_", ".db");
        String tempDbPath = tempDb.toAbsolutePath().toString();
        System.setProperty("college.db.path", tempDbPath);

        try {
            try (Connection conn = DBConnection.getConnection(tempDbPath)) {
                DatabaseInitializer.initializeSchemaOnly(conn);
            }

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

            boolean oneSucceeded = (user1 != null && user2 == null) || (user1 == null && user2 != null);
            assertTrue(oneSucceeded, "Exactly one attempt must succeed. user1=" + user1 + ", user2=" + user2);

            Throwable rejectedError = (user1 != null) ? error2 : error1;
            assertNotNull(rejectedError, "The failing thread must record an exception");
            assertTrue(rejectedError instanceof IllegalStateException
                    || (rejectedError.getMessage() != null && (
                            rejectedError.getMessage().contains("already provisioned")
                            || rejectedError.getMessage().contains("locked")
                            || rejectedError.getMessage().contains("SQLITE_BUSY"))),
                    "Rejected thread should fail safely: " + rejectedError);

            try (Connection conn = DBConnection.getConnection(tempDbPath)) {
                UserDAO dao = new UserDAO();
                int adminCount = dao.countActiveAdmins(conn);
                assertEquals(1, adminCount, "Exactly one active initial admin must exist");

                String successfulUsername = (user1 != null) ? "admin_alpha" : "admin_beta";
                User dbAdmin = dao.findByUsername(conn, successfulUsername);
                assertNotNull(dbAdmin);
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

    @Test
    @DisplayName("Verify cross-process concurrent initial admin provisioning beyond a single JVM")
    void testCrossProcessConcurrentInitialAdminProvisioning() throws Exception {
        Path tempDb = Files.createTempFile("disposable_crossproc_concurrency_", ".db");
        String tempDbPath = tempDb.toAbsolutePath().toString();

        try {
            // 1. Initialize schema in disposable database
            try (Connection conn = DBConnection.getConnection(tempDbPath)) {
                DatabaseInitializer.initializeSchemaOnly(conn);
            }

            // 2. Build java command to execute ConcurrentProcessAdminProvisioner
            String javaBin = ProcessHandle.current().info().command().orElse("java");
            String classPath = System.getProperty("java.class.path");

            ProcessBuilder pb1 = new ProcessBuilder(
                    javaBin,
                    "-cp", classPath,
                    "com.college.service.ConcurrentProcessAdminProvisioner",
                    tempDbPath,
                    "proc_admin_1",
                    "ProcAdmin@2026_1"
            );

            ProcessBuilder pb2 = new ProcessBuilder(
                    javaBin,
                    "-cp", classPath,
                    "com.college.service.ConcurrentProcessAdminProvisioner",
                    tempDbPath,
                    "proc_admin_2",
                    "ProcAdmin@2026_2"
            );

            // 3. Start both processes nearly simultaneously
            Process p1 = pb1.start();
            Process p2 = pb2.start();

            boolean p1Exited = p1.waitFor(15, TimeUnit.SECONDS);
            boolean p2Exited = p2.waitFor(15, TimeUnit.SECONDS);

            assertTrue(p1Exited, "Process 1 must exit within timeout");
            assertTrue(p2Exited, "Process 2 must exit within timeout");

            int exit1 = p1.exitValue();
            int exit2 = p2.exitValue();

            String out1 = readProcessOutput(p1);
            String out2 = readProcessOutput(p2);

            // Exactly one process must succeed (exit 0) and the other must be rejected (exit 1)
            boolean exactlyOneSucceeded = (exit1 == 0 && exit2 == 1) || (exit1 == 1 && exit2 == 0);
            assertTrue(exactlyOneSucceeded, String.format(
                    "Exactly one process must exit with 0. p1 exit=%d (out=%s), p2 exit=%d (out=%s)",
                    exit1, out1, exit2, out2));

            // Verify in the database: exactly one admin exists
            try (Connection conn = DBConnection.getConnection(tempDbPath)) {
                UserDAO dao = new UserDAO();
                int adminCount = dao.countActiveAdmins(conn);
                assertEquals(1, adminCount, "Exactly one admin user must exist in the database");

                String successfulUser = (exit1 == 0) ? "proc_admin_1" : "proc_admin_2";
                User dbAdmin = dao.findByUsername(conn, successfulUser);
                assertNotNull(dbAdmin, "Admin created in DB must match successful process username");
                assertEquals(Role.ADMIN, dbAdmin.getRole());
            }
        } finally {
            try {
                Files.deleteIfExists(tempDb);
                Files.deleteIfExists(Path.of(tempDbPath + "-wal"));
                Files.deleteIfExists(Path.of(tempDbPath + "-shm"));
            } catch (Exception ignored) {}
        }
    }

    private String readProcessOutput(Process p) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (Exception ignored) {}
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append("[ERR]").append(line).append("\n");
            }
        } catch (Exception ignored) {}
        return sb.toString().trim();
    }
}

