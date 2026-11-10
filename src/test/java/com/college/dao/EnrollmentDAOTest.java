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
 * [T8] Persistence & lifecycle tests for {@link EnrollmentDAO}.
 */
public class EnrollmentDAOTest {

    private static EnrollmentDAO enrollmentDAO;
    private static StudentDAO studentDAO;
    private static CourseDAO courseDAO;
    private static DepartmentDAO departmentDAO;
    private static UserDAO userDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        enrollmentDAO = new EnrollmentDAO();
        studentDAO = new StudentDAO();
        courseDAO = new CourseDAO();
        departmentDAO = new DepartmentDAO();
        userDAO = new UserDAO();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    private Student createTestStudent(Connection conn, String username, String stuId) {
        User u = userDAO.insert(conn, new User(username, "$2a$12$hash", Role.STUDENT));
        Department d = departmentDAO.insert(conn, new Department("D_" + stuId, "Dept " + stuId));
        Student s = new Student(u.getId(), d.getId(), stuId, "John", "Doe", stuId + "@college.edu", "555-0001", "2004-01-01", "2026-08-01", "1");
        return studentDAO.insert(conn, s);
    }

    @Test
    @DisplayName("Verify enrollment insertion, retrieval, and status handling")
    void testInsertAndFind() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS101", "Intro", 3, "1", 30));
            Student student = createTestStudent(conn, "stu1", "STU001");

            Enrollment enr = new Enrollment(student.getId(), course.getId(), "2026-2027");
            enr.setStatus("ACTIVE");
            enr.setEnrolledDate("2026-08-15");
            Enrollment inserted = enrollmentDAO.insert(conn, enr);
            assertTrue(inserted.getId() > 0);

            Enrollment found = enrollmentDAO.findById(inserted.getId());
            assertNotNull(found);
            assertEquals(student.getId(), found.getStudentId());
            assertEquals(course.getId(), found.getCourseId());
            assertEquals("2026-2027", found.getAcademicYear());
            assertEquals("ACTIVE", found.getStatus());
        }
    }

    @Test
    @DisplayName("Verify unique enrollment constraint (student_id, course_id, academic_year)")
    void testUniqueEnrollmentConstraint() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS101", "Intro", 3, "1", 30));
            Student student = createTestStudent(conn, "stu1", "STU001");

            Enrollment enr1 = new Enrollment(student.getId(), course.getId(), "2026-2027");
            enr1.setStatus("ACTIVE");
            enrollmentDAO.insert(conn, enr1);

            // Duplicate enrollment in same academic year must fail
            Enrollment dup = new Enrollment(student.getId(), course.getId(), "2026-2027");
            dup.setStatus("ACTIVE");
            assertThrows(DataAccessException.class, () -> enrollmentDAO.insert(conn, dup));
        }
    }

    @Test
    @DisplayName("Verify status transitions and active enrollment count for capacity guards")
    void testStatusTransitionsAndCapacityCount() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS201", "Algorithms", 4, "2", 30));
            Student s1 = createTestStudent(conn, "stu1", "STU001");
            Student s2 = createTestStudent(conn, "stu2", "STU002");

            Enrollment enr1 = new Enrollment(s1.getId(), course.getId(), "2026-2027");
            enr1.setStatus("ACTIVE");
            Enrollment e1 = enrollmentDAO.insert(conn, enr1);

            Enrollment enr2 = new Enrollment(s2.getId(), course.getId(), "2026-2027");
            enr2.setStatus("ACTIVE");
            Enrollment e2 = enrollmentDAO.insert(conn, enr2);

            assertEquals(2, enrollmentDAO.countActiveByCourseAndYear(conn, course.getId(), "2026-2027"));

            // Drop e1
            enrollmentDAO.updateStatus(conn, e1.getId(), "DROPPED");
            assertEquals("DROPPED", enrollmentDAO.findById(e1.getId()).getStatus());
            assertEquals(1, enrollmentDAO.countActiveByCourseAndYear(conn, course.getId(), "2026-2027"), "Dropped enrollment should not count towards active capacity");

            // Complete e2
            enrollmentDAO.updateStatus(conn, e2.getId(), "COMPLETED");
            assertEquals(0, enrollmentDAO.countActiveByCourseAndYear(conn, course.getId(), "2026-2027"));
        }
    }
}
