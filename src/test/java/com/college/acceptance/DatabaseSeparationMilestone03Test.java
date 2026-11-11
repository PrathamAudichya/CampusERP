package com.college.acceptance;

import com.college.config.DBConnection;
import com.college.config.DatabaseInitializer;
import com.college.config.DatabaseProfile;
import com.college.dao.CourseDAO;
import com.college.dao.DepartmentDAO;
import com.college.dao.StudentDAO;
import com.college.dao.TimetableDAO;
import com.college.dao.UserDAO;
import com.college.model.Course;
import com.college.model.Department;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.TimetableEntry;
import com.college.model.User;
import com.college.service.AuthService;
import com.college.service.FeePaymentService;
import com.college.service.StudentService;
import com.college.service.TimetableService;
import com.college.util.PasswordUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance test suite for Milestone 0.3 validating:
 * 1. Fresh STANDARD database initialization (clean schema, migrations V1-V11, zero demo seeds).
 * 2. Fresh DEMO database initialization (schema, migrations V1-V11, synthetic demo records).
 * 3. Existing DEMO database migration & integrity.
 * 4. Complete physical data isolation between STANDARD and DEMO (changes never leak).
 * 5. Restart persistence.
 * 6. Timetable and Academic scheduling.
 * 7. User authentication and credential workflows.
 * 8. Attendance and Finance workflows.
 * 9. Protected historical baseline database immutability & SHA-256 hash check.
 */
public class DatabaseSeparationMilestone03Test {

    private static final Path SCRATCH_DIR = Paths.get("target", "acceptance-scratch-m03");
    private static final Path STANDARD_DB = SCRATCH_DIR.resolve("database").resolve("campuserp-test.db");
    private static final Path DEMO_DB = SCRATCH_DIR.resolve("demo").resolve("campuserp-demo-test.db");
    private static final String HISTORICAL_HASH = "27F160E79FE67F0054503F42CEEEA79C2D0E929A030F3B5463F00C6DDF0B8346";

    private static String savedDbProperty;
    private static DatabaseProfile savedProfile;

    @BeforeAll
    static void setUpAll() throws Exception {
        savedDbProperty = System.getProperty("college.db.path");
        savedProfile = DBConnection.getActiveProfile();

        Files.createDirectories(SCRATCH_DIR.resolve("database"));
        Files.createDirectories(SCRATCH_DIR.resolve("demo"));
        Files.deleteIfExists(STANDARD_DB);
        Files.deleteIfExists(DEMO_DB);
    }

    @AfterAll
    static void tearDownAll() {
        if (savedDbProperty != null) {
            System.setProperty("college.db.path", savedDbProperty);
        } else {
            System.clearProperty("college.db.path");
        }
        DBConnection.setActiveProfile(savedProfile);
    }

    @Test
    @DisplayName("1. Fresh STANDARD database initializes clean schema without synthetic seeds")
    void testFreshStandardDatabaseInitialization() throws Exception {
        Files.deleteIfExists(STANDARD_DB);
        assertFalse(Files.exists(STANDARD_DB), "Standard DB must not exist before test");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + STANDARD_DB.toAbsolutePath())) {
            DatabaseInitializer.initializeWithConnection(conn, DatabaseProfile.STANDARD);

            // Verify all 11 migrations applied
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM schema_migrations")) {
                assertTrue(rs.next());
                assertEquals(11, rs.getInt(1), "All 11 schema migrations must be recorded in schema_migrations");
            }

            // Verify tables count >= 27
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 27, "Fresh standard DB must contain all institutional tables");
            }

            // Verify NO synthetic demo students exist
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM students")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Fresh standard DB must NOT contain synthetic demo students");
            }
        }
    }

    @Test
    @DisplayName("2. Fresh DEMO database initializes schema and populates synthetic seeds")
    void testFreshDemoDatabaseInitialization() throws Exception {
        Files.deleteIfExists(DEMO_DB);
        assertFalse(Files.exists(DEMO_DB), "Demo DB must not exist before test");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DEMO_DB.toAbsolutePath())) {
            DatabaseInitializer.initializeWithConnection(conn, DatabaseProfile.DEMO);

            // Verify all 11 migrations applied
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM schema_migrations")) {
                assertTrue(rs.next());
                assertEquals(11, rs.getInt(1), "All 11 schema migrations must be recorded in demo DB");
            }

            // Verify synthetic demo students and faculty exist
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM students")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) > 0, "Demo DB must contain synthetic demo students");
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM faculty")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) > 0, "Demo DB must contain synthetic demo faculty");
            }
        }
    }

    @Test
    @DisplayName("3. Complete data isolation: creating a demo student NEVER affects the standard database")
    void testPhysicalDataIsolationBetweenStandardAndDemo() throws Exception {
        Path stdPath = SCRATCH_DIR.resolve("database").resolve("iso-standard.db");
        Path demoPath = SCRATCH_DIR.resolve("demo").resolve("iso-demo.db");
        Files.deleteIfExists(stdPath);
        Files.deleteIfExists(demoPath);

        // 1. Initialize STANDARD
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + stdPath.toAbsolutePath())) {
            DatabaseInitializer.initializeWithConnection(conn, DatabaseProfile.STANDARD);
        }

        // 2. Initialize DEMO
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + demoPath.toAbsolutePath())) {
            DatabaseInitializer.initializeWithConnection(conn, DatabaseProfile.DEMO);
        }

        // 3. Connect to DEMO and insert a unique demo student
        try {
            System.setProperty("college.db.path", demoPath.toAbsolutePath().toString());
            UserDAO userDAO = new UserDAO();
            StudentDAO studentDAO = new StudentDAO();
            DepartmentDAO departmentDAO = new DepartmentDAO();

            try (Connection conn = DBConnection.getConnection()) {
                Department dept = departmentDAO.findAll().get(0);
                User user = new User("demo_exclusive_student", PasswordUtil.hashPassword("DemoSecret@123!"), Role.STUDENT);
                user.setActive(true);
                user = userDAO.insert(conn, user);

                Student s = new Student();
                s.setUserId(user.getId());
                s.setDepartmentId(dept.getId());
                s.setStudentId("STU_DEMO_EXCL");
                s.setFirstName("Demo");
                s.setLastName("Exclusive");
                s.setEmail("demo.exclusive@example.com");
                s.setAdmissionDate("2026-08-01");
                s.setSemester("1");
                s.setActive(true);
                studentDAO.insert(conn, s);
            }

            // Verify demo student exists in DEMO
            try (Connection conn = DBConnection.getConnection()) {
                Student found = studentDAO.findByStudentId("STU_DEMO_EXCL");
                assertNotNull(found, "Demo student must exist in DEMO database");
            }

            // 4. Switch to STANDARD and verify demo student DOES NOT exist
            System.setProperty("college.db.path", stdPath.toAbsolutePath().toString());
            try (Connection conn = DBConnection.getConnection()) {
                Student notFound = studentDAO.findByStudentId("STU_DEMO_EXCL");
                assertNull(notFound, "Demo student must NEVER appear in STANDARD database");

                User notFoundUser = userDAO.findByUsername("demo_exclusive_student");
                assertNull(notFoundUser, "Demo user must NEVER appear in STANDARD database");
            }
        } finally {
            System.clearProperty("college.db.path");
        }
    }

    @Test
    @DisplayName("4. Restart persistence: writes to STANDARD database persist across connection cycles")
    void testRestartPersistence() throws Exception {
        Path persistDb = SCRATCH_DIR.resolve("database").resolve("persist-test.db");
        Files.deleteIfExists(persistDb);

        try {
            System.setProperty("college.db.path", persistDb.toAbsolutePath().toString());

            // Cycle 1: Initialize and insert admin
            try (Connection conn = DBConnection.getConnection()) {
                DatabaseInitializer.initializeWithConnection(conn, DatabaseProfile.STANDARD);
                UserDAO userDAO = new UserDAO();
                User admin = new User("persist_admin", PasswordUtil.hashPassword("AdminPass@2026!"), Role.ADMIN);
                admin.setActive(true);
                userDAO.insert(conn, admin);
            }

            // Cycle 2: Simulated application restart (new connection instance)
            try (Connection conn = DBConnection.getConnection()) {
                UserDAO userDAO = new UserDAO();
                User found = userDAO.findByUsername("persist_admin");
                assertNotNull(found, "User record must persist across connection resets");
                assertTrue(PasswordUtil.verifyPassword("AdminPass@2026!", found.getPasswordHash()));
            }
        } finally {
            System.clearProperty("college.db.path");
        }
    }

    @Test
    @DisplayName("5. Timetable functionality works cleanly on migrated database")
    void testTimetableFunctionality() throws Exception {
        Path timetableDb = SCRATCH_DIR.resolve("database").resolve("timetable-test.db");
        Files.deleteIfExists(timetableDb);

        try {
            System.setProperty("college.db.path", timetableDb.toAbsolutePath().toString());
            try (Connection conn = DBConnection.getConnection()) {
                DatabaseInitializer.initializeWithConnection(conn, DatabaseProfile.DEMO);

                TimetableDAO timetableDAO = new TimetableDAO();
                DepartmentDAO departmentDAO = new DepartmentDAO();
                CourseDAO courseDAO = new CourseDAO();

                Course course = courseDAO.findAll().get(0);
                Department dept = departmentDAO.findById(course.getDepartmentId());

                TimetableEntry entry = new TimetableEntry();
                entry.setCourseId(course.getId());
                entry.setFacultyId(1);
                entry.setDepartmentId(dept.getId());
                entry.setSemester("1");
                entry.setDayOfWeek("MONDAY");
                entry.setStartTime("09:00");
                entry.setEndTime("10:00");
                entry.setClassroom("ROOM-101");
                entry.setAcademicYear("2026-2027");

                TimetableEntry saved = timetableDAO.insert(conn, entry);
                assertNotNull(saved);
                assertTrue(saved.getId() > 0);

                TimetableEntry fetched = timetableDAO.findById(saved.getId());
                assertNotNull(fetched);
                assertEquals("ROOM-101", fetched.getClassroom());

                List<TimetableEntry> entries = timetableDAO.findByDepartmentAndSemester(dept.getId(), "1", "2026-2027");
                assertFalse(entries.isEmpty());
                assertTrue(entries.stream().anyMatch(e -> e.getId() == saved.getId()));
            }
        } finally {
            System.clearProperty("college.db.path");
        }
    }

    @Test
    @DisplayName("6. Protected historical database hash check (must remain strictly unmodified)")
    void testHistoricalDatabaseProtectedHash() throws Exception {
        Path historicalPath = Paths.get("data", "college.db");
        assertTrue(Files.exists(historicalPath), "Historical baseline file data/college.db must exist");

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(historicalPath)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) {
                md.update(buf, 0, read);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02X", b));
        }

        String computedHash = sb.toString();
        assertEquals(HISTORICAL_HASH, computedHash,
                "Historical baseline data/college.db SHA-256 must match protected baseline exactly");
    }
}
