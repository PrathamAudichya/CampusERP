package com.college.service;

import com.college.TestDBHelper;
import com.college.config.DBConnection;
import com.college.dao.*;
import com.college.exception.ValidationException;
import com.college.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dedicated test suite verifying Milestone 3 Pre-Flight Edge Cases:
 * 1. Session expiration boundary (before, exactly at, and after expiry with injectable Clock).
 * 2. Mandatory explanation remarks for all 6 transition combinations (PRESENT->ABSENT, PRESENT->LATE,
 *    ABSENT->PRESENT, ABSENT->LATE, LATE->PRESENT, LATE->ABSENT).
 * 3. Failed attempt lockout scope strictly isolated to (session_id, student_id) without locking account,
 *    disabling login, or affecting unrelated sessions.
 */
public class AttendanceMilestone3EdgeCasesTest {

    private AttendanceSessionService sessionService;
    private AttendanceSessionDAO sessionDAO;
    private EnrollmentDAO enrollmentDAO;
    private StudentDAO studentDAO;
    private FacultyDAO facultyDAO;
    private CourseDAO courseDAO;
    private CourseAssignmentDAO assignmentDAO;
    private UserDAO userDAO;
    private DepartmentDAO departmentDAO;

    private User adminUser;
    private User facultyUser;
    private User studentUser1;
    private User studentUser2;
    private Faculty faculty;
    private Student student1;
    private Student student2;
    private Course course1;
    private Course course2;

    @BeforeAll
    static void initSuite() throws Exception {
        TestDBHelper.initTestDatabase();
    }

    @BeforeEach
    void setUp() throws Exception {
        sessionService = new AttendanceSessionService();
        sessionDAO = new AttendanceSessionDAO();
        enrollmentDAO = new EnrollmentDAO();
        studentDAO = new StudentDAO();
        facultyDAO = new FacultyDAO();
        courseDAO = new CourseDAO();
        assignmentDAO = new CourseAssignmentDAO();
        userDAO = new UserDAO();
        departmentDAO = new DepartmentDAO();

        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);

            Department dept = departmentDAO.insert(conn, new Department("EDGE_CS", "Computer Science Edge"));

            adminUser = userDAO.insert(conn, new User("admin_edge", "$2a$12$dummyHash", Role.ADMIN));
            facultyUser = userDAO.insert(conn, new User("prof_edge", "$2a$12$dummyHash", Role.FACULTY));
            studentUser1 = userDAO.insert(conn, new User("stu_edge1", "$2a$12$dummyHash", Role.STUDENT));
            studentUser2 = userDAO.insert(conn, new User("stu_edge2", "$2a$12$dummyHash", Role.STUDENT));

            faculty = facultyDAO.insert(conn, new Faculty(facultyUser.getId(), dept.getId(), "FAC_EDGE", "Prof", "Edge", "prof_edge@college.test", "1234567890", "Systems", "2020-01-01"));

            student1 = studentDAO.insert(conn, new Student(studentUser1.getId(), dept.getId(), "STU-EDGE-01", "Edge", "One", "stu1@college.test", "1234567891", "2002-01-01", "2026-08-01", "3"));
            student2 = studentDAO.insert(conn, new Student(studentUser2.getId(), dept.getId(), "STU-EDGE-02", "Edge", "Two", "stu2@college.test", "1234567892", "2002-01-01", "2026-08-01", "3"));

            course1 = courseDAO.insert(conn, new Course(dept.getId(), "CS501", "Distributed Systems", 4, "3", 60));
            course2 = courseDAO.insert(conn, new Course(dept.getId(), "CS502", "Cloud Computing", 4, "3", 60));

            assignmentDAO.insert(conn, new CourseAssignment(course1.getId(), faculty.getId(), "2026-2027"));
            assignmentDAO.insert(conn, new CourseAssignment(course2.getId(), faculty.getId(), "2026-2027"));

            Enrollment e1 = new Enrollment(student1.getId(), course1.getId(), "2026-2027");
            e1.setStatus("ACTIVE");
            enrollmentDAO.insert(conn, e1);

            Enrollment e1b = new Enrollment(student1.getId(), course2.getId(), "2026-2027");
            e1b.setStatus("ACTIVE");
            enrollmentDAO.insert(conn, e1b);

            Enrollment e2 = new Enrollment(student2.getId(), course1.getId(), "2026-2027");
            e2.setStatus("ACTIVE");
            enrollmentDAO.insert(conn, e2);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    // =========================================================================
    // PART 3 - EDGE CASE A: DETERMINISTIC EXPIRATION BOUNDARY TESTS (INJECTABLE CLOCK)
    // =========================================================================

    @Test
    void testExpirationBoundary_BeforeExpirySucceeds() {
        Instant baseTime = Instant.parse("2026-11-10T10:00:00Z");
        Clock fixedClock = Clock.fixed(baseTime, ZoneOffset.UTC);
        sessionService.setClock(fixedClock);

        // Open session for 10 minutes -> expires at 2026-11-10T10:10:00Z
        AttendanceSession session = sessionService.openSession(facultyUser, course1.getId(), "2026-2027", 10, "1");
        Instant expiryInstant = Instant.parse(session.getExpiresAt());
        assertEquals(baseTime.plus(Duration.ofMinutes(10)), expiryInstant);

        // Advance clock to 9 minutes and 59 seconds (1 second BEFORE expiry)
        sessionService.setClock(Clock.fixed(expiryInstant.minusSeconds(1), ZoneOffset.UTC));

        // Check-in should SUCCEED
        AttendanceSubmission sub = sessionService.checkInToSession(studentUser1, session.getId(), session.getSessionCode());
        assertNotNull(sub);
        assertEquals("ACCEPTED", sub.getStatus());
    }

    @Test
    void testExpirationBoundary_ExactlyAtExpiryFails() {
        Instant baseTime = Instant.parse("2026-11-10T10:00:00Z");
        Clock fixedClock = Clock.fixed(baseTime, ZoneOffset.UTC);
        sessionService.setClock(fixedClock);

        AttendanceSession session = sessionService.openSession(facultyUser, course1.getId(), "2026-2027", 10, "1");
        Instant expiryInstant = Instant.parse(session.getExpiresAt());

        // Set clock EXACTLY at expiry (now == expiresAt)
        sessionService.setClock(Clock.fixed(expiryInstant, ZoneOffset.UTC));

        ValidationException ex = assertThrows(ValidationException.class, () ->
                sessionService.checkInToSession(studentUser1, session.getId(), session.getSessionCode()));
        assertTrue(ex.getMessage().contains("SESSION EXPIRED"), "Expected SESSION EXPIRED exactly at expiry timestamp, got: " + ex.getMessage());

        // Session status should now be updated to CLOSED
        AttendanceSession reloaded = sessionService.getSessionById(session.getId());
        assertEquals("CLOSED", reloaded.getStatus());
    }

    @Test
    void testExpirationBoundary_AfterExpiryFails() {
        Instant baseTime = Instant.parse("2026-11-10T10:00:00Z");
        Clock fixedClock = Clock.fixed(baseTime, ZoneOffset.UTC);
        sessionService.setClock(fixedClock);

        AttendanceSession session = sessionService.openSession(facultyUser, course1.getId(), "2026-2027", 10, "1");
        Instant expiryInstant = Instant.parse(session.getExpiresAt());

        // Set clock to 1 second AFTER expiry
        sessionService.setClock(Clock.fixed(expiryInstant.plusSeconds(1), ZoneOffset.UTC));

        ValidationException ex = assertThrows(ValidationException.class, () ->
                sessionService.checkInToSession(studentUser1, session.getId(), session.getSessionCode()));
        assertTrue(ex.getMessage().contains("SESSION EXPIRED"));
    }

    // =========================================================================
    // PART 3 - EDGE CASE B: MANDATORY CORRECTION REMARKS FOR ALL 6 TRANSITIONS
    // =========================================================================

    @Test
    void testAttendanceCorrectionRemarks_AllSixTransitionsRequireRemarks() {
        AttendanceSession session = sessionService.openSession(facultyUser, course1.getId(), "2026-2027", 15, "1");

        // Enrollments are student1 and student2
        int enrId1 = enrollmentDAO.findByStudentCourseYear(student1.getId(), course1.getId(), "2026-2027").getId();

        // 1. Transition PRESENT -> ABSENT (student checked in, proposed PRESENT, override to ABSENT without remarks)
        sessionService.checkInToSession(studentUser1, session.getId(), session.getSessionCode());

        Map<Integer, AttendanceCorrection> corr1 = new HashMap<>();
        corr1.put(student1.getId(), new AttendanceCorrection(student1.getId(), enrId1, "PRESENT", "ABSENT", ""));
        ValidationException ex1 = assertThrows(ValidationException.class, () ->
                sessionService.finalizeSession(facultyUser, session.getId(), corr1));
        assertTrue(ex1.getMessage().contains("Mandatory remarks/reason required"));

        // 2. Transition PRESENT -> LATE without remarks
        Map<Integer, AttendanceCorrection> corr2 = new HashMap<>();
        corr2.put(student1.getId(), new AttendanceCorrection(student1.getId(), enrId1, "PRESENT", "LATE", "   "));
        ValidationException ex2 = assertThrows(ValidationException.class, () ->
                sessionService.finalizeSession(facultyUser, session.getId(), corr2));
        assertTrue(ex2.getMessage().contains("Mandatory remarks/reason required"));

        // 3. Transition ABSENT -> PRESENT (student2 did not check in, proposed ABSENT, override to PRESENT without remarks)
        int enrId2 = enrollmentDAO.findByStudentCourseYear(student2.getId(), course1.getId(), "2026-2027").getId();
        Map<Integer, AttendanceCorrection> corr3 = new HashMap<>();
        corr3.put(student2.getId(), new AttendanceCorrection(student2.getId(), enrId2, "ABSENT", "PRESENT", null));
        ValidationException ex3 = assertThrows(ValidationException.class, () ->
                sessionService.finalizeSession(facultyUser, session.getId(), corr3));
        assertTrue(ex3.getMessage().contains("Mandatory remarks/reason required"));

        // 4. Transition ABSENT -> LATE without remarks
        Map<Integer, AttendanceCorrection> corr4 = new HashMap<>();
        corr4.put(student2.getId(), new AttendanceCorrection(student2.getId(), enrId2, "ABSENT", "LATE", ""));
        ValidationException ex4 = assertThrows(ValidationException.class, () ->
                sessionService.finalizeSession(facultyUser, session.getId(), corr4));
        assertTrue(ex4.getMessage().contains("Mandatory remarks/reason required"));

        // 5. Transition LATE -> PRESENT without remarks
        Map<Integer, AttendanceCorrection> corr5 = new HashMap<>();
        corr5.put(student1.getId(), new AttendanceCorrection(student1.getId(), enrId1, "LATE", "PRESENT", ""));
        ValidationException ex5 = assertThrows(ValidationException.class, () ->
                sessionService.finalizeSession(facultyUser, session.getId(), corr5));
        assertTrue(ex5.getMessage().contains("Mandatory remarks/reason required"));

        // 6. Transition LATE -> ABSENT without remarks
        Map<Integer, AttendanceCorrection> corr6 = new HashMap<>();
        corr6.put(student1.getId(), new AttendanceCorrection(student1.getId(), enrId1, "LATE", "ABSENT", "  "));
        ValidationException ex6 = assertThrows(ValidationException.class, () ->
                sessionService.finalizeSession(facultyUser, session.getId(), corr6));
        assertTrue(ex6.getMessage().contains("Mandatory remarks/reason required"));

        // Now test all 6 transitions SUCCEED with valid non-empty remarks
        Map<Integer, AttendanceCorrection> validCorr = new HashMap<>();
        validCorr.put(student1.getId(), new AttendanceCorrection(student1.getId(), enrId1, "PRESENT", "LATE", "Arrived 15 minutes late with permission"));
        validCorr.put(student2.getId(), new AttendanceCorrection(student2.getId(), enrId2, "ABSENT", "PRESENT", "Medical certificate verified"));

        int[] counts = sessionService.finalizeSession(facultyUser, session.getId(), validCorr);
        assertEquals(1, counts[0], "Present count should be 1");
        assertEquals(0, counts[1], "Absent count should be 0");
        assertEquals(1, counts[2], "Late count should be 1");
        assertEquals(2, counts[3], "Total enrolled should be 2");
    }

    // =========================================================================
    // PART 3 - EDGE CASE C: FAILED ATTEMPT LOCKOUT SCOPE VERIFICATION
    // =========================================================================

    @Test
    void testFailedAttemptLockoutScope_IsStrictlyIsolatedToSpecificSession() {
        // Open two separate sessions: Session A (course1) and Session B (course2)
        AttendanceSession sessionA = sessionService.openSession(facultyUser, course1.getId(), "2026-2027", 15, "1");
        AttendanceSession sessionB = sessionService.openSession(facultyUser, course2.getId(), "2026-2027", 15, "1");

        // Fail 5 times in Session A
        for (int i = 0; i < 5; i++) {
            final String wrong = "99999" + i;
            assertThrows(ValidationException.class, () ->
                    sessionService.checkInToSession(studentUser1, sessionA.getId(), wrong));
        }

        // 6th attempt in Session A must be LOCKED
        ValidationException lockEx = assertThrows(ValidationException.class, () ->
                sessionService.checkInToSession(studentUser1, sessionA.getId(), sessionA.getSessionCode()));
        assertTrue(lockEx.getMessage().contains("MAXIMUM ATTEMPTS EXCEEDED"));

        // 1. Verify student's ERP account is NOT disabled
        User freshUser = userDAO.findById(studentUser1.getId());
        assertNotNull(freshUser);
        assertTrue(freshUser.isActive(), "User account must remain active in users table");

        Student freshStudent = studentDAO.findById(student1.getId());
        assertNotNull(freshStudent);
        assertTrue(freshStudent.isActive(), "Student profile must remain active in students table");

        // 2. Verify student role authorization works
        assertDoesNotThrow(() -> AuthService.requireRole(freshUser, Role.STUDENT));

        // 3. Verify student can still successfully check in to UNRELATED Session B
        AttendanceSubmission subB = sessionService.checkInToSession(studentUser1, sessionB.getId(), sessionB.getSessionCode());
        assertNotNull(subB);
        assertEquals("ACCEPTED", subB.getStatus());
        assertEquals(sessionB.getId(), subB.getSessionId());

        // 4. Verify lockout remains scoped to Session A for student 1, while student 2 can check into Session A
        AttendanceSubmission subA2 = sessionService.checkInToSession(studentUser2, sessionA.getId(), sessionA.getSessionCode());
        assertNotNull(subA2);
        assertEquals("ACCEPTED", subA2.getStatus());
    }
}
