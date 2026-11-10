package com.college.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance test suite verifying the Milestone 4 (V9) academic structure database migration pipeline:
 * 1. Fresh database initialization with full V9 schema.
 * 2. Upgrade from pre-V9 database schema with data preservation.
 * 3. Complete idempotency on repeated initialization passes.
 * 4. Referential integrity and foreign key constraint verification.
 */
public class DatabaseMigrationMilestone4Test {

    private static final Path TEST_DIR = Paths.get("target", "test-dbs-m4");

    @BeforeAll
    public static void setUpTestDirectory() throws IOException {
        Files.createDirectories(TEST_DIR);
    }

    @Test
    @DisplayName("SCENARIO 1: Fresh Database Initialization with V9 Academic Structure")
    public void testScenario1_FreshDatabaseInitialization() throws Exception {
        Path freshDbPath = TEST_DIR.resolve("fresh-m4-v9.db");
        Files.deleteIfExists(freshDbPath);

        String url = "jdbc:sqlite:" + freshDbPath.toAbsolutePath().toString();

        try (Connection conn = DriverManager.getConnection(url)) {
            // Initially 0 tables
            assertEquals(0, getTableNames(conn).size(), "Fresh database must start with zero tables");

            // Execute DatabaseInitializer
            DatabaseInitializer.initializeWithConnection(conn);

            // Verify V9 schema tables
            Set<String> tables = getTableNames(conn);
            assertTrue(tables.contains("departments"), "departments table must exist");
            assertTrue(tables.contains("academic_programs"), "academic_programs table must exist");
            assertTrue(tables.contains("programme_semesters"), "programme_semesters table must exist");
            assertTrue(tables.contains("programme_curriculum"), "programme_curriculum table must exist");
            assertTrue(tables.contains("student_cohorts"), "student_cohorts table must exist");
            assertTrue(tables.contains("student_progression_history"), "student_progression_history table must exist");

            // Verify department columns
            Set<String> deptCols = getColumnNames(conn, "departments");
            assertTrue(deptCols.contains("school"), "departments must have school column");
            assertTrue(deptCols.contains("description"), "departments must have description column");
            assertTrue(deptCols.contains("head_of_department"), "departments must have head_of_department column");
            assertTrue(deptCols.contains("contact_email"), "departments must have contact_email column");
            assertTrue(deptCols.contains("contact_phone"), "departments must have contact_phone column");

            // Verify academic_programs columns
            Set<String> progCols = getColumnNames(conn, "academic_programs");
            assertTrue(progCols.contains("description"), "academic_programs must have description column");
            assertTrue(progCols.contains("credits_required"), "academic_programs must have credits_required column");
            assertTrue(progCols.contains("academic_regulation"), "academic_programs must have academic_regulation column");

            // Verify students columns
            Set<String> stuCols = getColumnNames(conn, "students");
            assertTrue(stuCols.contains("program_id"), "students must have program_id column");
            assertTrue(stuCols.contains("cohort_id"), "students must have cohort_id column");
            assertTrue(stuCols.contains("admission_year"), "students must have admission_year column");
            assertTrue(stuCols.contains("expected_graduation_year"), "students must have expected_graduation_year column");

            // Verify migration registration
            assertTrue(hasMigration(conn, "V9__academic_structure_and_programmes"),
                    "V9__academic_structure_and_programmes must be recorded in schema_migrations");
            assertTrue(hasMigration(conn, "V10__milestone4_stabilization"),
                    "V10__milestone4_stabilization must be recorded in schema_migrations");
            assertTrue(hasMigration(conn, "V11__progression_history_fk_rebuild"),
                    "V11__progression_history_fk_rebuild must be recorded in schema_migrations");

            // Verify foreign key integrity
            assertNoForeignKeyViolations(conn);

            // Verify foreign key list on student_progression_history
            String fkOnDelete = getForeignKeyOnDelete(conn, "student_progression_history", "students");
            assertEquals("RESTRICT", fkOnDelete, "Fresh database must enforce ON DELETE RESTRICT on students foreign key");
        }
    }

    @Test
    @DisplayName("SCENARIO 2: Upgrade from Pre-V9 Database with Data Preservation")
    public void testScenario2_UpgradeFromPreV9WithDataPreservation() throws Exception {
        Path upgradeDbPath = TEST_DIR.resolve("upgrade-pre-v9.db");
        Files.deleteIfExists(upgradeDbPath);

        String url = "jdbc:sqlite:" + upgradeDbPath.toAbsolutePath().toString();

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            // Create older schema (simulating V8) without Milestone 4 columns and tables
            stmt.execute("CREATE TABLE schema_migrations (version TEXT PRIMARY KEY, description TEXT NOT NULL, applied_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')))");
            stmt.execute("INSERT INTO schema_migrations (version, description) VALUES ('V1__base_schema', 'Initial core schema')");
            stmt.execute("INSERT INTO schema_migrations (version, description) VALUES ('V8__attendance_automation_enhancements', 'Attendance audit')");

            stmt.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, username TEXT UNIQUE, password_hash TEXT, role TEXT, must_change_password INTEGER DEFAULT 0, active INTEGER DEFAULT 1)");
            stmt.execute("CREATE TABLE departments (id INTEGER PRIMARY KEY, code TEXT UNIQUE, name TEXT, active INTEGER DEFAULT 1)");
            stmt.execute("CREATE TABLE courses (id INTEGER PRIMARY KEY, department_id INTEGER REFERENCES departments(id), course_code TEXT UNIQUE, name TEXT, credits INTEGER, semester TEXT, max_capacity INTEGER DEFAULT 60, active INTEGER DEFAULT 1)");
            stmt.execute("CREATE TABLE students (id INTEGER PRIMARY KEY, user_id INTEGER REFERENCES users(id), department_id INTEGER REFERENCES departments(id), student_id TEXT UNIQUE, first_name TEXT, last_name TEXT, email TEXT UNIQUE, phone TEXT, date_of_birth TEXT, admission_date TEXT, semester TEXT DEFAULT '1', active INTEGER DEFAULT 1)");
            stmt.execute("CREATE TABLE faculty (id INTEGER PRIMARY KEY, user_id INTEGER REFERENCES users(id), department_id INTEGER REFERENCES departments(id), faculty_id TEXT UNIQUE, first_name TEXT, last_name TEXT, email TEXT UNIQUE, phone TEXT, specialization TEXT, join_date TEXT, active INTEGER DEFAULT 1)");
            stmt.execute("CREATE TABLE enrollments (id INTEGER PRIMARY KEY, student_id INTEGER REFERENCES students(id), course_id INTEGER REFERENCES courses(id), academic_year TEXT, enrolled_date TEXT, status TEXT DEFAULT 'ACTIVE')");
            stmt.execute("CREATE TABLE attendance (id INTEGER PRIMARY KEY, enrollment_id INTEGER REFERENCES enrollments(id), attendance_date TEXT, status TEXT, remarks TEXT, UNIQUE(enrollment_id, attendance_date))");
            stmt.execute("CREATE TABLE fee_structures (id INTEGER PRIMARY KEY, department_id INTEGER REFERENCES departments(id), semester TEXT, fee_type TEXT, amount_paise INTEGER, academic_year TEXT, due_date TEXT, active INTEGER DEFAULT 1)");
            stmt.execute("CREATE TABLE fee_payments (id INTEGER PRIMARY KEY, student_id INTEGER REFERENCES students(id), fee_structure_id INTEGER REFERENCES fee_structures(id), amount_paid_paise INTEGER, payment_date TEXT, payment_method TEXT, transaction_ref TEXT UNIQUE, status TEXT, original_payment_id INTEGER REFERENCES fee_payments(id), created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            stmt.execute("CREATE TABLE audit_log (id INTEGER PRIMARY KEY, user_id INTEGER, action TEXT, entity_type TEXT, entity_id INTEGER, old_value TEXT, new_value TEXT, timestamp TEXT DEFAULT CURRENT_TIMESTAMP)");

            // Seed historical data
            stmt.execute("INSERT INTO departments VALUES (1, 'HIST_CS', 'Historical CS', 1)");
            stmt.execute("INSERT INTO users VALUES (1, 'admin', 'hash', 'ADMIN', 0, 1)");
            stmt.execute("INSERT INTO users VALUES (2, 'hist_stu', 'hash', 'STUDENT', 0, 1)");
            stmt.execute("INSERT INTO students VALUES (1, 2, 1, 'OLD001', 'Old', 'Student', 'old@college.test', '123', '2005-01-01', '2024-08-01', '1', 1)");

            // Apply migrations via DatabaseInitializer
            DatabaseInitializer.initializeWithConnection(conn);

            // Verify historical data preserved
            try (ResultSet rs = stmt.executeQuery("SELECT name, school FROM departments WHERE code = 'HIST_CS'")) {
                assertTrue(rs.next(), "Historical department must exist");
                assertEquals("Historical CS", rs.getString("name"));
                assertEquals("School of Engineering", rs.getString("school"), "Default school must be populated");
            }

            try (ResultSet rs = stmt.executeQuery("SELECT first_name, semester, program_id, cohort_id FROM students WHERE student_id = 'OLD001'")) {
                assertTrue(rs.next(), "Historical student must exist");
                assertEquals("Old", rs.getString("first_name"));
                assertEquals("1", rs.getString("semester"));
                assertNull(rs.getObject("program_id"), "Legacy student initially has null program_id");
                assertNull(rs.getObject("cohort_id"), "Legacy student initially has null cohort_id");
            }

            // Verify newly created V9 tables exist
            Set<String> tables = getTableNames(conn);
            assertTrue(tables.contains("academic_programs"));
            assertTrue(tables.contains("programme_semesters"));
            assertTrue(tables.contains("programme_curriculum"));
            assertTrue(tables.contains("student_cohorts"));
            assertTrue(tables.contains("student_progression_history"));

            // Verify migrations registered
            assertTrue(hasMigration(conn, "V9__academic_structure_and_programmes"));
            assertTrue(hasMigration(conn, "V10__milestone4_stabilization"));
            assertTrue(hasMigration(conn, "V11__progression_history_fk_rebuild"));

            // Verify foreign key integrity
            assertNoForeignKeyViolations(conn);

            // Verify foreign key list on student_progression_history
            String fkOnDelete = getForeignKeyOnDelete(conn, "student_progression_history", "students");
            assertEquals("RESTRICT", fkOnDelete, "Upgraded database must enforce ON DELETE RESTRICT on students foreign key");
        }
    }

    @Test
    @DisplayName("SCENARIO 3: Complete Idempotency on Repeated Invocations")
    public void testScenario3_MigrationIdempotency() throws Exception {
        Path idemDbPath = TEST_DIR.resolve("idempotency-m4.db");
        Files.deleteIfExists(idemDbPath);

        String url = "jdbc:sqlite:" + idemDbPath.toAbsolutePath().toString();

        try (Connection conn = DriverManager.getConnection(url)) {
            // First run
            DatabaseInitializer.initializeWithConnection(conn);
            int tableCountPass1 = getTableNames(conn).size();
            int migrationCountPass1 = getMigrationCount(conn);

            // Second run
            DatabaseInitializer.initializeWithConnection(conn);
            int tableCountPass2 = getTableNames(conn).size();
            int migrationCountPass2 = getMigrationCount(conn);

            // Third run
            DatabaseInitializer.initializeWithConnection(conn);
            int tableCountPass3 = getTableNames(conn).size();
            int migrationCountPass3 = getMigrationCount(conn);

            assertEquals(tableCountPass1, tableCountPass2, "Table count must remain invariant across passes");
            assertEquals(tableCountPass2, tableCountPass3, "Table count must remain invariant across passes");
            assertEquals(migrationCountPass1, migrationCountPass2, "Migration count must remain invariant across passes");
            assertEquals(migrationCountPass2, migrationCountPass3, "Migration count must remain invariant across passes");

            assertNoForeignKeyViolations(conn);
        }
    }

    @Test
    @DisplayName("SCENARIO 5: Milestone 4 Stabilization Triggers & Historical Department School Normalization")
    public void testScenario5_Milestone4StabilizationAndNormalization() throws Exception {
        Path stabDbPath = TEST_DIR.resolve("stab-m4-v10.db");
        Files.deleteIfExists(stabDbPath);

        String url = "jdbc:sqlite:" + stabDbPath.toAbsolutePath().toString();

        try (Connection conn = DriverManager.getConnection(url)) {
            DatabaseInitializer.initializeWithConnection(conn);

            // 1. Verify triggers exist
            Set<String> triggers = new HashSet<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='trigger'")) {
                while (rs.next()) {
                    triggers.add(rs.getString("name").toLowerCase());
                }
            }
            assertTrue(triggers.contains("trg_prevent_progression_history_del"), "Delete prevention trigger must exist");
            assertTrue(triggers.contains("trg_prevent_progression_history_upd"), "Update prevention trigger must exist");
            assertTrue(triggers.contains("trg_student_cohort_prog_ins"), "Cohort-prog insert trigger must exist");
            assertTrue(triggers.contains("trg_student_cohort_prog_upd"), "Cohort-prog update trigger must exist");

            // 2. Insert historical departments to verify name-verified normalization
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT OR REPLACE INTO departments (id, code, name, school, active) " +
                        "VALUES (999, 'CA', 'Computer Applications', 'School of Engineering', 1)");
                stmt.execute("INSERT OR REPLACE INTO departments (id, code, name, school, active) " +
                        "VALUES (998, 'ENG', 'English Literature', 'School of Engineering', 1)");
                stmt.execute("INSERT OR REPLACE INTO departments (id, code, name, school, active) " +
                        "VALUES (997, 'GEN_ENG', 'General Engineering', 'School of Engineering', 1)");
            }

            // Run initializer to simulate migration pass
            DatabaseInitializer.initializeWithConnection(conn);

            // Verify CA school normalized to 'School of Computer Applications'
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT school FROM departments WHERE code = 'CA'")) {
                assertTrue(rs.next());
                assertEquals("School of Computer Applications", rs.getString("school"),
                        "Historical CA department must be normalized from School of Engineering to School of Computer Applications");
            }

            // Verify ENG (English Literature) normalized to 'School of Arts & Humanities'
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT school FROM departments WHERE code = 'ENG'")) {
                assertTrue(rs.next());
                assertEquals("School of Arts & Humanities", rs.getString("school"),
                        "Historical ENG department with English Literature name must be normalized to School of Arts & Humanities");
            }

            // Verify GEN_ENG (General Engineering) remains in 'School of Engineering'
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT school FROM departments WHERE code = 'GEN_ENG'")) {
                assertTrue(rs.next());
                assertEquals("School of Engineering", rs.getString("school"),
                        "Historical Engineering department must remain in School of Engineering");
            }
        }
    }

    @Test
    @DisplayName("SCENARIO 6: Legacy V9 CASCADE Foreign Key Rebuild to RESTRICT and Student Deletion Safety")
    public void testScenario6_LegacyV9CascadeRebuildToRestrictAndStudentDeletionSafety() throws Exception {
        Path legacyDbPath = TEST_DIR.resolve("legacy-v9-cascade.db");
        Files.deleteIfExists(legacyDbPath);

        String url = "jdbc:sqlite:" + legacyDbPath.toAbsolutePath().toString();

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            // Enable foreign keys
            stmt.execute("PRAGMA foreign_keys = ON;");

            // Build base schema with legacy V9 CASCADE foreign key
            stmt.execute("CREATE TABLE schema_migrations (version TEXT PRIMARY KEY, description TEXT NOT NULL)");
            stmt.execute("INSERT INTO schema_migrations VALUES ('V1__base_schema', 'Initial')");
            stmt.execute("INSERT INTO schema_migrations VALUES ('V9__academic_structure_and_programmes', 'V9')");

            stmt.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, username TEXT UNIQUE, password_hash TEXT, role TEXT, must_change_password INTEGER DEFAULT 0, active INTEGER DEFAULT 1)");
            stmt.execute("CREATE TABLE departments (id INTEGER PRIMARY KEY, code TEXT UNIQUE, name TEXT, school TEXT, description TEXT, head_of_department TEXT, contact_email TEXT, contact_phone TEXT, active INTEGER DEFAULT 1)");
            stmt.execute("CREATE TABLE students (id INTEGER PRIMARY KEY, user_id INTEGER REFERENCES users(id), department_id INTEGER REFERENCES departments(id), student_id TEXT UNIQUE, first_name TEXT, last_name TEXT, email TEXT UNIQUE, phone TEXT, date_of_birth TEXT, admission_date TEXT, semester TEXT DEFAULT '1', active INTEGER DEFAULT 1)");

            // Legacy V9 table with ON DELETE CASCADE
            stmt.execute("CREATE TABLE student_progression_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "student_id INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE, " +
                    "previous_semester INTEGER NOT NULL, " +
                    "new_semester INTEGER NOT NULL, " +
                    "academic_year TEXT NOT NULL, " +
                    "action TEXT NOT NULL, " +
                    "performed_by INTEGER NOT NULL REFERENCES users(id), " +
                    "reason TEXT NOT NULL, " +
                    "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");

            // Insert initial student and progression records
            stmt.execute("INSERT INTO users (id, username, password_hash, role, must_change_password, active) VALUES (1, 'admin', 'hash', 'ADMIN', 0, 1)");
            stmt.execute("INSERT INTO users (id, username, password_hash, role, must_change_password, active) VALUES (2, 'stu1', 'hash', 'STUDENT', 0, 1)");
            stmt.execute("INSERT INTO departments (id, code, name, school, active) VALUES (1, 'CS', 'Computer Science', 'School of Engineering', 1)");
            stmt.execute("INSERT INTO students (id, user_id, department_id, student_id, first_name, last_name, email, semester, active) VALUES (1, 2, 1, 'STU001', 'Alice', 'Smith', 'alice@college.test', '1', 1)");
            stmt.execute("INSERT INTO student_progression_history VALUES (1, 1, 1, 2, '2026-2027', 'PROMOTED', 1, 'Passed term 1', CURRENT_TIMESTAMP)");

            // Verify that prior to upgrade, FK is CASCADE
            String preUpgradeFk = getForeignKeyOnDelete(conn, "student_progression_history", "students");
            assertEquals("CASCADE", preUpgradeFk, "Pre-upgrade database must have legacy ON DELETE CASCADE");

            // Execute DatabaseInitializer upgrade
            DatabaseInitializer.initializeWithConnection(conn);

            // Verify migration V11 applied
            assertTrue(hasMigration(conn, "V11__progression_history_fk_rebuild"),
                    "V11__progression_history_fk_rebuild must be registered");

            // Verify FK is now RESTRICT
            String postUpgradeFk = getForeignKeyOnDelete(conn, "student_progression_history", "students");
            assertEquals("RESTRICT", postUpgradeFk,
                    "V11 migration must safely rebuild table to enforce ON DELETE RESTRICT");

            // Verify zero FK violations
            assertNoForeignKeyViolations(conn);

            // Verify historical progression data preserved intact across rebuild
            try (ResultSet rs = stmt.executeQuery("SELECT reason FROM student_progression_history WHERE id = 1")) {
                assertTrue(rs.next(), "Progression history row must be preserved across table rebuild");
                assertEquals("Passed term 1", rs.getString("reason"));
            }

            // Verify that deleting the student is now strictly BLOCKED by foreign key constraint
            SQLException deleteEx = assertThrows(SQLException.class, () ->
                    stmt.executeUpdate("DELETE FROM students WHERE id = 1"));
            assertTrue(deleteEx.getMessage().toLowerCase().contains("foreign key constraint failed") ||
                       deleteEx.getMessage().toLowerCase().contains("constraint"));

            // Verify student still exists
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM students WHERE id = 1")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Student row must remain intact");
            }

            // Verify progression history row was NOT deleted by cascade
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM student_progression_history WHERE id = 1")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Progression history row must not be cascaded");
            }
        }
    }

    // Helper methods
    private Set<String> getTableNames(Connection conn) throws SQLException {
        Set<String> tables = new HashSet<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
            while (rs.next()) {
                tables.add(rs.getString("name").toLowerCase());
            }
        }
        return tables;
    }

    private Set<String> getColumnNames(Connection conn, String table) throws SQLException {
        Set<String> cols = new HashSet<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                cols.add(rs.getString("name").toLowerCase());
            }
        }
        return cols;
    }

    private boolean hasMigration(Connection conn, String version) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1 FROM schema_migrations WHERE version = '" + version + "'")) {
            return rs.next();
        }
    }

    private int getMigrationCount(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_migrations")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void assertNoForeignKeyViolations(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_check")) {
            List<String> violations = new ArrayList<>();
            while (rs.next()) {
                violations.add(String.format("Table: %s, RowID: %d, Target: %s, FkID: %d",
                        rs.getString(1), rs.getLong(2), rs.getString(3), rs.getInt(4)));
            }
            assertTrue(violations.isEmpty(), "Foreign key check failed with violations: " + violations);
        }
    }

    private String getForeignKeyOnDelete(Connection conn, String table, String refTable) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_list(" + table + ")")) {
            while (rs.next()) {
                if (refTable.equalsIgnoreCase(rs.getString("table"))) {
                    return rs.getString("on_delete");
                }
            }
        }
        return null;
    }
}
