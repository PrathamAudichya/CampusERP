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
 * [T24] Faculty teaching dashboard isolation & authorization tests for {@link FacultyDashboardService}.
 */
public class FacultyDashboardServiceTest {

    private static FacultyDashboardService dashboardService;
    private static UserDAO userDAO;
    private static DepartmentDAO departmentDAO;
    private static FacultyDAO facultyDAO;
    private static CourseDAO courseDAO;
    private static CourseAssignmentDAO assignmentDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        dashboardService = new FacultyDashboardService();
        userDAO = new UserDAO();
        departmentDAO = new DepartmentDAO();
        facultyDAO = new FacultyDAO();
        courseDAO = new CourseDAO();
        assignmentDAO = new CourseAssignmentDAO();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    @Test
    @DisplayName("Verify faculty dashboard data assembly isolated to assigned courses")
    void testFacultyDashboardDataAssembly() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course c1 = courseDAO.insert(conn, new Course(dept.getId(), "CS101", "Intro", 4, "1", 50));
            Course c2 = courseDAO.insert(conn, new Course(dept.getId(), "CS102", "Advanced", 4, "1", 50));

            User facUser = userDAO.insert(conn, new User("fac_dash1", "$2a$12$hash", Role.FACULTY));
            Faculty faculty = facultyDAO.insert(conn, new Faculty(facUser.getId(), dept.getId(), "F_DASH1", "Ada", "Lovelace", "ada@c.edu", "555", "Algorithms", "2020-01-01"));

            // Assign faculty ONLY to c1
            assignmentDAO.insert(conn, new CourseAssignment(c1.getId(), faculty.getId(), "2026-2027"));

            FacultyDashboardService.FacultyDashboardData data = dashboardService.getDashboardData(facUser, "2026-2027");
            assertNotNull(data);
            assertEquals("F_DASH1", data.getFacultyProfile().getFacultyId());
            assertEquals(1, data.getAssignments().size());
            assertEquals(c1.getId(), data.getAssignments().get(0).getCourseId());
            assertTrue(data.getCourseCatalog().containsKey(c1.getId()));
            assertFalse(data.getCourseCatalog().containsKey(c2.getId()), "Unassigned course must not appear in faculty dashboard");
        }
    }

    @Test
    @DisplayName("Special Regression Gap 8 & 9: Forced password change blocks faculty dashboard assembly")
    void testForcedPasswordChangeBlocksFacultyDashboard() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            User forcedUser = new User("fac_forced", "$2a$12$hash", Role.FACULTY);
            forcedUser.setMustChangePassword(true);
            userDAO.insert(conn, forcedUser);
            facultyDAO.insert(conn, new Faculty(forcedUser.getId(), dept.getId(), "F_FORCED", "Ada", "L", "f@c.edu", "555", "AI", "2020-01-01"));

            assertThrows(AuthenticationException.class,
                    () -> dashboardService.getDashboardData(forcedUser, "2026-2027"),
                    "Dashboard must be blocked if faculty must change password");
        }
    }

    @Test
    @DisplayName("Verify non-faculty roles cannot access faculty dashboard")
    void testNonFacultyRoleRejected() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User student = userDAO.insert(conn, new User("stu_no_facdash", "$2a$12$hash", Role.STUDENT));
            assertThrows(AuthenticationException.class,
                    () -> dashboardService.getDashboardData(student, "2026-2027"));
        }
    }
}

