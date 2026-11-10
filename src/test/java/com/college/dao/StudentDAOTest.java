package com.college.dao;

import com.college.TestDBHelper;
import com.college.exception.DataAccessException;
import com.college.model.Department;
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
 * [T5] Persistence tests for {@link StudentDAO}.
 */
public class StudentDAOTest {

    private static StudentDAO studentDAO;
    private static UserDAO userDAO;
    private static DepartmentDAO departmentDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        studentDAO = new StudentDAO();
        userDAO = new UserDAO();
        departmentDAO = new DepartmentDAO();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    private Student createSampleStudent(Connection conn, String username, String studentId, String email) {
        User user = userDAO.insert(conn, new User(username, "$2a$12$hash", Role.STUDENT));
        Department dept = departmentDAO.insert(conn, new Department("CS_" + studentId, "Computer Science"));

        Student s = new Student();
        s.setUserId(user.getId());
        s.setDepartmentId(dept.getId());
        s.setStudentId(studentId);
        s.setFirstName("Alice");
        s.setLastName("Wonderland");
        s.setEmail(email);
        s.setPhone("555-0101");
        s.setDateOfBirth("2003-05-15");
        s.setAdmissionDate("2026-08-01");
        s.setSemester("1");
        s.setActive(true);
        return studentDAO.insert(conn, s);
    }

    @Test
    @DisplayName("Verify student insertion and retrieval by ID, StudentID, and UserID")
    void testInsertAndFind() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Student inserted = createSampleStudent(conn, "alice", "STU2026001", "alice@college.edu");
            assertTrue(inserted.getId() > 0);

            Student byId = studentDAO.findById(inserted.getId());
            assertNotNull(byId);
            assertEquals("STU2026001", byId.getStudentId());
            assertEquals("Alice", byId.getFirstName());
            assertEquals("2003-05-15", byId.getDateOfBirth());
            assertEquals("2026-08-01", byId.getAdmissionDate());

            Student byStudentId = studentDAO.findByStudentId("STU2026001");
            assertNotNull(byStudentId);
            assertEquals(inserted.getId(), byStudentId.getId());

            Student byUserId = studentDAO.findByUserId(inserted.getUserId());
            assertNotNull(byUserId);
            assertEquals(inserted.getId(), byUserId.getId());
        }
    }

    @Test
    @DisplayName("Verify unique student_id and unique email constraints")
    void testUniqueConstraints() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Student s1 = createSampleStudent(conn, "user1", "STU001", "unique1@college.edu");

            // Attempt duplicate student_id
            User u2 = userDAO.insert(conn, new User("user2", "hash", Role.STUDENT));
            Student dupId = new Student();
            dupId.setUserId(u2.getId());
            dupId.setDepartmentId(s1.getDepartmentId());
            dupId.setStudentId("STU001"); // DUPLICATE
            dupId.setFirstName("Bob");
            dupId.setLastName("Smith");
            dupId.setEmail("diff@college.edu");
            assertThrows(DataAccessException.class, () -> studentDAO.insert(conn, dupId));

            // Attempt duplicate email
            User u3 = userDAO.insert(conn, new User("user3", "hash", Role.STUDENT));
            Student dupEmail = new Student();
            dupEmail.setUserId(u3.getId());
            dupEmail.setDepartmentId(s1.getDepartmentId());
            dupEmail.setStudentId("STU003");
            dupEmail.setFirstName("Charlie");
            dupEmail.setLastName("Brown");
            dupEmail.setEmail("unique1@college.edu"); // DUPLICATE
            assertThrows(DataAccessException.class, () -> studentDAO.insert(conn, dupEmail));
        }
    }

    @Test
    @DisplayName("Verify foreign key violations reject invalid user_id or department_id")
    void testForeignKeyEnforcement() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));

            Student invalidUser = new Student();
            invalidUser.setUserId(9999); // NON-EXISTENT USER
            invalidUser.setDepartmentId(dept.getId());
            invalidUser.setStudentId("STU999");
            invalidUser.setFirstName("Ghost");
            invalidUser.setLastName("User");
            invalidUser.setEmail("ghost@college.edu");
            assertThrows(DataAccessException.class, () -> studentDAO.insert(conn, invalidUser));
        }
    }

    @Test
    @DisplayName("Verify student profile update and soft deactivation")
    void testUpdateAndSoftDeactivation() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Student s = createSampleStudent(conn, "upd_user", "STU_UPD", "upd@college.edu");

            s.setPhone("555-9999");
            s.setEmail("updated_email@college.edu");
            s.setSemester("2");
            boolean updated = studentDAO.update(conn, s);
            assertTrue(updated);

            Student reloaded = studentDAO.findById(s.getId());
            assertEquals("555-9999", reloaded.getPhone());
            assertEquals("updated_email@college.edu", reloaded.getEmail());
            assertEquals("2", reloaded.getSemester());

            // Soft deactivation
            studentDAO.setActive(conn, s.getId(), false);
            assertFalse(studentDAO.findById(s.getId()).isActive());
            assertTrue(studentDAO.findAllActive().isEmpty());
            assertEquals(1, studentDAO.findAll().size());
        }
    }
}
