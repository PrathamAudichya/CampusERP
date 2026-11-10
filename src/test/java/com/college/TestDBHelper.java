package com.college;

import com.college.config.DatabaseInitializer;
import org.sqlite.SQLiteConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Shared-memory SQLite test harness and trigger-aware table cleanup helper.
 *
 * Provides isolated in-memory testing using SQLite shared cache with:
 * - Foreign key enforcement (PRAGMA foreign_keys = ON)
 * - IMMEDIATE transaction mode (SQLiteConfig.TransactionMode.IMMEDIATE)
 * - Persistent keeper connection to prevent shared memory destruction between test methods
 * - cleanAllTables() teardown that safely handles immutable triggers, self-referencing FKs,
 *   and resets primary key autoincrement sequences.
 */
public class TestDBHelper {

    public static final String TEST_DB_URL =
            "jdbc:sqlite:file:testdb_" + ProcessHandle.current().pid() + "?mode=memory&cache=shared";

    private static Connection keepAliveConnection;

    private TestDBHelper() {
        // Utility class
    }

    /**
     * Initializes the shared-memory database once per JVM test process.
     * The keepAliveConnection prevents SQLite from destroying the shared in-memory database
     * when individual test connections close.
     */
    public static synchronized void initTestDatabase() throws Exception {
        System.setProperty("college.db.path", TEST_DB_URL);
        if (keepAliveConnection == null || keepAliveConnection.isClosed()) {
            SQLiteConfig config = new SQLiteConfig();
            config.enforceForeignKeys(true);
            config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
            config.setBusyTimeout(5000);

            // Establish and hold open keeper connection for entire test JVM lifecycle
            keepAliveConnection = DriverManager.getConnection(TEST_DB_URL, config.toProperties());
            DatabaseInitializer.initializeWithConnection(keepAliveConnection);

            // JVM shutdown hook ensures keeper connection is cleanly closed upon test run completion
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    tearDownTestDatabase();
                } catch (Exception ignored) {
                }
            }));
        }
    }

    /**
     * Obtains a new connection to the shared-memory test database.
     *
     * @return configured JDBC Connection with FK enforcement, IMMEDIATE transaction mode, and 5000ms busy timeout
     * @throws SQLException if a connection cannot be established
     */
    public static Connection getTestConnection() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        config.setBusyTimeout(5000);
        return DriverManager.getConnection(TEST_DB_URL, config.toProperties());
    }

    /**
     * Resets all tables between test executions while preserving production trigger behavior during tests.
     * 1. Temporarily drops trg_audit_log_no_delete to allow test-harness cleanup.
     * 2. Deletes child refund transactions first to satisfy self-referencing foreign keys on fee_payments.
     * 3. Deletes from all 13 tables in reverse foreign key order.
     * 4. Resets sqlite_sequence so autoincrement primary keys predictably restart from 1.
     * 5. Re-instates trg_audit_log_no_delete to guarantee full production trigger enforcement during tests.
     *
     * @param conn database connection to use for cleanup
     * @throws SQLException if cleanup fails
     */
    public static void cleanAllTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            boolean triggerDropped = false;
            Throwable cleanupFailure = null;
            try {
                // Step 1: Temporarily drop audit delete trigger and progression delete trigger for test teardown
                stmt.execute("DROP TRIGGER IF EXISTS trg_audit_log_no_delete");
                stmt.execute("DROP TRIGGER IF EXISTS trg_prevent_progression_history_del");
                triggerDropped = true;

                // Step 2: Delete child refund transactions first (self-referencing FK)
                stmt.executeUpdate("DELETE FROM fee_payments WHERE original_payment_id IS NOT NULL");

                // Step 3: Delete from all tables in reverse FK dependency order
                String[] tablesInReverseFkOrder = {
                        "student_progression_history",
                        "attendance_failed_attempts",
                        "attendance_submissions", "attendance_sessions",
                        "student_assessment_marks", "assessment_items", "assessment_schemes",
                        "timetables", "audit_log", "marks", "attendance", "fee_payments",
                        "enrollments", "examinations", "course_assignments",
                        "students",
                        "student_cohorts",
                        "programme_curriculum",
                        "programme_semesters",
                        "fee_structures", "courses",
                        "academic_programs",
                        "faculty",
                        "departments", "users",
                        "institution_bank_details", "schema_migrations"
                };
                for (String table : tablesInReverseFkOrder) {
                    try (ResultSet rs = stmt.executeQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
                        if (rs.next()) {
                            stmt.executeUpdate("DELETE FROM " + table);
                        }
                    }
                }

                // Step 4: Reset autoincrement sequences
                stmt.executeUpdate("DELETE FROM sqlite_sequence");
            } catch (Throwable t) {
                cleanupFailure = t;
                if (t instanceof SQLException sqlEx) {
                    throw sqlEx;
                } else if (t instanceof RuntimeException rtEx) {
                    throw rtEx;
                } else if (t instanceof Error err) {
                    throw err;
                } else {
                    throw new SQLException("Table cleanup encountered failure", t);
                }
            } finally {
                if (triggerDropped) {
                    try (Statement restoreStmt = conn.createStatement()) {
                        // Step 5: Re-instate production audit delete trigger and progression delete trigger
                        restoreStmt.execute("""
                            CREATE TRIGGER IF NOT EXISTS trg_audit_log_no_delete
                            BEFORE DELETE ON audit_log
                            BEGIN
                                SELECT RAISE(FAIL, 'Deletions from audit_log are prohibited.');
                            END;
                        """);
                        restoreStmt.execute("""
                            CREATE TRIGGER IF NOT EXISTS trg_prevent_progression_history_del
                            BEFORE DELETE ON student_progression_history
                            BEGIN
                                SELECT RAISE(ABORT, 'Progression history records are immutable and cannot be deleted.');
                            END;
                        """);
                    } catch (Throwable restoreEx) {
                        if (cleanupFailure != null) {
                            cleanupFailure.addSuppressed(restoreEx);
                        } else {
                            if (restoreEx instanceof SQLException sqlEx) {
                                throw sqlEx;
                            } else if (restoreEx instanceof RuntimeException rtEx) {
                                throw rtEx;
                            } else if (restoreEx instanceof Error err) {
                                throw err;
                            } else {
                                throw new SQLException("Failed to reinstate trg_audit_log_no_delete", restoreEx);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Closes the persistent keep-alive connection, allowing the in-memory database to be garbage collected.
     *
     * @throws Exception if closing fails
     */
    public static synchronized void tearDownTestDatabase() throws Exception {
        if (keepAliveConnection != null && !keepAliveConnection.isClosed()) {
            keepAliveConnection.close();
            keepAliveConnection = null;
        }
    }
}

