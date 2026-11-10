package com.college.dao;

import com.college.TestDBHelper;
import com.college.exception.DataAccessException;
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
 * [T7] Persistence & authorization tests for {@link CourseAssignmentDAO}.
 */
public class CourseAssignmentDAOTest {

    private static CourseAssignmentDAO assignmentDAO;
    private static CourseDAO courseDAO;
    private static FacultyDAO facultyDAO;
    private static DepartmentDAO departmentDAO;
    private static UserDAO userDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        assignmentDAO = new CourseAssignmentDAO();
        courseDAO = new CourseDAO();
        facultyDAO = new FacultyDAO();
        departmentDAO = new DepartmentDAO();
        userDAO = new UserDAO();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    private Faculty createTestFaculty(Connection conn, String username, String facId) {
        User u = userDAO.insert(conn, new User(username, "$2a$12$hash", Role.FACULTY));
        Department d = departmentDAO.insert(conn, new Department("DEPT_" + facId, "Dept " + facId));
        Faculty f = new Faculty(u.getId(), d.getId(), facId, "Prof", "Smith", facId + "@college.edu", "555-1212", "Systems", "2020-01-01");
        return facultyDAO.insert(conn, f);
    }

    @Test
    @DisplayName("Verify assignment creation and lookup by ID, Course, and Faculty")
    void testInsertAndFind() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS101", "Intro", 3, "1", 50));
            Faculty faculty = createTestFaculty(conn, "fac1", "FAC001");

            CourseAssignment ca = new CourseAssignment(course.getId(), faculty.getId(), "2026-2027");
            CourseAssignment inserted = assignmentDAO.insert(conn, ca);
            assertTrue(inserted.getId() > 0);

            CourseAssignment found = assignmentDAO.findById(inserted.getId());
            assertNotNull(found);
            assertEquals(course.getId(), found.getCourseId());
            assertEquals(faculty.getId(), found.getFacultyId());
            assertEquals("2026-2027", found.getAcademicYear());
            assertTrue(found.isActive());
        }
    }

    @Test
    @DisplayName("Verify multiple faculty can be assigned to the same course for co-teaching")
    void testMultipleFacultyPerCourse() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS201", "Algorithms", 4, "2", 60));
            Faculty fac1 = createTestFaculty(conn, "fac1", "FAC001");
            Faculty fac2 = createTestFaculty(conn, "fac2", "FAC002");

            assignmentDAO.insert(conn, new CourseAssignment(course.getId(), fac1.getId(), "2026-2027"));
            assignmentDAO.insert(conn, new CourseAssignment(course.getId(), fac2.getId(), "2026-2027"));

            List<CourseAssignment> assignments = assignmentDAO.findByCourse(conn, course.getId());
            assertEquals(2, assignments.size(), "Course should allow multiple faculty assignments");
        }
    }

    @Test
    @DisplayName("Verify assignment deactivation and reactivation")
    void testDeactivationAndReactivation() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS301", "Networks", 3, "3", 50));
            Faculty faculty = createTestFaculty(conn, "fac1", "FAC001");

            CourseAssignment ca = assignmentDAO.insert(conn, new CourseAssignment(course.getId(), faculty.getId(), "2026-2027"));

            // Deactivate
            assignmentDAO.setActive(conn, ca.getId(), false);
            assertFalse(assignmentDAO.findById(ca.getId()).isActive());
            assertTrue(assignmentDAO.findActiveByFacultyAndYear(conn, faculty.getId(), "2026-2027").isEmpty());

            // Reactivate
            assignmentDAO.setActive(conn, ca.getId(), true);
            assertTrue(assignmentDAO.findById(ca.getId()).isActive());
            assertEquals(1, assignmentDAO.findActiveByFacultyAndYear(conn, faculty.getId(), "2026-2027").size());
        }
    }
}
