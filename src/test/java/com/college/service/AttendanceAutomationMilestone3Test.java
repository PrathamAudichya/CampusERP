package com.college.service;

import com.college.TestDBHelper;
import com.college.config.DBConnection;
import com.college.config.DatabaseInitializer;
import com.college.dao.*;
import com.college.exception.AuthenticationException;
import com.college.exception.ValidationException;
import com.college.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 test suite verifying Milestone 3: Complete Attendance Automation.
 * Validates session lifecycle, 6-digit codes, leading zeroes, code regeneration,
 * student check-in, rate-limiting, expiration, manual review overrides, mandatory remarks,
 * atomic finalization, institutional 75% attendance policy, and authorization boundaries.
 */
public class AttendanceAutomationMilestone3Test {

    private AttendanceSessionService sessionService;
    private AttendanceService attendanceService;
    private AttendanceSessionDAO sessionDAO;
    private AttendanceDAO attendanceDAO;
    private EnrollmentDAO enrollmentDAO;
    private StudentDAO studentDAO;
    private FacultyDAO facultyDAO;
    private CourseDAO courseDAO;
    private CourseAssignmentDAO assignmentDAO;
    private UserDAO userDAO;
    private DepartmentDAO departmentDAO;

    private User adminUser;
    private User facultyUser1;
    private User facultyUser2;
    private User studentUser1;
    private User studentUser2;
    private User studentUser3;
    private User studentUserInactive;

    private Faculty faculty1;
    private Faculty faculty2;
    private Student student1;
    private Student student2;
    private Student student3;
    private Student studentInactive;

    private Department dept;
    private Course course1;
    private Course course2;
    private Enrollment enr1;
    private Enrollment enr2;
    private Enrollment enr3;

    @BeforeAll
    static void initGlobalTestDb() throws Exception {
        TestDBHelper.initTestDatabase();
    }

    @BeforeEach
    void setUp() throws Exception {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }

        sessionDAO = new AttendanceSessionDAO();
        attendanceDAO = new AttendanceDAO();
        enrollmentDAO = new EnrollmentDAO();
        studentDAO = new StudentDAO();
        facultyDAO = new FacultyDAO();
        courseDAO = new CourseDAO();
        assignmentDAO = new CourseAssignmentDAO();
        userDAO = new UserDAO();
        departmentDAO = new DepartmentDAO();

        sessionService = new AttendanceSessionService(
                sessionDAO, attendanceDAO, enrollmentDAO, studentDAO, facultyDAO,
                courseDAO, assignmentDAO, new AuditService());
        attendanceService = new AttendanceService();

        setupTestData();
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    private void setupTestData() throws Exception {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            // Admin
            adminUser = userDAO.insert(conn, new User("admin_test", "$2a$12$dummyHash", Role.ADMIN));

            // Faculty
            facultyUser1 = userDAO.insert(conn, new User("fac1_test", "$2a$12$dummyHash", Role.FACULTY));
            facultyUser2 = userDAO.insert(conn, new User("fac2_test", "$2a$12$dummyHash", Role.FACULTY));

            // Students
            studentUser1 = userDAO.insert(conn, new User("stu1_test", "$2a$12$dummyHash", Role.STUDENT));
            studentUser2 = userDAO.insert(conn, new User("stu2_test", "$2a$12$dummyHash", Role.STUDENT));
            studentUser3 = userDAO.insert(conn, new User("stu3_test", "$2a$12$dummyHash", Role.STUDENT));

            User inactU = new User("stu_inact", "$2a$12$dummyHash", Role.STUDENT);
            inactU.setActive(false);
            studentUserInactive = userDAO.insert(conn, inactU);

            // Department
            dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));

            // Faculty profiles
            faculty1 = facultyDAO.insert(conn, new Faculty(facultyUser1.getId(), dept.getId(), "FAC001", "Alan", "Turing", "alan@college.edu", "9876543210", "AI", "2020-01-01"));
            faculty2 = facultyDAO.insert(conn, new Faculty(facultyUser2.getId(), dept.getId(), "FAC002", "Grace", "Hopper", "grace@college.edu", "9876543211", "Systems", "2020-01-01"));

            // Student profiles
            student1 = studentDAO.insert(conn, new Student(studentUser1.getId(), dept.getId(), "STU001", "Ada", "Lovelace", "ada@college.edu", "9876543212", "2002-05-10", "2026-08-01", "1"));
            student2 = studentDAO.insert(conn, new Student(studentUser2.getId(), dept.getId(), "STU002", "Charles", "Babbage", "charles@college.edu", "9876543213", "2001-11-20", "2026-08-01", "1"));
            student3 = studentDAO.insert(conn, new Student(studentUser3.getId(), dept.getId(), "STU003", "Claude", "Shannon", "claude@college.edu", "9876543214", "2002-03-15", "2026-08-01", "1"));

            Student inactS = new Student(studentUserInactive.getId(), dept.getId(), "STU004", "Inactive", "Student", "inactive@college.edu", "9876543215", "2001-01-01", "2026-08-01", "1");
            inactS.setActive(false);
            studentInactive = studentDAO.insert(conn, inactS);

            // Courses
            course1 = courseDAO.insert(conn, new Course(dept.getId(), "CS101", "Data Structures", 4, "1", 60));
            course2 = courseDAO.insert(conn, new Course(dept.getId(), "CS102", "Algorithms", 4, "1", 60));

            // Assign Course 1 to Faculty 1
            assignmentDAO.insert(conn, new CourseAssignment(course1.getId(), faculty1.getId(), "2026-2027"));

            // Enrollments for Course 1
            Enrollment e1 = new Enrollment(student1.getId(), course1.getId(), "2026-2027");
            e1.setStatus("ACTIVE");
            enr1 = enrollmentDAO.insert(conn, e1);

            Enrollment e2 = new Enrollment(student2.getId(), course1.getId(), "2026-2027");
            e2.setStatus("ACTIVE");
            enr2 = enrollmentDAO.insert(conn, e2);

            Enrollment e3 = new Enrollment(student3.getId(), course1.getId(), "2026-2027");
            e3.setStatus("ACTIVE");
            enr3 = enrollmentDAO.insert(conn, e3);
        }
    }

    // =========================================================================
    // 1. SESSION LIFECYCLE & SECURITY TESTS
    // =========================================================================

    @Test
    void testSessionCreationSuccess() {
        AttendanceSession session = sessionService.openSession(facultyUser1, course1.getId(), "2026-2027", 10, "Lecture 1");

        assertNotNull(session);
        assertTrue(session.getId() > 0);
        assertEquals(course1.getId(), session.getCourseId());
        assertEquals(faculty1.getId(), session.getFacultyId());
        assertEquals("OPEN", session.getStatus());
        assertEquals("Lecture 1", session.getSessionNumber());
        assertEquals(LocalDate.now().toString(), session.getSessionDate());

        // 6-digit numeric verification code preserving leading zeroes
        assertNotNull(session.getSessionCode());
        assertTrue(session.getSessionCode().matches("^[0-9]{6}$"), "Code must be exactly 6 numeric digits: " + session.getSessionCode());

        // Expiry timestamp check
        assertNotNull(session.getExpiresAt());
        Instant expiry = Instant.parse(session.getExpiresAt());
        assertTrue(expiry.isAfter(Instant.now().plus(Duration.ofMinutes(8))));
    }

    @Test
    void testSessionCreationUnauthorizedFacultyRejected() {
        // Faculty 2 is not assigned to Course 1
        ValidationException ex = assertThrows(ValidationException.class, () ->
                sessionService.openSession(facultyUser2, course1.getId(), "2026-2027", 10, "1"));
        assertTrue(ex.getMessage().contains("not assigned"), "Must reject unassigned faculty");
    }

    @Test
    void testDuplicateOpenSessionRejection() {
        sessionService.openSession(facultyUser1, course1.getId(), "2026-2027", 10, "Lecture 1");

        // Attempting to open a second concurrent open session for the same course today
        ValidationException ex = assertThrows(ValidationException.class, () ->
                sessionService.openSession(facultyUser1, course1.getId(), "2026-2027", 10, "Lecture 2"));
        assertTrue(ex.getMessage().contains("already OPEN"), "Must reject concurrent duplicate open sessions");
    }

    @Test
    void testCodeRegenerationSuccess() {
        AttendanceSession session = sessionService.openSession(facultyUser1, course1.getId(), "2026-2027", 10);
        String initialCode = session.getSessionCode();

        String newCode = sessionService.regenerateCode(facultyUser1, session.getId());

        assertNotNull(newCode);
        assertTrue(newCode.matches("^[0-9]{6}$"));
        assertNotEquals(initialCode, newCode, "Regenerated code must differ from initial code");

        AttendanceSession reloaded = sessionDAO.findById(session.getId());
        assertEquals(newCode, reloaded.getSessionCode());

        // Verification: Previous code is completely invalidated
        assertNull(sessionDAO.findOpenSessionByCode(initialCode), "Old code must no longer match any active session");
        assertNotNull(sessionDAO.findOpenSessionByCode(newCode));
    }

    @Test
    void testCodeRegenerationProhibitedWhenFinalized() {
        AttendanceSession session = sessionService.openSession(facultyUser1, course1.getId(), "2026-2027", 10);
        sessionService.finalizeSession(facultyUser1, session.getId());

        ValidationException ex = assertThrows(ValidationException.class, () ->
                sessionService.regenerateCode(facultyUser1, session.getId()));
        assertTrue(ex.getMessage().contains("FINALIZED"), "Regeneration must be rejected after finalization");
    }

    @Test
    void testCodeRegenerationProhibitedWhenClosed() {
        AttendanceSession session = sessionService.openSession(facultyUser1, course1.getId(), "2026-2027", 10);
        sessionService.closeSession(facultyUser1, session.getId());

        ValidationException ex = assertThrows(ValidationException.class, () ->
                sessionService.regenerateCode(facultyUser1, session.getId()));
        assertTrue(ex.getMessage().contains("CLOSED"), "Regeneration must be rejected after closure");
    }

    @Test
    void testCodeRegenerationProhibitedWhenExpired() throws Exception {
        AttendanceSession session = sessionService.openSession(facultyUser1, course1.getId(), "2026-2027", 10);
        // Set expired timestamp directly in DB
        String pastExpiry = Instant.now().minus(Duration.ofMinutes(15)).toString();
        try (Connection conn = TestDBHelper.getTestConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE attendance_sessions SET expires_at = '" + pastExpiry + "' WHERE id = " + session.getId());
        }

        ValidationException ex = assertThrows(ValidationException.class, () ->
                sessionService.regenerateCode(facultyUser1, session.getId()));
        assertTrue(ex.getMessage().contains("expired"), "Regeneration must be rejected on expired sessions");
    }

    // =========================================================================
    // 2. STUDENT SELF-SERVICE CHECK-IN TESTS
    // =========================================================================

    @Test
    void testStudentCheckInSuccess() {
        AttendanceSession session = sessionService.openSession(facultyUser1, course1.getId(), "2026-2027", 10);

        AttendanceSubmission sub = sessionService.checkIn(studentUser1, session.getSessionCode());

        assertNotNull(sub);
        assertEquals(session.getId(), sub.getSessionId());
        assertEquals(student1.getId(), sub.getStudentId());
        assertEquals(enr1.getId(), sub.getEnrollmentId());
        assertEquals("ACCEPTED", sub.getStatus());

        // Check-in recorded, but attendance ledger NOT yet marked PRESENT (pending finalization)
        List<Attendance> ledger = attendanceDAO.findByEnrollment(enr1.getId());
        assertTrue(ledger.isEmpty(), "Attendance ledger should remain uncommitted until faculty finalizes");
    }

    @Test
    void testStudentCheckInIncorrectCode() {
        AttendanceSession session = sessionService.openSession(facultyUser1, course1.getId(), "2026-2027", 10);

        ValidationException ex = assertThrows(ValidationException.class, () ->
                sessionService.checkInToSession(studentUser1, session.getId(), "999999"));
        assertTrue(ex.getMessage().contains("INVALID CODE"));
    }

    @Test
    void testStudentCheckInRateLimitingMaxAttempts() {
        AttendanceSession session = sessionService.openSession(facultyUser1, course1.getId(), "2026-2027", 10);

        // 5 consecutive invalid code submissions
        for (int i = 0; i < 5; i++) {
            final String wrongCode = "11111" + i;
            assertThrows(ValidationException.class, () ->
                    sessionService.checkInToSession(studentUser1, session.getId(), wrongCode));
        }

        assertEquals(5, sessionDAO.getFailedAttempts(session.getId(), student1.getId()));

        // 6th attempt must be blocked by rate-limiting even if code is correct!
        ValidationException rateLimitEx = assertThrows(ValidationException.class, () ->
                sessionService.checkInToSession(studentUser1, session.getId(), session.getSessionCode()));
        assertTrue(rateLimitEx.getMessage().contains("MAXIMUM ATTEMPTS EXCEEDED"));
    }

    @Test
    void testStudentCheckInExpiredSession() throws Exception {
        AttendanceSession session = sessionService.openSession(facultyUser1, course1.getId(), "2026-2027", 10);
        String pastExpiry = Instant.now().minus(Duration.ofMinutes(5)).toString();
        try (Connection conn = TestDBHelper.getTestConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE attendance_sessions SET expires_at = '" + pastExpiry + "' WHERE id = " + session.getId());
        }

        ValidationException ex = assertThrows(ValidationException.class, () ->
                sessionService.checkIn(studentUser1, session.getSessionCode()));
        assertTrue(ex.getMessage().contains("SESSION EXPIRED"));
    }

    @Test
    void testStudentCheckInClosedSession() {
        AttendanceSession session = sessionService.openSession(facultyUser1, course1.getId(), "2026-2027", 10);
        sessionService.closeSession(facultyUser1, session.getId());

        ValidationException ex = assertThrows(ValidationException.class, () ->
                sessionService.checkInToSession(studentUser1, session.getId(), session.getSessionCode()));
        assertTrue(ex.getMessage().contains("SESSION CLOSED"));
    }

    @Test
    void testStudentCheckInDuplicateSubmissionPrevented() {
        AttendanceSession session = sessionService.openSession(facultyUser1, course1.getId(), "2026-2027", 10);

        sessionService.checkIn(studentUser1, session.getSessionCode());

        // Duplicate submission attempt
        ValidationException ex = assertThrows(ValidationException.class, () ->
                sessionService.checkIn(studentUser1, session.getSessionCode()));
        assertTrue(ex.getMessage().contains("ALREADY SUBMITTED"));
    }

    @Test
    void testStudentCheckInNonEnrolledStudentRejected() throws Exception {
        // Student 3 enrolled only in Course 1. Now open a session for Course 2
        try (Connection conn = TestDBHelper.getTestConnection()) {
            assignmentDAO.insert(conn, new CourseAssignment(course2.getId(), faculty1.getId(), "2026-2027"));
        }
        AttendanceSession session2 = sessionService.openSession(facultyUser1, course2.getId(), "2026-2027", 10);

        ValidationException ex = assertThrows(ValidationException.class, () ->
                sessionService.checkIn(studentUser1, session2.getSessionCode()));
        assertTrue(ex.getMessage().contains("NOT ENROLLED"));
    }

    @Test
    void testStudentCheckInInactiveAccountRejected() {
        AttendanceSession session = sessionService.openSession(facultyUser1, course1.getId(), "2026-2027", 10);

        // 1. Inactive user account is rejected by AuthService
        AuthenticationException authEx = assertThrows(AuthenticationException.class, () ->
                sessionService.checkIn(studentUserInactive, session.getSessionCode()));
        assertTrue(authEx.getMessage().toLowerCase().contains("inactive"));

        // 2. Inactive student profile is rejected by AttendanceSessionService
        student1.setActive(false);
        studentDAO.update(student1);
        ValidationException valEx = assertThrows(ValidationException.class, () ->
                sessionService.checkIn(studentUser1, session.getSessionCode()));
        assertTrue(valEx.getMessage().contains("ACCOUNT INACTIVE"));
    }

    // =========================================================================
    // 3. FACULTY FINALIZATION WORKFLOW & MANUAL OVERRIDES
    // =========================================================================

    @Test
    void testFinalizeSessionDefaultPresentAndAbsent() {
        AttendanceSession session = sessionService.openSession(facultyUser1, course1.getId(), "2026-2027", 10);

        // Student 1 and Student 2 check in; Student 3 does not check in
        sessionService.checkIn(studentUser1, session.getSessionCode());
        sessionService.checkIn(studentUser2, session.getSessionCode());

        int[] counts = sessionService.finalizeSession(facultyUser1, session.getId());

        assertEquals(2, counts[0], "2 students should be PRESENT");
        assertEquals(1, counts[1], "1 student should be ABSENT");
        assertEquals(0, counts[2], "0 students LATE");
        assertEquals(3, counts[3], "3 total enrolled students");

        // Verify official attendance ledger in DB
        List<Attendance> a1 = attendanceDAO.findByEnrollment(enr1.getId());
        assertEquals(1, a1.size());
        assertEquals("PRESENT", a1.get(0).getStatus());
        assertEquals("Self-Check-In Verified", a1.get(0).getRemarks());

        List<Attendance> a2 = attendanceDAO.findByEnrollment(enr2.getId());
        assertEquals("PRESENT", a2.get(0).getStatus());

        List<Attendance> a3 = attendanceDAO.findByEnrollment(enr3.getId());
        assertEquals(1, a3.size());
        assertEquals("ABSENT", a3.get(0).getStatus());
        assertEquals("Auto-Marked Absent (No Check-In)", a3.get(0).getRemarks());

        // Session status must be FINALIZED
        AttendanceSession finalized = sessionDAO.findById(session.getId());
        assertEquals("FINALIZED", finalized.getStatus());
        assertNotNull(finalized.getFinalizedAt());
    }

    @Test
    void testFinalizeSessionManualCorrectionsWithMandatoryRemarks() {
        AttendanceSession session = sessionService.openSession(facultyUser1, course1.getId(), "2026-2027", 10);

        // Student 1 checks in. Student 2 & 3 do not.
        sessionService.checkIn(studentUser1, session.getSessionCode());

        // Faculty reviews roster and manually grants attendance to Student 3 with valid medical remark
        Map<Integer, AttendanceCorrection> corrections = new HashMap<>();
        corrections.put(student3.getId(), new AttendanceCorrection(student3.getId(), enr3.getId(), "PRESENT", "Medical exemption approved by HOD"));

        int[] counts = sessionService.finalizeSession(facultyUser1, session.getId(), corrections);

        assertEquals(2, counts[0], "Student 1 and Student 3 PRESENT");
        assertEquals(1, counts[1], "Student 2 ABSENT");

        List<Attendance> a3 = attendanceDAO.findByEnrollment(enr3.getId());
        assertEquals("PRESENT", a3.get(0).getStatus());
        assertEquals("Medical exemption approved by HOD", a3.get(0).getRemarks());
    }

    @Test
    void testFinalizeSessionMissingMandatoryRemarksThrows() {
        AttendanceSession session = sessionService.openSession(facultyUser1, course1.getId(), "2026-2027", 10);

        // Student 3 did not check in (proposed ABSENT). Faculty tries to mark PRESENT with empty remarks
        Map<Integer, AttendanceCorrection> corrections = new HashMap<>();
        corrections.put(student3.getId(), new AttendanceCorrection(student3.getId(), enr3.getId(), "PRESENT", "   "));

        ValidationException ex = assertThrows(ValidationException.class, () ->
                sessionService.finalizeSession(facultyUser1, session.getId(), corrections));
        assertTrue(ex.getMessage().contains("Mandatory remarks/reason required"));
    }

    @Test
    void testFinalizeSessionIdempotency() {
        AttendanceSession session = sessionService.openSession(facultyUser1, course1.getId(), "2026-2027", 10);
        sessionService.finalizeSession(facultyUser1, session.getId());

        // Re-finalization must throw exception and not duplicate ledger rows
        ValidationException ex = assertThrows(ValidationException.class, () ->
                sessionService.finalizeSession(facultyUser1, session.getId()));
        assertTrue(ex.getMessage().contains("already finalized"));

        List<Attendance> a1 = attendanceDAO.findByEnrollment(enr1.getId());
        assertEquals(1, a1.size(), "Must not duplicate attendance ledger rows");
    }

    // =========================================================================
    // 4. ATTENDANCE PERCENTAGE & POLICY TESTS
    // =========================================================================

    @Test
    void testCalculatePercentageExact75() {
        // 3 PRESENT, 0 LATE, 1 ABSENT => 3/4 = 75.0%
        double pct = AttendanceService.calculatePercentage(3, 0, 4);
        assertEquals(75.0, pct, 0.01);
    }

    @Test
    void testCalculatePercentageAbove75() {
        // 4 PRESENT, 0 LATE, 1 ABSENT => 4/5 = 80.0%
        double pct = AttendanceService.calculatePercentage(4, 0, 5);
        assertEquals(80.0, pct, 0.01);
    }

    @Test
    void testCalculatePercentageBelow75Defaulter() {
        // 2 PRESENT, 0 LATE, 2 ABSENT => 2/4 = 50.0%
        double pct = AttendanceService.calculatePercentage(2, 0, 4);
        assertEquals(50.0, pct, 0.01);
    }

    @Test
    void testCalculatePercentageLateWeightingHalf() {
        // 2 PRESENT (2.0) + 2 LATE (1.0) = 3.0 out of 4 sessions => 75.0%
        double pct = AttendanceService.calculatePercentage(2, 2, 4);
        assertEquals(75.0, pct, 0.01);
    }

    @Test
    void testCalculatePercentageZeroSessionsNeutral() {
        // Zero sessions conducted => 0.0 without division by zero
        double pct = AttendanceService.calculatePercentage(0, 0, 0);
        assertEquals(0.0, pct, 0.01);

        double pctEmptyList = AttendanceService.calculatePercentage(List.of());
        assertEquals(0.0, pctEmptyList, 0.01);
    }

    // =========================================================================
    // 5. AUTHORIZATION BOUNDARY TESTS
    // =========================================================================

    @Test
    void testFacultyCannotFinalizeUnassignedCourseSession() throws Exception {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            assignmentDAO.insert(conn, new CourseAssignment(course2.getId(), faculty2.getId(), "2026-2027"));
        }
        AttendanceSession sessionCourse2 = sessionService.openSession(facultyUser2, course2.getId(), "2026-2027", 10);

        // Faculty 1 attempts to finalize session belonging to Course 2
        AuthenticationException ex = assertThrows(AuthenticationException.class, () ->
                sessionService.finalizeSession(facultyUser1, sessionCourse2.getId()));
        assertTrue(ex.getMessage().contains("assigned courses"));
    }

    @Test
    void testAdminHasSuperuserSessionAccess() {
        // Admin can open, view, and finalize sessions for any course
        AttendanceSession adminSession = sessionService.openSession(adminUser, course1.getId(), "2026-2027", 10);
        assertNotNull(adminSession);

        int[] counts = sessionService.finalizeSession(adminUser, adminSession.getId());
        assertEquals(3, counts[3]);
    }

    // =========================================================================
    // 6. DATABASE SCHEMA MIGRATION IDEMPOTENCY
    // =========================================================================

    @Test
    void testV8MigrationSchemaIdempotency() throws Exception {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            // Re-invoke DatabaseInitializer on existing DB
            DatabaseInitializer.initializeSchemaOnly(conn);

            // Verify attendance_sessions columns exist
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA table_info(attendance_sessions)")) {
                boolean hasSessionNumber = false;
                boolean hasFinalizedAt = false;
                boolean hasFinalizedBy = false;
                while (rs.next()) {
                    String col = rs.getString("name");
                    if ("session_number".equalsIgnoreCase(col)) hasSessionNumber = true;
                    if ("finalized_at".equalsIgnoreCase(col)) hasFinalizedAt = true;
                    if ("finalized_by".equalsIgnoreCase(col)) hasFinalizedBy = true;
                }
                assertTrue(hasSessionNumber);
                assertTrue(hasFinalizedAt);
                assertTrue(hasFinalizedBy);
            }

            // Verify attendance_failed_attempts table exists
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='attendance_failed_attempts'")) {
                assertTrue(rs.next(), "attendance_failed_attempts table must exist");
            }
        }
    }
}
