package com.college.service;

import com.college.TestDBHelper;
import com.college.dao.*;
import com.college.exception.AuthenticationException;
import com.college.exception.ValidationException;
import com.college.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [T20] Batch attendance, duplicate checks, ISO dates, & authorization tests for {@link AttendanceService}.
 */
public class AttendanceServiceTest {

    private static AttendanceService attendanceService;
    private static UserDAO userDAO;
    private static DepartmentDAO departmentDAO;
    private static FacultyDAO facultyDAO;
    private static StudentDAO studentDAO;
    private static CourseDAO courseDAO;
    private static CourseAssignmentDAO assignmentDAO;
    private static EnrollmentDAO enrollmentDAO;
    private static AttendanceDAO attendanceDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        attendanceService = new AttendanceService();
        userDAO = new UserDAO();
        departmentDAO = new DepartmentDAO();
        facultyDAO = new FacultyDAO();
        studentDAO = new StudentDAO();
        courseDAO = new CourseDAO();
        assignmentDAO = new CourseAssignmentDAO();
        enrollmentDAO = new EnrollmentDAO();
        attendanceDAO = new AttendanceDAO();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    @Test
    @DisplayName("Verify valid batch attendance marking across multiple students")
    void testValidBatchAttendance() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = userDAO.insert(conn, new User("admin_att", "$2a$12$hash", Role.ADMIN));
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS101", "Programming", 4, "1", 50));

            User u1 = userDAO.insert(conn, new User("s1_att", "$2a$12$hash", Role.STUDENT));
            Student s1 = studentDAO.insert(conn, new Student(u1.getId(), dept.getId(), "S_ATT1", "A", "B", "a@c.edu", "555", "2004-01-01", "2026-08-01", "1"));
            Enrollment enr1 = new Enrollment(s1.getId(), course.getId(), "2026-2027");
            enr1.setStatus("ACTIVE");
            Enrollment e1 = enrollmentDAO.insert(conn, enr1);

            User u2 = userDAO.insert(conn, new User("s2_att", "$2a$12$hash", Role.STUDENT));
            Student s2 = studentDAO.insert(conn, new Student(u2.getId(), dept.getId(), "S_ATT2", "C", "D", "b@c.edu", "555", "2004-01-01", "2026-08-01", "1"));
            Enrollment enr2 = new Enrollment(s2.getId(), course.getId(), "2026-2027");
            enr2.setStatus("ACTIVE");
            Enrollment e2 = enrollmentDAO.insert(conn, enr2);

            List<AttendanceService.AttendanceEntry> entries = new ArrayList<>();
            entries.add(new AttendanceService.AttendanceEntry(e1.getId(), "PRESENT", "On time"));
            entries.add(new AttendanceService.AttendanceEntry(e2.getId(), "LATE", "5 min"));

            assertDoesNotThrow(() -> attendanceService.markAttendanceBatch(admin, course.getId(), "2026-2027", "2026-09-10", entries));

            // Verify both attendance records persisted
            assertEquals(1, attendanceDAO.findByEnrollment(conn, e1.getId()).size());
            assertEquals(1, attendanceDAO.findByEnrollment(conn, e2.getId()).size());
        }
    }

    @Test
    @DisplayName("Verify duplicate enrollment ID in same batch is rejected in Pass 1")
    void testDuplicateEnrollmentInBatchRejected() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = userDAO.insert(conn, new User("admin_dup_att", "$2a$12$hash", Role.ADMIN));
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS102", "Programming", 4, "1", 50));

            User u1 = userDAO.insert(conn, new User("s1_dup", "$2a$12$hash", Role.STUDENT));
            Student s1 = studentDAO.insert(conn, new Student(u1.getId(), dept.getId(), "S_DUP1", "A", "B", "adup@c.edu", "555", "2004-01-01", "2026-08-01", "1"));
            Enrollment enr1 = new Enrollment(s1.getId(), course.getId(), "2026-2027");
            enr1.setStatus("ACTIVE");
            Enrollment e1 = enrollmentDAO.insert(conn, enr1);

            List<AttendanceService.AttendanceEntry> entries = new ArrayList<>();
            entries.add(new AttendanceService.AttendanceEntry(e1.getId(), "PRESENT", ""));
            entries.add(new AttendanceService.AttendanceEntry(e1.getId(), "ABSENT", "Duplicate!"));

            ValidationException ex = assertThrows(ValidationException.class,
                    () -> attendanceService.markAttendanceBatch(admin, course.getId(), "2026-2027", "2026-09-10", entries));
            assertTrue(ex.getMessage().contains("Duplicate enrollment ID detected in batch"));
        }
    }

    @Test
    @DisplayName("Special Regression Gap 6: Faculty attendance recording scoped to assigned courses (SEC-03)")
    void testFacultyAttendanceScopedToAssignedCourses() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course cAssigned = courseDAO.insert(conn, new Course(dept.getId(), "CS_ASG_ATT", "Course A", 4, "1", 50));
            Course cUnassigned = courseDAO.insert(conn, new Course(dept.getId(), "CS_UNASG_ATT", "Course B", 4, "1", 50));

            User facUser = userDAO.insert(conn, new User("fac_att_user", "$2a$12$hash", Role.FACULTY));
            Faculty faculty = facultyDAO.insert(conn, new Faculty(facUser.getId(), dept.getId(), "FAC_ATT", "Alan", "T", "alan@c.edu", "555", "AI", "2020-01-01"));

            // Assign faculty to cAssigned
            assignmentDAO.insert(conn, new CourseAssignment(cAssigned.getId(), faculty.getId(), "2026-2027"));

            User u1 = userDAO.insert(conn, new User("stu_att_x", "$2a$12$hash", Role.STUDENT));
            Student s1 = studentDAO.insert(conn, new Student(u1.getId(), dept.getId(), "S_ATT_X", "A", "B", "ax@c.edu", "555", "2004-01-01", "2026-08-01", "1"));
            Enrollment enrA = new Enrollment(s1.getId(), cAssigned.getId(), "2026-2027");
            enrA.setStatus("ACTIVE");
            Enrollment enrAssigned = enrollmentDAO.insert(conn, enrA);

            Enrollment enrU = new Enrollment(s1.getId(), cUnassigned.getId(), "2026-2027");
            enrU.setStatus("ACTIVE");
            Enrollment enrUnassigned = enrollmentDAO.insert(conn, enrU);

            // 1. Assigned course succeeds
            List<AttendanceService.AttendanceEntry> entries1 = List.of(new AttendanceService.AttendanceEntry(enrAssigned.getId(), "PRESENT", ""));
            assertDoesNotThrow(() -> attendanceService.markAttendanceBatch(facUser, cAssigned.getId(), "2026-2027", "2026-09-15", entries1));

            // 2. Unassigned course fails
            List<AttendanceService.AttendanceEntry> entries2 = List.of(new AttendanceService.AttendanceEntry(enrUnassigned.getId(), "PRESENT", ""));
            assertThrows(Exception.class, () -> attendanceService.markAttendanceBatch(facUser, cUnassigned.getId(), "2026-2027", "2026-09-15", entries2));
        }
    }

    @Test
    @DisplayName("Verify atomic transaction rollback on partial failure in batch attendance")
    void testBatchAttendanceRollbackOnPartialFailure() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = userDAO.insert(conn, new User("admin_roll_att", "$2a$12$hash", Role.ADMIN));
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS_ROLL_ATT", "Course", 4, "1", 50));

            User u1 = userDAO.insert(conn, new User("stu_r1", "$2a$12$hash", Role.STUDENT));
            Student s1 = studentDAO.insert(conn, new Student(u1.getId(), dept.getId(), "S_R1", "A", "B", "ar1@c.edu", "555", "2004-01-01", "2026-08-01", "1"));
            Enrollment enrV = new Enrollment(s1.getId(), course.getId(), "2026-2027");
            enrV.setStatus("ACTIVE");
            Enrollment validEnr = enrollmentDAO.insert(conn, enrV);

            List<AttendanceService.AttendanceEntry> entries = new ArrayList<>();
            entries.add(new AttendanceService.AttendanceEntry(validEnr.getId(), "PRESENT", ""));
            // Second entry has non-existent enrollment ID 99999
            entries.add(new AttendanceService.AttendanceEntry(99999, "PRESENT", ""));

            assertThrows(ValidationException.class,
                    () -> attendanceService.markAttendanceBatch(admin, course.getId(), "2026-2027", "2026-09-18", entries));

            // Verify first entry was rolled back and does not persist
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM attendance")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Batch must roll back completely on invalid enrollment ID");
            }
        }
    }
}
