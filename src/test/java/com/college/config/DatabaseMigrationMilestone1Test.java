package com.college.config;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
 * Rigorous Milestone 1 acceptance test suite verifying the 7-stage database migration pipeline
 * across all three required scenarios:
 * 1. Fresh database initialization from resources
 * 2. Disposable copy of v2.0.8 baseline database
 * 3. Disposable v2.0.9-style database with missing columns and unmigrated tables
 *
 * Verifies pre-conditions, post-conditions, foreign key integrity, row preservation,
 * and complete second-pass idempotency.
 */
public class DatabaseMigrationMilestone1Test {

    private static final Path TEST_DIR = Paths.get("target", "test-dbs");

    @BeforeAll
    public static void setUpTestDirectory() throws IOException {
        Files.createDirectories(TEST_DIR);
    }

    @Test
    @DisplayName("SCENARIO 1: Fresh Database Initialization from Current Resources")
    public void testScenario1_FreshDatabaseInitialization() throws Exception {
        Path freshDbPath = TEST_DIR.resolve("scenario1-fresh.db");
        Files.deleteIfExists(freshDbPath);

        String url = "jdbc:sqlite:" + freshDbPath.toAbsolutePath().toString();

        try (Connection conn = DriverManager.getConnection(url)) {
            // Initial state: empty database
            assertEquals(0, getTableNames(conn).size(), "Fresh database must start with zero tables");

            // Execute application initialization
            DatabaseInitializer.initializeWithConnection(conn);

            // Verify all required tables exist
            verifyRequiredSchemaObjects(conn);

            // Verify migrations recorded
            verifyAllMigrationVersionsPresent(conn);

            // Verify foreign key integrity
            assertNoForeignKeyViolations(conn);

            // Verify attendance table columns
            verifyAttendanceTableColumns(conn);

            // Verify seed data row counts
            int userCount = getRowCount(conn, "users");
            int studentCount = getRowCount(conn, "students");
            int facultyCount = getRowCount(conn, "faculty");
            assertTrue(userCount >= 5, "Demo users should be present");
            assertTrue(studentCount >= 3, "Demo students should be present");
            assertTrue(facultyCount >= 2, "Demo faculty should be present");

            // Verify Idempotency: second initialization pass
            DatabaseInitializer.initializeWithConnection(conn);

            // Verify counts unchanged after second pass
            assertEquals(userCount, getRowCount(conn, "users"), "Users count unchanged after second pass");
            assertEquals(studentCount, getRowCount(conn, "students"), "Students count unchanged after second pass");
            assertNoForeignKeyViolations(conn);
        }
    }

    @Test
    @DisplayName("SCENARIO 2: Disposable Copy of v2.0.8 Baseline Database")
    public void testScenario2_V208BaselineDatabaseMigration() throws Exception {
        Path baselineSource = Paths.get("data", "college.db");
        Assumptions.assumeTrue(Files.exists(baselineSource), "Production baseline college.db not present in CI/clean-checkout environment; skipping baseline copy scenario");

        Path disposableDbPath = TEST_DIR.resolve("scenario2-v208-disposable.db");
        Files.copy(baselineSource, disposableDbPath, StandardCopyOption.REPLACE_EXISTING);

        String url = "jdbc:sqlite:" + disposableDbPath.toAbsolutePath().toString();

        try (Connection conn = DriverManager.getConnection(url)) {
            // Verify starting schema: session_code should NOT exist initially in baseline
            assertFalse(columnExists(conn, "attendance", "session_code"),
                    "v2.0.8 baseline attendance table must NOT contain session_code prior to migration");
            assertFalse(tableExists(conn, "timetables"),
                    "v2.0.8 baseline must NOT contain timetables table prior to migration");

            // Record initial row counts to ensure data preservation
            int initialStudents = getRowCount(conn, "students");
            int initialFaculty = getRowCount(conn, "faculty");
            int initialCourses = getRowCount(conn, "courses");
            int initialEnrollments = getRowCount(conn, "enrollments");
            int initialAttendance = getRowCount(conn, "attendance");
            int initialFeePayments = getRowCount(conn, "fee_payments");

            // Execute application initialization / migration pipeline
            DatabaseInitializer.initializeWithConnection(conn);

            // Verify postconditions
            verifyRequiredSchemaObjects(conn);
            verifyAttendanceTableColumns(conn);
            verifyAllMigrationVersionsPresent(conn);
            assertNoForeignKeyViolations(conn);

            // Verify zero data loss: existing records remain intact
            assertEquals(initialStudents, getRowCount(conn, "students"), "Student records must be preserved");
            assertEquals(initialFaculty, getRowCount(conn, "faculty"), "Faculty records must be preserved");
            assertEquals(initialCourses, getRowCount(conn, "courses"), "Course records must be preserved");
            assertEquals(initialEnrollments, getRowCount(conn, "enrollments"), "Enrollment records must be preserved");
            assertEquals(initialAttendance, getRowCount(conn, "attendance"), "Attendance records must be preserved");
            assertEquals(initialFeePayments, getRowCount(conn, "fee_payments"), "Fee payment records must be preserved");

            // Verify Idempotency: execute second time
            DatabaseInitializer.initializeWithConnection(conn);

            // Verify row counts and integrity unchanged
            assertEquals(initialStudents, getRowCount(conn, "students"), "Row counts must remain stable");
            assertNoForeignKeyViolations(conn);
        }
    }

    @Test
    @DisplayName("SCENARIO 3: Disposable v2.0.9-Style Database Missing session_code and timetables")
    public void testScenario3_V209StyleMissingObjectsMigration() throws Exception {
        Path disposableDbPath = TEST_DIR.resolve("scenario3-v209-style.db");
        Files.deleteIfExists(disposableDbPath);

        String url = "jdbc:sqlite:" + disposableDbPath.toAbsolutePath().toString();

        // Construct synthetic v2.0.9-style database with base tables but MISSING session_code and timetables
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, username TEXT UNIQUE, password_hash TEXT, role TEXT, must_change_password INTEGER DEFAULT 0, active INTEGER DEFAULT 1)");
                stmt.execute("CREATE TABLE departments (id INTEGER PRIMARY KEY, code TEXT UNIQUE, name TEXT, active INTEGER DEFAULT 1)");
                stmt.execute("CREATE TABLE courses (id INTEGER PRIMARY KEY, department_id INTEGER REFERENCES departments(id), course_code TEXT UNIQUE, name TEXT, credits INTEGER, semester TEXT, max_capacity INTEGER DEFAULT 60, active INTEGER DEFAULT 1)");
                stmt.execute("CREATE TABLE students (id INTEGER PRIMARY KEY, user_id INTEGER REFERENCES users(id), department_id INTEGER REFERENCES departments(id), student_id TEXT UNIQUE, first_name TEXT, last_name TEXT, email TEXT UNIQUE, phone TEXT, date_of_birth TEXT, admission_date TEXT, semester TEXT DEFAULT '1', active INTEGER DEFAULT 1)");
                stmt.execute("CREATE TABLE faculty (id INTEGER PRIMARY KEY, user_id INTEGER REFERENCES users(id), department_id INTEGER REFERENCES departments(id), faculty_id TEXT UNIQUE, first_name TEXT, last_name TEXT, email TEXT UNIQUE, phone TEXT, specialization TEXT, join_date TEXT, active INTEGER DEFAULT 1)");
                stmt.execute("CREATE TABLE enrollments (id INTEGER PRIMARY KEY, student_id INTEGER REFERENCES students(id), course_id INTEGER REFERENCES courses(id), academic_year TEXT, enrolled_date TEXT, status TEXT DEFAULT 'ACTIVE')");
                // Base attendance without session_code
                stmt.execute("CREATE TABLE attendance (id INTEGER PRIMARY KEY, enrollment_id INTEGER REFERENCES enrollments(id), attendance_date TEXT, status TEXT, remarks TEXT, UNIQUE(enrollment_id, attendance_date))");
                stmt.execute("CREATE TABLE fee_structures (id INTEGER PRIMARY KEY, department_id INTEGER REFERENCES departments(id), semester TEXT, fee_type TEXT, amount_paise INTEGER, academic_year TEXT, due_date TEXT, active INTEGER DEFAULT 1)");
                stmt.execute("CREATE TABLE fee_payments (id INTEGER PRIMARY KEY, student_id INTEGER REFERENCES students(id), fee_structure_id INTEGER REFERENCES fee_structures(id), amount_paid_paise INTEGER, payment_date TEXT, payment_method TEXT, transaction_ref TEXT UNIQUE, status TEXT, original_payment_id INTEGER REFERENCES fee_payments(id), created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
                stmt.execute("CREATE TABLE audit_log (id INTEGER PRIMARY KEY, user_id INTEGER, action TEXT, entity_type TEXT, entity_id INTEGER, old_value TEXT, new_value TEXT, timestamp TEXT DEFAULT CURRENT_TIMESTAMP)");

                // Populate test records
                stmt.execute("INSERT INTO departments VALUES (1, 'CS', 'Computer Science', 1)");
                stmt.execute("INSERT INTO courses VALUES (1, 1, 'CS101', 'Data Structures', 4, '1', 60, 1)");
                stmt.execute("INSERT INTO users VALUES (1, 'admin', 'hash', 'ADMIN', 0, 1)");
                stmt.execute("INSERT INTO users VALUES (2, 'STU2026001', 'hash', 'STUDENT', 0, 1)");
                stmt.execute("INSERT INTO students VALUES (1, 2, 1, 'STU2026001', 'Test', 'Student', 'test@example.test', '123', '2005-01-01', '2026-08-01', '1', 1)");
                stmt.execute("INSERT INTO enrollments VALUES (1, 1, 1, '2026-2027', '2026-08-01', 'ACTIVE')");
                stmt.execute("INSERT INTO attendance VALUES (1, 1, '2026-09-01', 'PRESENT', 'Initial record')");
            }

            // Verify precondition: session_code is absent
            assertFalse(columnExists(conn, "attendance", "session_code"));
            assertFalse(tableExists(conn, "timetables"));

            // Execute application initialization / migration pipeline
            DatabaseInitializer.initializeWithConnection(conn);

            // Verify postconditions
            assertTrue(columnExists(conn, "attendance", "session_code"), "session_code must be added");
            assertTrue(tableExists(conn, "timetables"), "timetables table must be created");
            verifyRequiredSchemaObjects(conn);
            verifyAllMigrationVersionsPresent(conn);
            assertNoForeignKeyViolations(conn);

            // Verify existing attendance record survived
            assertEquals(1, getRowCount(conn, "attendance"));
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT status, remarks FROM attendance WHERE id=1")) {
                assertTrue(rs.next());
                assertEquals("PRESENT", rs.getString("status"));
                assertEquals("Initial record", rs.getString("remarks"));
            }

            // Verify Idempotency: execute second time
            DatabaseInitializer.initializeWithConnection(conn);
            assertEquals(1, getRowCount(conn, "attendance"), "Attendance record count preserved on rerun");
            assertNoForeignKeyViolations(conn);
        }
    }

    // Helper validation methods

    private void verifyRequiredSchemaObjects(Connection conn) throws SQLException {
        String[] expectedTables = {
                "users", "departments", "students", "faculty", "courses",
                "course_assignments", "enrollments", "attendance", "examinations",
                "marks", "fee_structures", "fee_payments", "audit_log", "timetables",
                "attendance_sessions", "attendance_submissions", "academic_programs",
                "assessment_schemes", "assessment_items", "student_assessment_marks",
                "institution_bank_details", "schema_migrations"
        };
        for (String table : expectedTables) {
            assertTrue(tableExists(conn, table), "Expected table must exist: " + table);
        }

        // Verify key indexes
        assertTrue(indexExists(conn, "idx_att_session_code"), "idx_att_session_code index must exist");
        assertTrue(indexExists(conn, "idx_timetables_course"), "idx_timetables_course index must exist");
        assertTrue(indexExists(conn, "idx_timetables_faculty"), "idx_timetables_faculty index must exist");
        assertTrue(indexExists(conn, "idx_att_sess_code"), "idx_att_sess_code index must exist");
        assertTrue(indexExists(conn, "idx_programs_code"), "idx_programs_code index must exist");
        assertTrue(indexExists(conn, "idx_ass_scheme_course"), "idx_ass_scheme_course index must exist");
    }

    private void verifyAttendanceTableColumns(Connection conn) throws SQLException {
        boolean sessionCodeFound = false;
        String sessionCodeType = null;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(attendance)")) {
            while (rs.next()) {
                if ("session_code".equalsIgnoreCase(rs.getString("name"))) {
                    sessionCodeFound = true;
                    sessionCodeType = rs.getString("type");
                }
            }
        }
        assertTrue(sessionCodeFound, "attendance table must contain session_code column");
        assertNotNull(sessionCodeType);
        assertTrue(sessionCodeType.toUpperCase().contains("TEXT"),
                "session_code must have TEXT type, was: " + sessionCodeType);
    }

    private void verifyAllMigrationVersionsPresent(Connection conn) throws SQLException {
        Set<String> expectedVersions = Set.of(
                "V1__base_schema",
                "V2__attendance_session_code",
                "V3__timetables",
                "V4__attendance_sessions",
                "V5__academic_programs",
                "V6__assessment_schemes",
                "V7__bank_details_and_online_payments"
        );
        Set<String> actualVersions = new HashSet<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version FROM schema_migrations")) {
            while (rs.next()) {
                actualVersions.add(rs.getString("version"));
            }
        }
        for (String v : expectedVersions) {
            assertTrue(actualVersions.contains(v), "schema_migrations must contain applied version: " + v);
        }
    }

    private void assertNoForeignKeyViolations(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_check")) {
            List<String> violations = new ArrayList<>();
            while (rs.next()) {
                violations.add("Table=" + rs.getString(1) + ", RowID=" + rs.getString(2) + ", Target=" + rs.getString(3));
            }
            assertTrue(violations.isEmpty(), "PRAGMA foreign_key_check found violations: " + violations);
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='" + tableName + "'")) {
            return rs.next();
        }
    }

    private boolean indexExists(Connection conn, String indexName) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1 FROM sqlite_master WHERE type='index' AND name='" + indexName + "'")) {
            return rs.next();
        }
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private int getRowCount(Connection conn, String tableName) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT count(*) FROM " + tableName)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    private List<String> getTableNames(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
            while (rs.next()) {
                tables.add(rs.getString("name"));
            }
        }
        return tables;
    }
}

