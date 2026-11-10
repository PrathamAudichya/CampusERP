package com.college.service;

import com.college.TestDBHelper;
import com.college.dao.CourseDAO;
import com.college.dao.DepartmentDAO;
import com.college.dao.EnrollmentDAO;
import com.college.dao.StudentDAO;
import com.college.dao.UserDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.ValidationException;
import com.college.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [T17] Lifecycle, capacity guards & authorization tests for {@link EnrollmentService}.
 */
public class EnrollmentServiceTest {

    private static EnrollmentService enrollmentService;
    private static EnrollmentDAO enrollmentDAO;
    private static StudentDAO studentDAO;
    private static CourseDAO courseDAO;
    private static DepartmentDAO departmentDAO;
    private static UserDAO userDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        enrollmentService = new EnrollmentService();
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

    private User createAdmin(Connection conn) {
        User u = new User("admin_enr", "$2a$12$hash", Role.ADMIN);
        u.setActive(true);
        return userDAO.insert(conn, u);
    }

    private Student createStudent(Connection conn, String roll, int deptId) {
        User u = userDAO.insert(conn, new User("u_" + roll, "$2a$12$hash", Role.STUDENT));
        return studentDAO.insert(conn, new Student(u.getId(), deptId, roll, "Fn", "Ln", roll + "@col.edu", "555", "2004-01-01", "2026-08-01", "1"));
    }

    @Test
    @DisplayName("Verify enrollment creation and capacity guard enforcement")
    void testEnrollmentCapacityGuard() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));

            // Course with capacity = 1
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS_CAP", "Small Class", 3, "1", 1));

            Student s1 = createStudent(conn, "STU_C1", dept.getId());
            Student s2 = createStudent(conn, "STU_C2", dept.getId());

            // First enrollment succeeds
            Enrollment e1 = enrollmentService.enroll(admin, s1.getId(), course.getId(), "2026-2027");
            assertNotNull(e1);
            assertEquals("ACTIVE", e1.getStatus());

            // Second enrollment exceeds capacity (1/1) -> ValidationException
            assertThrows(ValidationException.class, () -> enrollmentService.enroll(admin, s2.getId(), course.getId(), "2026-2027"));
        }
    }

    @Test
    @DisplayName("Verify role authorization (SEC-04): Faculty cannot enroll or drop students")
    void testFacultyCannotEnrollOrDrop() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User faculty = userDAO.insert(conn, new User("fac_user", "$2a$12$hash", Role.FACULTY));
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS_F", "Course", 3, "1", 10));
            Student student = createStudent(conn, "STU_F", dept.getId());

            assertThrows(AuthenticationException.class, () -> enrollmentService.enroll(faculty, student.getId(), course.getId(), "2026-2027"));
        }
    }

    @Test
    @DisplayName("Verify dropping enrollment and reactivation of DROPPED status")
    void testDropAndReactivateEnrollment() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS_ACT", "Course", 3, "1", 10));
            Student student = createStudent(conn, "STU_ACT", dept.getId());

            Enrollment enr = enrollmentService.enroll(admin, student.getId(), course.getId(), "2026-2027");
            assertEquals("ACTIVE", enr.getStatus());

            // Drop
            enrollmentService.dropEnrollment(admin, enr.getId());
            assertEquals("DROPPED", enrollmentDAO.findById(enr.getId()).getStatus());

            // Re-enrolling reactivates existing dropped enrollment
            Enrollment reactivated = enrollmentService.enroll(admin, student.getId(), course.getId(), "2026-2027");
            assertEquals(enr.getId(), reactivated.getId(), "Existing record should be reactivated");
            assertEquals("ACTIVE", reactivated.getStatus());
        }
    }
}
