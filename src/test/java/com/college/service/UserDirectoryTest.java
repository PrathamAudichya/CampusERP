package com.college.service;

import com.college.TestDBHelper;
import com.college.dao.DepartmentDAO;
import com.college.dao.FacultyDAO;
import com.college.dao.StudentDAO;
import com.college.dao.UserDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.ValidationException;
import com.college.model.Department;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.User;
import com.college.model.UserDirectoryItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Institutional User Directory and Administrative Credential Management.
 */
public class UserDirectoryTest {

    private static UserDAO userDAO;
    private static StudentDAO studentDAO;
    private static FacultyDAO facultyDAO;
    private static DepartmentDAO departmentDAO;
    private static AuthService authService;
    private static StudentService studentService;
    private static FacultyService facultyService;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        userDAO = new UserDAO();
        studentDAO = new StudentDAO();
        facultyDAO = new FacultyDAO();
        departmentDAO = new DepartmentDAO();
        authService = new AuthService();
        studentService = new StudentService();
        facultyService = new FacultyService();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    @Test
    @DisplayName("Verify comprehensive User Directory query joins students, faculty, and departments with 0 duplicates")
    void testUserDirectoryQueryAccuracy() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            // 1. Create Admin
            User admin = new User("admin1", "$2a$12$somehashforadminaccount", Role.ADMIN);
            admin.setActive(true);
            admin = userDAO.insert(conn, admin);

            // 2. Create Department
            Department dept = new Department("CS", "Computer Science");
            dept = departmentDAO.insert(conn, dept);

            // 3. Create Student via StudentService
            Student s = new Student();
            s.setStudentId("STU2026888");
            s.setFirstName("Ada");
            s.setLastName("Lovelace");
            s.setEmail("ada@college.edu");
            s.setPhone("555-1111");
            s.setDepartmentId(dept.getId());
            s.setSemester("1");
            s.setAdmissionDate("2026-08-01");
            s.setDateOfBirth("2005-12-10");
            StudentService.StudentCreationResult sRes = studentService.createStudentWithAutoAccount(admin, s);

            // 4. Create Faculty via FacultyService
            Faculty f = new Faculty();
            f.setFacultyId("FAC2026777");
            f.setFirstName("Charles");
            f.setLastName("Babbage");
            f.setEmail("charles@college.edu");
            f.setPhone("555-2222");
            f.setDepartmentId(dept.getId());
            f.setSpecialization("Computer Architecture");
            f.setJoinDate("2026-01-10");
            Faculty fRes = facultyService.createFaculty(admin, f, "initialFacultyPass123");

            // 5. Query Directory
            List<UserDirectoryItem> items = authService.getUserDirectory(admin);
            assertEquals(3, items.size(), "Directory must contain exactly 3 users (Admin, Student, Faculty)");

            // Check Admin Item
            UserDirectoryItem adminItem = items.stream().filter(it -> it.getRole() == Role.ADMIN).findFirst().orElse(null);
            assertNotNull(adminItem);
            assertEquals("admin1", adminItem.getUsername());
            assertEquals("System Administrator", adminItem.getFullName());
            assertEquals("ADM-" + String.format("%03d", admin.getId()), adminItem.getDisplayNaturalId());
            assertEquals("Central Administration", adminItem.getDisplayDepartment());

            // Check Student Item
            UserDirectoryItem studentItem = items.stream().filter(it -> it.getRole() == Role.STUDENT).findFirst().orElse(null);
            assertNotNull(studentItem);
            assertEquals("STU2026888", studentItem.getUsername());
            assertEquals("STU2026888", studentItem.getNaturalId());
            assertEquals("Ada Lovelace", studentItem.getFullName());
            assertEquals("ada@college.edu", studentItem.getEmail());
            assertEquals("CS", studentItem.getDepartmentCode());
            assertTrue(studentItem.isMustChangePassword(), "New student must be required to change password on first login");

            // Check Faculty Item
            UserDirectoryItem facultyItem = items.stream().filter(it -> it.getRole() == Role.FACULTY).findFirst().orElse(null);
            assertNotNull(facultyItem);
            assertEquals("FAC2026777", facultyItem.getUsername());
            assertEquals("FAC2026777", facultyItem.getNaturalId());
            assertEquals("Charles Babbage", facultyItem.getFullName());
            assertEquals("charles@college.edu", facultyItem.getEmail());
            assertEquals("Computer Architecture", facultyItem.getSpecialization());
            assertTrue(facultyItem.isMustChangePassword());
        }
    }

    @Test
    @DisplayName("Verify administrative password reset generates temporary password and flags must_change_password=1")
    void testAdminResetPassword() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = new User("admin_mgr", "$2a$12$somehashforadminaccount", Role.ADMIN);
            admin.setActive(true);
            admin = userDAO.insert(conn, admin);

            Department dept = departmentDAO.insert(conn, new Department("EC", "Electronics"));

            Faculty f = new Faculty();
            f.setFacultyId("FAC2026999");
            f.setFirstName("Grace");
            f.setLastName("Hopper");
            f.setEmail("ghopper@college.edu");
            f.setDepartmentId(dept.getId());
            f.setSpecialization("Compilers");
            Faculty createdFaculty = facultyService.createFaculty(admin, f, "InitialPass123");

            // Reset password via AuthService
            String tempPassword = authService.resetUserPassword(admin, createdFaculty.getUserId(), null);
            assertNotNull(tempPassword);
            assertTrue(tempPassword.length() >= 8, "Temporary password must be at least 8 characters");

            // Verify user in DB has must_change_password=1
            User updatedUser = userDAO.findById(createdFaculty.getUserId());
            assertTrue(updatedUser.isMustChangePassword());

            // Verify login with temporary password succeeds
            User loggedIn = authService.login(updatedUser.getUsername(), tempPassword);
            assertNotNull(loggedIn);
            assertTrue(loggedIn.isMustChangePassword());
        }
    }

    @Test
    @DisplayName("Verify force password change without resetting password")
    void testForcePasswordChange() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = new User("admin_pwd", "$2a$12$somehashforadminaccount", Role.ADMIN);
            admin.setActive(true);
            admin = userDAO.insert(conn, admin);

            User user = new User("regular_user", "$2a$12$somehashforregularuser", Role.STUDENT);
            user.setMustChangePassword(false);
            user.setActive(true);
            user = userDAO.insert(conn, user);

            assertFalse(userDAO.findById(user.getId()).isMustChangePassword());

            boolean success = authService.forcePasswordChange(admin, user.getId());
            assertTrue(success);

            User refreshed = userDAO.findById(user.getId());
            assertTrue(refreshed.isMustChangePassword());
        }
    }

    @Test
    @DisplayName("Verify non-admin role is rejected from User Directory and Password Reset")
    void testRbacRejection() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User newUser = new User("student_user", "$2a$12$somehash", Role.STUDENT);
            newUser.setActive(true);
            final User student = userDAO.insert(conn, newUser);

            assertThrows(AuthenticationException.class, () -> authService.getUserDirectory(student));
            assertThrows(AuthenticationException.class, () -> authService.resetUserPassword(student, 1, "newPass123"));
            assertThrows(AuthenticationException.class, () -> authService.forcePasswordChange(student, 1));
        }
    }
}
