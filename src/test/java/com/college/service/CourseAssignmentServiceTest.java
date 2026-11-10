package com.college.service;

import com.college.TestDBHelper;
import com.college.dao.CourseAssignmentDAO;
import com.college.dao.CourseDAO;
import com.college.dao.DepartmentDAO;
import com.college.dao.FacultyDAO;
import com.college.dao.UserDAO;
import com.college.exception.AuthenticationException;
import com.college.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [T21] Course assignment authorization & lifecycle tests for {@link CourseAssignmentService}.
 */
public class CourseAssignmentServiceTest {

    private static CourseAssignmentService assignmentService;
    private static UserDAO userDAO;
    private static DepartmentDAO departmentDAO;
    private static FacultyDAO facultyDAO;
    private static CourseDAO courseDAO;
    private static CourseAssignmentDAO assignmentDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        assignmentService = new CourseAssignmentService();
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
    @DisplayName("Verify admin assigns faculty to course and queries active assignments")
    void testAssignFacultyToCourse() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = userDAO.insert(conn, new User("admin_asg", "$2a$12$hash", Role.ADMIN));
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS101", "Intro", 3, "1", 50));

            User facUser = userDAO.insert(conn, new User("fac_asg", "$2a$12$hash", Role.FACULTY));
            Faculty faculty = facultyDAO.insert(conn, new Faculty(facUser.getId(), dept.getId(), "FAC_ASG", "Bob", "Smith", "bob@col.edu", "555", "Networks", "2020-01-01"));

            CourseAssignment ca = assignmentService.assignFaculty(admin, course.getId(), faculty.getId(), "2026-2027");
            assertNotNull(ca);
            assertTrue(ca.getId() > 0);
            assertTrue(ca.isActive());

            List<CourseAssignment> list = assignmentService.getActiveAssignmentsByFacultyAndYear(admin, faculty.getId(), "2026-2027");
            assertEquals(1, list.size());
            assertEquals(course.getId(), list.get(0).getCourseId());
        }
    }

    @Test
    @DisplayName("Verify role authorization: Non-admin users cannot assign faculty")
    void testNonAdminCannotAssignFaculty() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User faculty = userDAO.insert(conn, new User("fac_unauth_asg", "$2a$12$hash", Role.FACULTY));
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS102", "Intro", 3, "1", 50));
            Faculty f = facultyDAO.insert(conn, new Faculty(faculty.getId(), dept.getId(), "FAC_U", "Bob", "S", "b@c.edu", "555", "AI", "2020-01-01"));

            assertThrows(AuthenticationException.class,
                    () -> assignmentService.assignFaculty(faculty, course.getId(), f.getId(), "2026-2027"));
        }
    }
}
