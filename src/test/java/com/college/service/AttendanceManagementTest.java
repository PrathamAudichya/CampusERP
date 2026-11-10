package com.college.service;

import com.college.TestDBHelper;
import com.college.dao.AttendanceDAO;
import com.college.dao.CourseAssignmentDAO;
import com.college.dao.CourseDAO;
import com.college.dao.DepartmentDAO;
import com.college.dao.EnrollmentDAO;
import com.college.dao.FacultyDAO;
import com.college.dao.StudentDAO;
import com.college.dao.UserDAO;
import com.college.model.Attendance;
import com.college.model.Course;
import com.college.model.CourseAssignment;
import com.college.model.Department;
import com.college.model.Enrollment;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Attendance management enhancements: 6-digit session codes, DOB verification, and batch marking.
 */
public class AttendanceManagementTest {

    private static AttendanceService attendanceService;
    private static AttendanceDAO attendanceDAO;
    private static EnrollmentDAO enrollmentDAO;
    private static CourseDAO courseDAO;
    private static CourseAssignmentDAO courseAssignmentDAO;
    private static FacultyDAO facultyDAO;
    private static StudentDAO studentDAO;
    private static DepartmentDAO departmentDAO;
    private static UserDAO userDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        attendanceService = new AttendanceService();
        attendanceDAO = new AttendanceDAO();
        enrollmentDAO = new EnrollmentDAO();
        courseDAO = new CourseDAO();
        courseAssignmentDAO = new CourseAssignmentDAO();
        facultyDAO = new FacultyDAO();
        studentDAO = new StudentDAO();
        departmentDAO = new DepartmentDAO();
        userDAO = new UserDAO();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    @Test
    @DisplayName("Verify 6-digit session verification code generation")
    void testSessionCodeGeneration() {
        for (int i = 0; i < 50; i++) {
            String code = attendanceService.generateSessionCode();
            assertNotNull(code);
            assertEquals(6, code.length());
            int num = Integer.parseInt(code);
            assertTrue(num >= 100000 && num <= 999999);
        }
    }

    @Test
    @DisplayName("Verify batch attendance marking persists session verification code in database")
    void testBatchAttendanceWithSessionCode() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            // Admin user
            User admin = new User("admin_att", "$2a$12$hash", Role.ADMIN);
            admin.setActive(true);
            admin = userDAO.insert(conn, admin);

            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS101", "Data Structures", 4, "1", 60));

            // Student and Enrollment
            User uStudent = userDAO.insert(conn, new User("stu_test", "$2a$12$hash", Role.STUDENT));
            Student student = new Student();
            student.setUserId(uStudent.getId());
            student.setDepartmentId(dept.getId());
            student.setStudentId("STU2026101");
            student.setFirstName("John");
            student.setLastName("Smith");
            student.setEmail("jsmith@college.edu");
            student.setPhone("555-1234");
            student.setSemester("1");
            student.setDateOfBirth("2005-06-15");
            student.setAdmissionDate("2026-08-01");
            student = studentDAO.insert(conn, student);

            Enrollment enrollment = enrollmentDAO.insert(conn, new Enrollment(student.getId(), course.getId(), "2026-2027"));

            String sessionCode = "584920";
            List<AttendanceService.AttendanceEntry> entries = List.of(
                    new AttendanceService.AttendanceEntry(enrollment.getId(), "PRESENT", "On time")
            );

            attendanceService.markAttendanceBatch(admin, course.getId(), "2026-2027", "2026-09-10", entries, sessionCode);

            // Verify attendance record
            List<Attendance> records = attendanceDAO.findByEnrollment(conn, enrollment.getId());
            assertEquals(1, records.size());
            Attendance a = records.get(0);
            assertEquals("PRESENT", a.getStatus());
            assertEquals("584920", a.getSessionCode(), "Session code must be persisted and retrieved from database");
        }
    }

    @Test
    @DisplayName("Verify student Date of Birth validation without displaying or exposing DOB")
    void testVerifyStudentDob() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("EC", "Electronics"));
            User uStudent = userDAO.insert(conn, new User("stu_dob", "$2a$12$hash", Role.STUDENT));
            Student student = new Student();
            student.setUserId(uStudent.getId());
            student.setDepartmentId(dept.getId());
            student.setStudentId("STU2026202");
            student.setFirstName("Marie");
            student.setLastName("Curie");
            student.setEmail("mcurie@college.edu");
            student.setPhone("555-5678");
            student.setSemester("1");
            student.setDateOfBirth("2005-11-07");
            student.setAdmissionDate("2026-08-01");
            student = studentDAO.insert(conn, student);

            assertTrue(attendanceService.verifyStudentDob(student.getId(), "2005-11-07"));
            assertTrue(attendanceService.verifyStudentDob(student.getId(), " 2005-11-07 ")); // with trim
            assertFalse(attendanceService.verifyStudentDob(student.getId(), "2005-11-08"));
            assertFalse(attendanceService.verifyStudentDob(student.getId(), "1999-01-01"));
            assertFalse(attendanceService.verifyStudentDob(student.getId(), ""));
            assertFalse(attendanceService.verifyStudentDob(student.getId(), null));
        }
    }

    @Test
    @DisplayName("Verify attendance percentage calculation with LATE weight and 75% threshold")
    void testAttendanceThresholdPolicy() {
        Attendance a1 = new Attendance(1, "2026-09-01", "PRESENT", "");
        Attendance a2 = new Attendance(1, "2026-09-02", "PRESENT", "");
        Attendance a3 = new Attendance(1, "2026-09-03", "LATE", "");   // 0.5
        Attendance a4 = new Attendance(1, "2026-09-04", "ABSENT", ""); // 0

        // 4 sessions, score = 1 + 1 + 0.5 + 0 = 2.5 / 4 = 62.5%
        double pct = AttendanceService.calculatePercentage(List.of(a1, a2, a3, a4));
        assertEquals(62.5, pct, 0.01);
        assertFalse(pct >= 75.0, "Student must be flagged as below 75% threshold");

        // Add 2 more PRESENT sessions: 4.5 / 6 = 75.0%
        Attendance a5 = new Attendance(1, "2026-09-05", "PRESENT", "");
        Attendance a6 = new Attendance(1, "2026-09-06", "PRESENT", "");
        double pct2 = AttendanceService.calculatePercentage(List.of(a1, a2, a3, a4, a5, a6));
        assertEquals(75.0, pct2, 0.01);
        assertTrue(pct2 >= 75.0, "Student must meet 75% threshold");
    }
}
