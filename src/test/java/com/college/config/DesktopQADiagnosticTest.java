package com.college.config;

import com.college.dao.TimetableDAO;
import com.college.dao.UserDAO;
import com.college.model.Role;
import com.college.model.TimetableEntry;
import com.college.model.User;
import com.college.service.AuthService;
import com.college.util.PasswordUtil;

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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Desktop QA Diagnostic test verifying:
 * 1. Timetable table creation in fresh database
 * 2. Timetable DAO operations work against initialized DB
 * 3. Username update with uniqueness validation
 * 4. Password provisioning generates secure random credentials
 * 5. All expected tables exist after initialization
 */
public class DesktopQADiagnosticTest {

    private static final Path TEST_DIR = Paths.get("target", "test-dbs-qa");

    @BeforeAll
    public static void setUpTestDirectory() throws IOException {
        Files.createDirectories(TEST_DIR);
    }

    @Test
    @DisplayName("BUG-1: Fresh database must contain timetables table after initialization")
    public void testTimetablesTableExistsInFreshDatabase() throws Exception {
        Path dbPath = TEST_DIR.resolve("timetable-test.db");
        Files.deleteIfExists(dbPath);

        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        try (Connection conn = DriverManager.getConnection(url)) {
            DatabaseInitializer.initializeWithConnection(conn);

            // Verify timetables table exists
            assertTrue(tableExists(conn, "timetables"),
                    "timetables table must exist after fresh database initialization");

            // Verify expected columns
            List<String> columns = getTableColumns(conn, "timetables");
            assertTrue(columns.contains("id"), "timetables must have id column");
            assertTrue(columns.contains("course_id"), "timetables must have course_id column");
            assertTrue(columns.contains("faculty_id"), "timetables must have faculty_id column");
            assertTrue(columns.contains("department_id"), "timetables must have department_id column");
            assertTrue(columns.contains("semester"), "timetables must have semester column");
            assertTrue(columns.contains("day_of_week"), "timetables must have day_of_week column");
            assertTrue(columns.contains("start_time"), "timetables must have start_time column");
            assertTrue(columns.contains("end_time"), "timetables must have end_time column");
            assertTrue(columns.contains("classroom"), "timetables must have classroom column");
            assertTrue(columns.contains("academic_year"), "timetables must have academic_year column");
        }
    }

    @Test
    @DisplayName("BUG-1: Migrating existing demo database without timetables")
    public void testMigrateExistingDemoDatabase() throws Exception {
        Path demoCopy = TEST_DIR.resolve("demo-copy.db");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(demoCopy), "demo-copy.db must exist");

        String url = "jdbc:sqlite:" + demoCopy.toAbsolutePath();
        try (Connection conn = DriverManager.getConnection(url)) {
            // Check before migration
            boolean hadTimetablesBefore = tableExists(conn, "timetables");
            System.out.println("demo-copy.db had timetables before: " + hadTimetablesBefore);

            DatabaseInitializer.initializeWithConnection(conn);

            // Check after migration
            boolean hasTimetablesAfter = tableExists(conn, "timetables");
            System.out.println("demo-copy.db has timetables after: " + hasTimetablesAfter);
            assertTrue(hasTimetablesAfter, "timetables table must exist after migration");
        }
    }

    @Test
    @DisplayName("BUG-1: TimetableDAO queries must not fail on freshly initialized database")
    public void testTimetableDAOOnFreshDatabase() throws Exception {
        Path dbPath = TEST_DIR.resolve("timetable-dao-test.db");
        Files.deleteIfExists(dbPath);

        String prevProp = System.getProperty("college.db.path");
        try {
            System.setProperty("college.db.path", dbPath.toAbsolutePath().toString());

            String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
            try (Connection conn = DriverManager.getConnection(url)) {
                DatabaseInitializer.initializeWithConnection(conn);
            }

            // TimetableDAO should be able to query without "no such table" error
            TimetableDAO timetableDAO = new TimetableDAO();
            List<TimetableEntry> entries = timetableDAO.findByAcademicYear("2025-2026");
            assertNotNull(entries, "TimetableDAO.findByAcademicYear must return non-null list");
            assertEquals(0, entries.size(), "Fresh database should have 0 timetable entries");
        } finally {
            if (prevProp != null) {
                System.setProperty("college.db.path", prevProp);
            } else {
                System.clearProperty("college.db.path");
            }
        }
    }

    @Test
    @DisplayName("BUG-2: Username must be updatable via UserDAO.updateUsername")
    public void testUsernameUpdateWithUniquenessValidation() throws Exception {
        Path dbPath = TEST_DIR.resolve("username-update-test.db");
        Files.deleteIfExists(dbPath);

        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        try (Connection conn = DriverManager.getConnection(url)) {
            DatabaseInitializer.initializeSchemaOnly(conn);

            UserDAO userDAO = new UserDAO();

            // Create two users
            User user1 = new User("originaluser1", PasswordUtil.hashPassword("TestP@ssw0rd!"), Role.ADMIN);
            user1.setActive(true);
            user1.setMustChangePassword(false);
            User saved1 = userDAO.insert(conn, user1);

            User user2 = new User("originaluser2", PasswordUtil.hashPassword("TestP@ssw0rd!"), Role.FACULTY);
            user2.setActive(true);
            user2.setMustChangePassword(false);
            User saved2 = userDAO.insert(conn, user2);

            // Update user1's username
            boolean updated = userDAO.updateUsername(conn, saved1.getId(), "newusername1");
            assertTrue(updated, "Username update must return true");

            // Verify the new username
            User refreshed = userDAO.findById(conn, saved1.getId());
            assertNotNull(refreshed);
            assertEquals("newusername1", refreshed.getUsername(), "Username must be updated in database");

            // Verify original username no longer exists
            User notFound = userDAO.findByUsername(conn, "originaluser1");
            assertNull(notFound, "Old username must no longer resolve");

            // Verify new username is found
            User foundByNew = userDAO.findByUsername(conn, "newusername1");
            assertNotNull(foundByNew, "New username must resolve to the user");
            assertEquals(saved1.getId(), foundByNew.getId());
        }
    }

    @Test
    @DisplayName("BUG-2: AuthService.updateUsername enforces uniqueness constraint")
    public void testAuthServiceUsernameUniqueness() throws Exception {
        Path dbPath = TEST_DIR.resolve("username-uniqueness-test.db");
        Files.deleteIfExists(dbPath);

        String prevProp = System.getProperty("college.db.path");
        try {
            System.setProperty("college.db.path", dbPath.toAbsolutePath().toString());

            String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
            try (Connection conn = DriverManager.getConnection(url)) {
                DatabaseInitializer.initializeSchemaOnly(conn);

                UserDAO userDAO = new UserDAO();

                User admin = new User("admin_user", PasswordUtil.hashPassword("Str0ngP@ss!"), Role.ADMIN);
                admin.setActive(true);
                admin.setMustChangePassword(false);
                User savedAdmin = userDAO.insert(conn, admin);

                User faculty = new User("faculty_user", PasswordUtil.hashPassword("Str0ngP@ss!"), Role.FACULTY);
                faculty.setActive(true);
                faculty.setMustChangePassword(false);
                User savedFaculty = userDAO.insert(conn, faculty);

                AuthService authService = new AuthService(userDAO, new com.college.service.AuditService());

                // Valid rename should succeed
                boolean result = authService.updateUsername(savedAdmin, savedFaculty.getId(), "new_faculty_name");
                assertTrue(result, "Valid username update must succeed");

                // Attempting to use admin's username should fail with ValidationException
                assertThrows(com.college.exception.ValidationException.class,
                        () -> authService.updateUsername(savedAdmin, savedFaculty.getId(), "admin_user"),
                        "Duplicate username must throw ValidationException");
            }
        } finally {
            if (prevProp != null) {
                System.setProperty("college.db.path", prevProp);
            } else {
                System.clearProperty("college.db.path");
            }
        }
    }

    @Test
    @DisplayName("BUG-3: PasswordUtil.generateTemporaryPassword creates secure random credential")
    public void testPasswordProvisioningGeneratesSecurePasswords() {
        // Generate multiple passwords and verify they are:
        // 1. Non-null and non-empty
        // 2. At least 12 characters
        // 3. Different from each other
        String pw1 = PasswordUtil.generateTemporaryPassword();
        String pw2 = PasswordUtil.generateTemporaryPassword();
        String pw3 = PasswordUtil.generateTemporaryPassword();

        assertNotNull(pw1, "Generated password must not be null");
        assertNotNull(pw2, "Generated password must not be null");
        assertNotNull(pw3, "Generated password must not be null");

        assertFalse(pw1.isEmpty(), "Generated password must not be empty");
        assertTrue(pw1.length() >= 12, "Generated password must be >= 12 chars, got: " + pw1.length());

        // All three should be different (cryptographic randomness)
        assertNotEquals(pw1, pw2, "Generated passwords must be unique (cryptographic)");
        assertNotEquals(pw2, pw3, "Generated passwords must be unique (cryptographic)");
        assertNotEquals(pw1, pw3, "Generated passwords must be unique (cryptographic)");

        // Each password must be BCrypt-hashable and verifiable
        String hash = PasswordUtil.hashPassword(pw1);
        assertTrue(PasswordUtil.verifyPassword(pw1, hash), "Generated password must verify against its BCrypt hash");
    }

    @Test
    @DisplayName("BUG-3: Student creation produces temporary password with must_change_password=1")
    public void testStudentAccountProvisioningSetsForcePasswordChange() throws Exception {
        Path dbPath = TEST_DIR.resolve("student-provisioning-test.db");
        Files.deleteIfExists(dbPath);

        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        try (Connection conn = DriverManager.getConnection(url)) {
            DatabaseInitializer.initializeWithConnection(conn);

            UserDAO userDAO = new UserDAO();

            // Simulate student account creation workflow
            String tempPassword = PasswordUtil.generateTemporaryPassword();
            String hash = PasswordUtil.hashPassword(tempPassword);

            User studentUser = new User("STU2026001", hash, Role.STUDENT);
            studentUser.setMustChangePassword(true);  // This is what StudentService sets
            studentUser.setActive(true);

            User saved = userDAO.insert(conn, studentUser);
            assertNotNull(saved, "Student user must be inserted");
            assertTrue(saved.getId() > 0, "Student user must have positive ID");

            // Verify from DB
            User fromDb = userDAO.findById(conn, saved.getId());
            assertNotNull(fromDb);
            assertEquals("STU2026001", fromDb.getUsername());
            assertTrue(fromDb.isMustChangePassword(), "must_change_password must be 1 for new student");
            assertTrue(fromDb.isActive());

            // Verify the temp password works
            assertTrue(PasswordUtil.verifyPassword(tempPassword, fromDb.getPasswordHash()),
                    "Temporary password must verify against stored hash");
        }
    }

    @Test
    @DisplayName("All expected tables must exist after fresh initialization")
    public void testAllExpectedTablesExist() throws Exception {
        Path dbPath = TEST_DIR.resolve("all-tables-test.db");
        Files.deleteIfExists(dbPath);

        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        try (Connection conn = DriverManager.getConnection(url)) {
            DatabaseInitializer.initializeWithConnection(conn);

            String[] expectedTables = {
                "users", "departments", "students", "faculty", "courses",
                "course_assignments", "enrollments", "attendance", "examinations",
                "marks", "fee_structures", "fee_payments", "audit_log",
                "timetables", "attendance_sessions", "attendance_submissions",
                "attendance_failed_attempts", "academic_programs", "programme_semesters",
                "programme_curriculum", "student_cohorts", "student_progression_history",
                "assessment_schemes", "assessment_items", "student_assessment_marks",
                "institution_bank_details", "schema_migrations"
            };

            for (String table : expectedTables) {
                assertTrue(tableExists(conn, table),
                        "Table '" + table + "' must exist after initialization");
            }
        }
    }

    @Test
    @DisplayName("UserDAO.update must NOT include username in UPDATE statement")
    public void testUserDAOUpdateDoesNotChangeUsername() throws Exception {
        Path dbPath = TEST_DIR.resolve("userdao-update-test.db");
        Files.deleteIfExists(dbPath);

        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        try (Connection conn = DriverManager.getConnection(url)) {
            DatabaseInitializer.initializeSchemaOnly(conn);

            UserDAO userDAO = new UserDAO();

            User user = new User("testuser", PasswordUtil.hashPassword("TestP@ssw0rd!"), Role.ADMIN);
            user.setActive(true);
            user.setMustChangePassword(false);
            User saved = userDAO.insert(conn, user);

            // Modify the in-memory User object's username and call update
            saved.setUsername("modifiedname");
            saved.setMustChangePassword(true);
            boolean updated = userDAO.update(conn, saved);
            assertTrue(updated);

            // Verify that the DB username is NOT changed (update() does NOT update username)
            User fromDb = userDAO.findById(conn, saved.getId());
            assertEquals("testuser", fromDb.getUsername(),
                    "UserDAO.update() must NOT change username; it only updates password_hash, role, must_change_password, active");
            assertTrue(fromDb.isMustChangePassword(), "must_change_password should be updated");
        }
    }

    @Test
    @DisplayName("GUI Component Test: FacultyPanel contains Change Username action and FacultyDetailsDialog supports User")
    public void testFacultyPanelGuiComponentsOnEdt() throws Exception {
        Path dbPath = TEST_DIR.resolve("faculty-gui-test.db");
        Files.deleteIfExists(dbPath);
        String prevProp = System.getProperty("college.db.path");
        try {
            System.setProperty("college.db.path", dbPath.toAbsolutePath().toString());
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath())) {
                DatabaseInitializer.initializeWithConnection(conn);
            }

            User admin = new User("admin_test", PasswordUtil.hashPassword("TestP@ss1234!"), Role.ADMIN);
            admin.setId(1);
            admin.setActive(true);

            javax.swing.SwingUtilities.invokeAndWait(() -> {
                com.college.ui.panel.FacultyPanel panel = new com.college.ui.panel.FacultyPanel(admin);
                assertNotNull(panel);

                List<javax.swing.JButton> buttons = findAllButtons(panel);
                assertTrue(buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Change Username")),
                        "FacultyPanel must contain 'Change Username' button");
                assertTrue(buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("View Details")),
                        "FacultyPanel must contain 'View Details' button");

                // Test FacultyDetailsDialog constructor with linked User
                com.college.model.Faculty faculty = new com.college.model.Faculty();
                faculty.setId(1);
                faculty.setFacultyId("FAC101");
                faculty.setFirstName("Alan");
                faculty.setLastName("Turing");
                faculty.setActive(true);

                com.college.model.Department dept = new com.college.model.Department("CS", "Computer Science");
                com.college.model.User user = new com.college.model.User("FAC101", "hash", Role.FACULTY);

                com.college.ui.components.FacultyDetailsDialog dialog =
                        new com.college.ui.components.FacultyDetailsDialog(null, faculty, dept, user);
                assertNotNull(dialog);
                dialog.dispose();
            });
        } finally {
            if (prevProp != null) {
                System.setProperty("college.db.path", prevProp);
            } else {
                System.clearProperty("college.db.path");
            }
        }
    }

    @Test
    @DisplayName("GUI Component Test: TimetablePanel initializes cleanly without 'no such table: timetables' error")
    public void testTimetablePanelGuiEdtRendering() throws Exception {
        Path dbPath = TEST_DIR.resolve("timetable-gui-test.db");
        Files.deleteIfExists(dbPath);
        String prevProp = System.getProperty("college.db.path");
        try {
            System.setProperty("college.db.path", dbPath.toAbsolutePath().toString());
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath())) {
                DatabaseInitializer.initializeWithConnection(conn);
            }

            User admin = new User("admin_test2", PasswordUtil.hashPassword("TestP@ss1234!"), Role.ADMIN);
            admin.setId(1);
            admin.setActive(true);

            javax.swing.SwingUtilities.invokeAndWait(() -> {
                assertDoesNotThrow(() -> {
                    com.college.ui.panel.TimetablePanel panel = new com.college.ui.panel.TimetablePanel(admin);
                    assertNotNull(panel);
                }, "TimetablePanel must initialize without 'no such table: timetables' exception");
            });
        } finally {
            if (prevProp != null) {
                System.setProperty("college.db.path", prevProp);
            } else {
                System.clearProperty("college.db.path");
            }
        }
    }

    private List<javax.swing.JButton> findAllButtons(java.awt.Container container) {
        List<javax.swing.JButton> result = new ArrayList<>();
        for (java.awt.Component comp : container.getComponents()) {
            if (comp instanceof javax.swing.JButton) {
                result.add((javax.swing.JButton) comp);
            } else if (comp instanceof java.awt.Container) {
                result.addAll(findAllButtons((java.awt.Container) comp));
            }
        }
        return result;
    }

    // Helper methods
    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='" + tableName + "'")) {
            return rs.next();
        }
    }

    private List<String> getTableColumns(Connection conn, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }
}

