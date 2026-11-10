package com.college.service;

import com.college.TestDBHelper;
import com.college.dao.DepartmentDAO;
import com.college.dao.StudentDAO;
import com.college.dao.UserDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.DataAccessException;
import com.college.exception.ValidationException;
import com.college.model.Department;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [T15] Transactional student creation & lifecycle tests for {@link StudentService}.
 */
public class StudentServiceTest {

    private static StudentService studentService;
    private static UserDAO userDAO;
    private static DepartmentDAO departmentDAO;
    private static StudentDAO studentDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        studentService = new StudentService();
        userDAO = new UserDAO();
        departmentDAO = new DepartmentDAO();
        studentDAO = new StudentDAO();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    private User createAdmin(Connection conn) {
        User admin = new User("admin_svc", "$2a$12$hash", Role.ADMIN);
        admin.setActive(true);
        return userDAO.insert(conn, admin);
    }

    @Test
    @DisplayName("Verify atomic student creation with linked user account")
    void testAtomicStudentCreationSuccess() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));

            Student s = new Student();
            s.setDepartmentId(dept.getId());
            s.setStudentId("STU2026100");
            s.setFirstName("Grace");
            s.setLastName("Hopper");
            s.setEmail("ghopper@college.edu");
            s.setPhone("555-0101");
            s.setDateOfBirth("2004-12-09");
            s.setAdmissionDate("2026-08-01");
            s.setSemester("1");

            Student created = studentService.createStudent(admin, s, "initialPass123");
            assertNotNull(created);
            assertTrue(created.getId() > 0);
            assertTrue(created.getUserId() > 0);

            // Verify User account
            User user = userDAO.findById(created.getUserId());
            assertNotNull(user);
            assertEquals("STU2026100", user.getUsername());
            assertEquals(Role.STUDENT, user.getRole());
            assertTrue(user.isMustChangePassword(), "New student must be flagged for mandatory password change");
        }
    }

    @Test
    @DisplayName("Verify role-based access control: Non-admin users cannot create students")
    void testNonAdminCannotCreateStudent() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User faculty = userDAO.insert(conn, new User("fac_unauth", "$2a$12$hash", Role.FACULTY));
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));

            Student s = new Student();
            s.setDepartmentId(dept.getId());
            s.setStudentId("STU_UNAUTH");
            s.setFirstName("Test");
            s.setLastName("User");
            s.setEmail("test@college.edu");
            s.setSemester("1");

            assertThrows(AuthenticationException.class, () -> studentService.createStudent(faculty, s, "pass123"));
        }
    }

    @Test
    @DisplayName("Special Regression Gap 1: Secondary SQL failure in student creation rolls back user account cleanly")
    void testStudentCreationRollbackOnSecondaryFailure() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = departmentDAO.insert(conn, new Department("CS_FAIL", "Computer Science"));

            Student s = new Student();
            s.setDepartmentId(dept.getId());
            s.setStudentId("STU_FAIL");
            s.setFirstName("Fail");
            s.setLastName("Student");
            s.setEmail("fail@college.edu");
            s.setSemester("1");

            // Mock studentDAO with secondary SQL insertion failure after user is inserted
            StudentDAO failingStudentDAO = new StudentDAO() {
                @Override
                public Student insert(Connection c, Student stu) {
                    throw new DataAccessException("Simulated secondary SQL insertion failure");
                }
            };
            StudentService service = new StudentService(failingStudentDAO, userDAO, departmentDAO, new AuditService());

            // Creation must fail and roll back the linked user account
            assertThrows(DataAccessException.class, () -> service.createStudent(admin, s, "pass123"));

            // Verify user account was rolled back and DOES NOT exist in DB
            User orphanedUser = userDAO.findByUsername("STU_FAIL");
            assertNull(orphanedUser, "User account must be rolled back when student record insertion fails");

            // Verify no student record exists
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM students")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        }
    }

    @Test
    @DisplayName("Verify duplicate username or studentId collision triggers clean rollback")
    void testDuplicateCollisionRollback() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));

            Student s1 = new Student();
            s1.setDepartmentId(dept.getId());
            s1.setStudentId("STU_DUP_1");
            s1.setFirstName("One");
            s1.setLastName("Student");
            s1.setEmail("one@college.edu");
            s1.setSemester("1");
            studentService.createStudent(admin, s1, "pass123");

            // Attempting duplicate studentId / username
            Student s2 = new Student();
            s2.setDepartmentId(dept.getId());
            s2.setStudentId("STU_DUP_1");
            s2.setFirstName("Two");
            s2.setLastName("Student");
            s2.setEmail("two@college.edu");
            s2.setSemester("1");

            assertThrows(ValidationException.class, () -> studentService.createStudent(admin, s2, "pass123"));
        }
    }
}
