package com.college.service;

import com.college.TestDBHelper;
import com.college.dao.DepartmentDAO;
import com.college.dao.FacultyDAO;
import com.college.dao.UserDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.ValidationException;
import com.college.model.Department;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Faculty credential management, auto-generated temporary password assignment,
 * and first-login password rotation enforcement.
 */
public class FacultyCredentialManagementTest {

    private static FacultyService facultyService;
    private static AuthService authService;
    private static UserDAO userDAO;
    private static DepartmentDAO departmentDAO;
    private static FacultyDAO facultyDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        facultyService = new FacultyService();
        authService = new AuthService();
        userDAO = new UserDAO();
        departmentDAO = new DepartmentDAO();
        facultyDAO = new FacultyDAO();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    @Test
    @DisplayName("Verify faculty creation with initial password creates linked user with must_change_password=1")
    void testFacultyCreationWithCredentials() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = userDAO.insert(conn, new User("admin_fac_cred", "$2a$12$hash", Role.ADMIN));
            Department dept = departmentDAO.insert(conn, new Department("ME", "Mechanical Engineering"));

            Faculty f = new Faculty();
            f.setDepartmentId(dept.getId());
            f.setFacultyId("FAC2026501");
            f.setFirstName("Isaac");
            f.setLastName("Newton");
            f.setEmail("inewton@college.edu");
            f.setPhone("555-4321");
            f.setSpecialization("Classical Mechanics");

            Faculty created = facultyService.createFaculty(admin, f, "TempNewtonPass1!");
            assertNotNull(created);
            assertTrue(created.getUserId() > 0);

            // Verify user record
            User user = userDAO.findById(created.getUserId());
            assertNotNull(user);
            assertEquals("FAC2026501", user.getUsername(), "Faculty username must default to Faculty ID");
            assertEquals(Role.FACULTY, user.getRole());
            assertTrue(user.isMustChangePassword(), "New faculty account must require password change on first login");
            assertTrue(user.isActive());

            // Verify authentication succeeds with initial temporary password
            User loggedIn = authService.login("FAC2026501", "TempNewtonPass1!");
            assertNotNull(loggedIn);
            assertTrue(loggedIn.isMustChangePassword());

            // Force change password on first login
            authService.forceChangePassword(loggedIn, "PermanentSecurePass123!");

            // Verify must_change_password is now cleared
            User updatedUser = userDAO.findById(created.getUserId());
            assertFalse(updatedUser.isMustChangePassword());

            // Verify login with new permanent password succeeds
            User reLoggedIn = authService.login("FAC2026501", "PermanentSecurePass123!");
            assertNotNull(reLoggedIn);
            assertFalse(reLoggedIn.isMustChangePassword());
        }
    }

    @Test
    @DisplayName("Verify administrator password reset workflow for faculty account")
    void testFacultyPasswordResetWorkflow() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = userDAO.insert(conn, new User("admin_reset", "$2a$12$hash", Role.ADMIN));
            Department dept = departmentDAO.insert(conn, new Department("CE", "Civil Engineering"));

            Faculty f = new Faculty();
            f.setDepartmentId(dept.getId());
            f.setFacultyId("FAC2026502");
            f.setFirstName("Archimedes");
            f.setLastName("Syracuse");
            f.setEmail("archimedes@college.edu");
            f.setSpecialization("Hydraulics");

            Faculty created = facultyService.createFaculty(admin, f, "InitialArchPass1!");
            User user = userDAO.findById(created.getUserId());
            authService.forceChangePassword(user, "OldPermanentPass1!");

            assertFalse(userDAO.findById(created.getUserId()).isMustChangePassword());

            // Admin resets faculty password
            String newTempPass = authService.resetUserPassword(admin, created.getUserId(), null);
            assertNotNull(newTempPass);

            // Verify user state after reset
            User resetUser = userDAO.findById(created.getUserId());
            assertTrue(resetUser.isMustChangePassword(), "Resetting password must set must_change_password=1");

            // Verify faculty can log in with new temporary password
            User loggedIn = authService.login("FAC2026502", newTempPass);
            assertNotNull(loggedIn);
            assertTrue(loggedIn.isMustChangePassword());
        }
    }
}

