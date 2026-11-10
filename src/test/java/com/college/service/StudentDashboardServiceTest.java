package com.college.service;

import com.college.TestDBHelper;
import com.college.dao.*;
import com.college.exception.AuthenticationException;
import com.college.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [T23] Student personal dashboard isolation & authorization tests for {@link StudentDashboardService}.
 */
public class StudentDashboardServiceTest {

    private static StudentDashboardService dashboardService;
    private static UserDAO userDAO;
    private static DepartmentDAO departmentDAO;
    private static StudentDAO studentDAO;
    private static CourseDAO courseDAO;
    private static EnrollmentDAO enrollmentDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        dashboardService = new StudentDashboardService();
        userDAO = new UserDAO();
        departmentDAO = new DepartmentDAO();
        studentDAO = new StudentDAO();
        courseDAO = new CourseDAO();
        enrollmentDAO = new EnrollmentDAO();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    @Test
    @DisplayName("Verify student dashboard successfully assembles data for authenticated student")
    void testStudentDashboardDataAssembly() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS101", "Intro", 4, "1", 50));

            User stuUser = userDAO.insert(conn, new User("stu_dash1", "$2a$12$hash", Role.STUDENT));
            Student student = studentDAO.insert(conn, new Student(stuUser.getId(), dept.getId(), "S_DASH1", "A", "B", "adash@col.edu", "555", "2004-01-01", "2026-08-01", "1"));
            Enrollment enr = new Enrollment(student.getId(), course.getId(), "2026-2027");
            enr.setStatus("ACTIVE");
            enrollmentDAO.insert(conn, enr);

            StudentDashboardService.StudentDashboardData data = dashboardService.getDashboardData(stuUser, "2026-2027");
            assertNotNull(data);
            assertEquals("S_DASH1", data.getStudentProfile().getStudentId());
            assertEquals(1, data.getEnrollments().size());
            assertEquals("2026-2027", data.getAcademicYear());
        }
    }

    @Test
    @DisplayName("Special Regression Gap 8 & 9: Forced password change blocks student dashboard assembly")
    void testForcedPasswordChangeBlocksDashboard() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            User forcedUser = new User("stu_forced", "$2a$12$hash", Role.STUDENT);
            forcedUser.setMustChangePassword(true);
            userDAO.insert(conn, forcedUser);
            studentDAO.insert(conn, new Student(forcedUser.getId(), dept.getId(), "S_FORCED", "F", "U", "f@c.edu", "555", "2004-01-01", "2026-08-01", "1"));

            assertThrows(AuthenticationException.class,
                    () -> dashboardService.getDashboardData(forcedUser, "2026-2027"),
                    "Dashboard must be blocked if student must change password");
        }
    }

    @Test
    @DisplayName("Verify non-student roles cannot access student dashboard")
    void testNonStudentRoleRejected() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User faculty = userDAO.insert(conn, new User("fac_no_stdash", "$2a$12$hash", Role.FACULTY));
            assertThrows(AuthenticationException.class,
                    () -> dashboardService.getDashboardData(faculty, "2026-2027"));
        }
    }
}
