package com.college.dao;

import com.college.TestDBHelper;
import com.college.exception.DataAccessException;
import com.college.model.Course;
import com.college.model.Department;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [T6] Persistence & reactivation tests for {@link CourseDAO}.
 */
public class CourseDAOTest {

    private static CourseDAO courseDAO;
    private static DepartmentDAO departmentDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        courseDAO = new CourseDAO();
        departmentDAO = new DepartmentDAO();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    @Test
    @DisplayName("Verify course creation with custom capacity and retrieval")
    void testInsertAndFind() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));

            Course course = new Course();
            course.setDepartmentId(dept.getId());
            course.setCourseCode("CS101");
            course.setName("Introduction to Programming");
            course.setCredits(4);
            course.setSemester("1");
            course.setMaxCapacity(85); // Custom capacity
            course.setActive(true);

            Course inserted = courseDAO.insert(conn, course);
            assertTrue(inserted.getId() > 0);

            Course found = courseDAO.findById(inserted.getId());
            assertNotNull(found);
            assertEquals("CS101", found.getCourseCode());
            assertEquals(4, found.getCredits());
            assertEquals("1", found.getSemester());
            assertEquals(85, found.getMaxCapacity(), "Custom capacity of 85 must be preserved");

            Course byCode = courseDAO.findByCourseCode("CS101");
            assertNotNull(byCode);
            assertEquals(inserted.getId(), byCode.getId());
        }
    }

    @Test
    @DisplayName("Verify unique course_code constraint rejection")
    void testDuplicateCourseCodeRejected() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));

            Course c1 = new Course(dept.getId(), "CS201", "Algorithms", 4, "2", 60);
            courseDAO.insert(conn, c1);

            Course c2 = new Course(dept.getId(), "CS201", "Advanced Algorithms", 4, "3", 60);
            assertThrows(DataAccessException.class, () -> courseDAO.insert(conn, c2));
        }
    }

    @Test
    @DisplayName("Verify updating course properties including capacity")
    void testUpdateCourse() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course c = courseDAO.insert(conn, new Course(dept.getId(), "CS301", "Operating Systems", 3, "3", 50));

            c.setName("Modern Operating Systems");
            c.setCredits(5);
            c.setMaxCapacity(75);
            boolean updated = courseDAO.update(conn, c);
            assertTrue(updated);

            Course reloaded = courseDAO.findById(c.getId());
            assertEquals("Modern Operating Systems", reloaded.getName());
            assertEquals(5, reloaded.getCredits());
            assertEquals(75, reloaded.getMaxCapacity());
        }
    }

    @Test
    @DisplayName("Verify soft deactivation and reactivation of courses")
    void testDeactivationAndReactivation() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course c = courseDAO.insert(conn, new Course(dept.getId(), "CS401", "Compilers", 4, "4", 40));

            courseDAO.setActive(conn, c.getId(), false);
            assertFalse(courseDAO.findById(c.getId()).isActive());
            assertTrue(courseDAO.findAllActive().isEmpty());

            courseDAO.setActive(conn, c.getId(), true);
            assertTrue(courseDAO.findById(c.getId()).isActive());
            assertEquals(1, courseDAO.findAllActive().size());
        }
    }
}
