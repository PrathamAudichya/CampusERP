package com.college;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification test suite for TestDBHelper.cleanAllTables() failure-path remediation.
 *
 * Verifies:
 * 1. Normal cleanup completes and restores the trg_audit_log_no_delete trigger.
 * 2. Injected failure during an intermediate deletion still causes trigger recreation to be attempted.
 * 3. The original cleanup exception remains observable.
 * 4. A trigger recreation failure is reported via suppressed exceptions without silently hiding the cleanup failure.
 * 5. Subsequent audit-log deletion remains blocked when trigger recreation succeeds.
 */
public class TestDBHelperRemediationTest {

    private static Connection testConn;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        testConn = TestDBHelper.getTestConnection();
    }

    @AfterEach
    void tearDown() throws Exception {
        TestDBHelper.cleanAllTables(testConn);
    }

    @Test
    @DisplayName("1. Normal cleanup completes and restores trg_audit_log_no_delete trigger")
    void testNormalCleanupCompletesAndRestoresTrigger() throws Exception {
        TestDBHelper.cleanAllTables(testConn);

        // Populate test data
        long userId;
        try (Statement stmt = testConn.createStatement()) {
            stmt.executeUpdate("INSERT INTO users (username, password_hash, role) VALUES ('clean_user', 'hash', 'ADMIN')", Statement.RETURN_GENERATED_KEYS);
            try (ResultSet genKeys = stmt.getGeneratedKeys()) {
                assertTrue(genKeys.next());
                userId = genKeys.getLong(1);
            }
            stmt.executeUpdate("INSERT INTO audit_log (user_id, action, entity_type, entity_id) VALUES (" + userId + ", 'CREATE', 'users', 1)");
        }

        // Execute cleanup
        TestDBHelper.cleanAllTables(testConn);

        // Verify tables are empty
        try (Statement stmt = testConn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "users table must be empty");
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM audit_log")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "audit_log table must be empty");
            }

            // Verify trigger was restored and prohibits deletion
            stmt.executeUpdate("INSERT INTO users (username, password_hash, role) VALUES ('audit_user', 'hash', 'ADMIN')", Statement.RETURN_GENERATED_KEYS);
            long newUserId;
            try (ResultSet genKeys = stmt.getGeneratedKeys()) {
                assertTrue(genKeys.next());
                newUserId = genKeys.getLong(1);
            }
            stmt.executeUpdate("INSERT INTO audit_log (user_id, action, entity_type, entity_id) VALUES (" + newUserId + ", 'INSERT_TEST', 'users', 1)");

            SQLException ex = assertThrows(SQLException.class, () -> {
                stmt.executeUpdate("DELETE FROM audit_log");
            }, "Deleting from audit_log must fail due to restored trigger");
            assertTrue(ex.getMessage().contains("Deletions from audit_log are prohibited"),
                    "Trigger error message must match expected: " + ex.getMessage());
        }
    }

    @Test
    @DisplayName("2. Injected failure during intermediate deletion still attempts trigger recreation and blocks audit delete")
    void testInjectedFailureDuringIntermediateDeletionRestoresTrigger() throws Exception {
        // Proxy connection that injects an intermediate failure on DELETE FROM marks
        // but permits normal execution of CREATE TRIGGER on restoreStmt
        Connection proxyConn = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("createStatement".equals(method.getName())) {
                            Statement realStmt = (Statement) method.invoke(testConn, args);
                            return (Statement) Proxy.newProxyInstance(
                                    Statement.class.getClassLoader(),
                                    new Class<?>[]{Statement.class},
                                    new InvocationHandler() {
                                        @Override
                                        public Object invoke(Object stmtProxy, Method stmtMethod, Object[] stmtArgs) throws Throwable {
                                            if ("executeUpdate".equals(stmtMethod.getName()) && stmtArgs != null && stmtArgs.length > 0) {
                                                String sql = (String) stmtArgs[0];
                                                if (sql.contains("DELETE FROM marks")) {
                                                    throw new SQLException("Injected failure during intermediate deletion on marks");
                                                }
                                            }
                                            return stmtMethod.invoke(realStmt, stmtArgs);
                                        }
                                    }
                            );
                        }
                        return method.invoke(testConn, args);
                    }
                }
        );

        // Call cleanAllTables - must throw the injected intermediate failure
        SQLException thrown = assertThrows(SQLException.class, () -> {
            TestDBHelper.cleanAllTables(proxyConn);
        }, "cleanAllTables must propagate intermediate deletion failure");

        // Requirement: The original cleanup exception remains observable
        assertTrue(thrown.getMessage().contains("Injected failure during intermediate deletion on marks"),
                "Original cleanup exception message must be preserved: " + thrown.getMessage());

        // Requirement: Trigger recreation was attempted and succeeded in finally block
        // Verify trigger is active on real testConn
        try (Statement stmt = testConn.createStatement()) {
            long userId;
            try (ResultSet rs = stmt.executeQuery("SELECT id FROM users LIMIT 1")) {
                if (rs.next()) {
                    userId = rs.getLong(1);
                } else {
                    stmt.executeUpdate("INSERT INTO users (username, password_hash, role) VALUES ('audit_user2', 'hash', 'ADMIN')", Statement.RETURN_GENERATED_KEYS);
                    try (ResultSet genKeys = stmt.getGeneratedKeys()) {
                        assertTrue(genKeys.next());
                        userId = genKeys.getLong(1);
                    }
                }
            }

            stmt.executeUpdate("INSERT INTO audit_log (user_id, action, entity_type, entity_id) VALUES (" + userId + ", 'POST_FAILURE_TEST', 'marks', 99)");

            SQLException deleteEx = assertThrows(SQLException.class, () -> {
                stmt.executeUpdate("DELETE FROM audit_log WHERE action = 'POST_FAILURE_TEST'");
            }, "Audit deletion must still be blocked because trigger was restored in finally block");
            assertTrue(deleteEx.getMessage().contains("Deletions from audit_log are prohibited"),
                    "Audit trigger must block deletions after intermediate failure: " + deleteEx.getMessage());
        }
    }

    @Test
    @DisplayName("3. Trigger recreation failure preserves original cleanup exception and attaches recreation failure as suppressed")
    void testTriggerRecreationFailurePreservesBothExceptionsViaSuppression() {
        // Wrap connection in dynamic proxy to inject both a deletion failure and a trigger recreation failure
        Connection proxyConn = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("createStatement".equals(method.getName())) {
                            Statement realStmt = (Statement) method.invoke(testConn, args);
                            return (Statement) Proxy.newProxyInstance(
                                    Statement.class.getClassLoader(),
                                    new Class<?>[]{Statement.class},
                                    new InvocationHandler() {
                                        @Override
                                        public Object invoke(Object stmtProxy, Method stmtMethod, Object[] stmtArgs) throws Throwable {
                                            String methodName = stmtMethod.getName();
                                            if ("executeUpdate".equals(methodName) && stmtArgs != null && stmtArgs.length > 0) {
                                                String sql = (String) stmtArgs[0];
                                                if (sql.contains("DELETE FROM marks")) {
                                                    throw new SQLException("Injected primary cleanup error on marks");
                                                }
                                            }
                                            if ("execute".equals(methodName) && stmtArgs != null && stmtArgs.length > 0) {
                                                String sql = (String) stmtArgs[0];
                                                if (sql.contains("CREATE TRIGGER IF NOT EXISTS trg_audit_log_no_delete")) {
                                                    throw new SQLException("Injected secondary trigger recreation failure");
                                                }
                                            }
                                            return stmtMethod.invoke(realStmt, stmtArgs);
                                        }
                                    }
                            );
                        }
                        return method.invoke(testConn, args);
                    }
                }
        );

        SQLException thrown = assertThrows(SQLException.class, () -> {
            TestDBHelper.cleanAllTables(proxyConn);
        });

        // Requirement: Original cleanup exception remains observable
        assertTrue(thrown.getMessage().contains("Injected primary cleanup error on marks"),
                "Original cleanup failure must remain primary: " + thrown.getMessage());

        // Requirement: Trigger recreation failure is reported without silently hiding cleanup failure
        Throwable[] suppressed = thrown.getSuppressed();
        assertEquals(1, suppressed.length, "Exactly one suppressed exception must be attached");
        assertTrue(suppressed[0] instanceof SQLException, "Suppressed exception must be SQLException");
        assertTrue(suppressed[0].getMessage().contains("Injected secondary trigger recreation failure"),
                "Suppressed exception must contain trigger recreation failure message: " + suppressed[0].getMessage());
    }

    @Test
    @DisplayName("4. Trigger recreation failure alone is reported directly when cleanup succeeds")
    void testTriggerRecreationFailureAloneIsReportedDirectly() {
        Connection proxyConn = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("createStatement".equals(method.getName())) {
                            Statement realStmt = (Statement) method.invoke(testConn, args);
                            return (Statement) Proxy.newProxyInstance(
                                    Statement.class.getClassLoader(),
                                    new Class<?>[]{Statement.class},
                                    new InvocationHandler() {
                                        @Override
                                        public Object invoke(Object stmtProxy, Method stmtMethod, Object[] stmtArgs) throws Throwable {
                                            if ("execute".equals(stmtMethod.getName()) && stmtArgs != null && stmtArgs.length > 0) {
                                                String sql = (String) stmtArgs[0];
                                                if (sql.contains("CREATE TRIGGER IF NOT EXISTS trg_audit_log_no_delete")) {
                                                    throw new SQLException("Injected standalone trigger recreation error");
                                                }
                                            }
                                            return stmtMethod.invoke(realStmt, stmtArgs);
                                        }
                                    }
                            );
                        }
                        return method.invoke(testConn, args);
                    }
                }
        );

        SQLException thrown = assertThrows(SQLException.class, () -> {
            TestDBHelper.cleanAllTables(proxyConn);
        });

        assertTrue(thrown.getMessage().contains("Injected standalone trigger recreation error"),
                "Standalone trigger recreation error must be directly reported: " + thrown.getMessage());
        assertEquals(0, thrown.getSuppressed().length, "No suppressed exceptions expected on standalone failure");
    }

    @Test
    @DisplayName("5. Drop trigger failure does not attempt recreation and propagates directly")
    void testDropTriggerFailureDoesNotAttemptRecreation() {
        boolean[] createTriggerAttempted = new boolean[]{false};

        Connection proxyConn = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("createStatement".equals(method.getName())) {
                            Statement realStmt = (Statement) method.invoke(testConn, args);
                            return (Statement) Proxy.newProxyInstance(
                                    Statement.class.getClassLoader(),
                                    new Class<?>[]{Statement.class},
                                    new InvocationHandler() {
                                        @Override
                                        public Object invoke(Object stmtProxy, Method stmtMethod, Object[] stmtArgs) throws Throwable {
                                            if ("execute".equals(stmtMethod.getName()) && stmtArgs != null && stmtArgs.length > 0) {
                                                String sql = (String) stmtArgs[0];
                                                if (sql.contains("DROP TRIGGER")) {
                                                    throw new SQLException("Injected drop trigger error");
                                                }
                                                if (sql.contains("CREATE TRIGGER")) {
                                                    createTriggerAttempted[0] = true;
                                                }
                                            }
                                            return stmtMethod.invoke(realStmt, stmtArgs);
                                        }
                                    }
                            );
                        }
                        return method.invoke(testConn, args);
                    }
                }
        );

        SQLException thrown = assertThrows(SQLException.class, () -> {
            TestDBHelper.cleanAllTables(proxyConn);
        });

        assertTrue(thrown.getMessage().contains("Injected drop trigger error"),
                "Drop trigger error must propagate: " + thrown.getMessage());
        assertFalse(createTriggerAttempted[0],
                "Trigger recreation should NOT be attempted if drop trigger failed");
    }
}
