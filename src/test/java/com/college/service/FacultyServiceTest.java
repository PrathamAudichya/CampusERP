package com.college.service;

import com.college.TestDBHelper;
import com.college.dao.DepartmentDAO;
import com.college.dao.FacultyDAO;
import com.college.dao.UserDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.DataAccessException;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [T16] Transactional faculty creation & lifecycle tests for {@link FacultyService}.
 */
public class FacultyServiceTest {

    private static FacultyService facultyService;
    private static UserDAO userDAO;
    private static DepartmentDAO departmentDAO;
    private static FacultyDAO facultyDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        facultyService = new FacultyService();
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

    private User createAdmin(Connection conn) {
        User admin = new User("admin_fac", "$2a$12$hash", Role.ADMIN);
        admin.setActive(true);
        return userDAO.insert(conn, admin);
    }

    @Test
    @DisplayName("Verify atomic faculty creation with linked user account")
    void testAtomicFacultyCreationSuccess() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = departmentDAO.insert(conn, new Department("EC", "Electronics"));

            Faculty f = new Faculty();
            f.setDepartmentId(dept.getId());
            f.setFacultyId("FAC2026101");
            f.setFirstName("Alan");
            f.setLastName("Turing");
            f.setEmail("aturing@college.edu");
            f.setPhone("555-0202");
            f.setSpecialization("Theoretical Computing");
            f.setJoinDate("2026-01-15");

            Faculty created = facultyService.createFaculty(admin, f, "tempPass123");
            assertNotNull(created);
            assertTrue(created.getId() > 0);
            assertTrue(created.getUserId() > 0);

            // Verify User account
            User user = userDAO.findById(created.getUserId());
            assertNotNull(user);
            assertEquals("FAC2026101", user.getUsername());
            assertEquals(Role.FACULTY, user.getRole());
            assertTrue(user.isMustChangePassword());
        }
    }

    @Test
    @DisplayName("Verify role-based access control: Non-admin users cannot create faculty")
    void testNonAdminCannotCreateFaculty() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User student = userDAO.insert(conn, new User("stu_unauth", "$2a$12$hash", Role.STUDENT));
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));

            Faculty f = new Faculty();
            f.setDepartmentId(dept.getId());
            f.setFacultyId("FAC_UNAUTH");
            f.setFirstName("Test");
            f.setLastName("Prof");
            f.setEmail("prof@college.edu");

            assertThrows(AuthenticationException.class, () -> facultyService.createFaculty(student, f, "pass123"));
        }
    }

    @Test
    @DisplayName("Special Regression Gap 2: Secondary SQL failure in faculty creation rolls back user account cleanly")
    void testFacultyCreationRollbackOnSecondaryFailure() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = departmentDAO.insert(conn, new Department("CS_FAIL_FAC", "Computer Science"));

            Faculty f = new Faculty();
            f.setDepartmentId(dept.getId());
            f.setFacultyId("FAC_FAIL");
            f.setFirstName("Fail");
            f.setLastName("Prof");
            f.setEmail("failprof@college.edu");

            // Mock facultyDAO with secondary SQL insertion failure after user is inserted
            FacultyDAO failingFacultyDAO = new FacultyDAO() {
                @Override
                public Faculty insert(Connection c, Faculty fac) {
                    throw new DataAccessException("Simulated secondary SQL insertion failure");
                }
            };
            FacultyService service = new FacultyService(failingFacultyDAO, userDAO, departmentDAO, new AuditService());

            assertThrows(DataAccessException.class, () -> service.createFaculty(admin, f, "pass123"));

            // Verify user account was rolled back
            User orphanedUser = userDAO.findByUsername("FAC_FAIL");
            assertNull(orphanedUser, "User account must be rolled back when faculty insertion fails");

            // Verify no faculty record exists
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM faculty")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        }
    }

    @Test
    @DisplayName("Verify duplicate username or employeeId collision triggers clean rollback")
    void testDuplicateFacultyCollisionRollback() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));

            Faculty f1 = new Faculty();
            f1.setDepartmentId(dept.getId());
            f1.setFacultyId("FAC_DUP_1");
            f1.setFirstName("One");
            f1.setLastName("Prof");
            f1.setEmail("oneprof@college.edu");
            facultyService.createFaculty(admin, f1, "pass123");

            // Duplicate facultyId attempt
            Faculty f2 = new Faculty();
            f2.setDepartmentId(dept.getId());
            f2.setFacultyId("FAC_DUP_1");
            f2.setFirstName("Two");
            f2.setLastName("Prof");
            f2.setEmail("twoprof@college.edu");

            assertThrows(ValidationException.class, () -> facultyService.createFaculty(admin, f2, "pass123"));
        }
    }

    @Test
    @DisplayName("Verify admin can successfully update faculty username")
    void testUpdateFacultyUsernameSuccess() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = departmentDAO.insert(conn, new Department("IT", "Information Tech"));

            Faculty f = new Faculty();
            f.setDepartmentId(dept.getId());
            f.setFacultyId("FAC_USER_01");
            f.setFirstName("Grace");
            f.setLastName("Hopper");
            f.setEmail("ghopper@college.edu");
            Faculty created = facultyService.createFaculty(admin, f, "pass123");

            // Verify initial linked user
            User linkedUser = facultyService.getLinkedUserForFaculty(admin, created.getId());
            assertNotNull(linkedUser);
            assertEquals("FAC_USER_01", linkedUser.getUsername());

            // Update username
            facultyService.updateFacultyUsername(admin, created.getId(), "grace.hopper");

            // Verify updated username
            User updatedUser = facultyService.getLinkedUserForFaculty(admin, created.getId());
            assertNotNull(updatedUser);
            assertEquals("grace.hopper", updatedUser.getUsername());
        }
    }

    @Test
    @DisplayName("Verify non-admin cannot update faculty username")
    void testUpdateFacultyUsernameForbiddenForNonAdmin() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            User student = userDAO.insert(conn, new User("stu_test", "$2a$12$hash", Role.STUDENT));
            Department dept = departmentDAO.insert(conn, new Department("IT2", "Information Tech"));

            Faculty f = new Faculty();
            f.setDepartmentId(dept.getId());
            f.setFacultyId("FAC_USER_02");
            f.setFirstName("Ada");
            f.setLastName("Lovelace");
            f.setEmail("alovelace@college.edu");
            Faculty created = facultyService.createFaculty(admin, f, "pass123");

            assertThrows(AuthenticationException.class, () ->
                    facultyService.updateFacultyUsername(student, created.getId(), "ada.lovelace"));
        }
    }

    @Test
    @DisplayName("Verify duplicate username collision during faculty username update throws ValidationException")
    void testUpdateFacultyUsernameDuplicateCollision() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = departmentDAO.insert(conn, new Department("IT3", "Information Tech"));

            Faculty f1 = new Faculty();
            f1.setDepartmentId(dept.getId());
            f1.setFacultyId("FAC_USER_03A");
            f1.setFirstName("Claude");
            f1.setLastName("Shannon");
            f1.setEmail("cshannon@college.edu");
            Faculty created1 = facultyService.createFaculty(admin, f1, "pass123");

            Faculty f2 = new Faculty();
            f2.setDepartmentId(dept.getId());
            f2.setFacultyId("FAC_USER_03B");
            f2.setFirstName("John");
            f2.setLastName("Neumann");
            f2.setEmail("jneumann@college.edu");
            Faculty created2 = facultyService.createFaculty(admin, f2, "pass123");

            // Attempting to update f2 username to f1 username should fail
            assertThrows(ValidationException.class, () ->
                    facultyService.updateFacultyUsername(admin, created2.getId(), "FAC_USER_03A"));
        }
    }

    @Test
    @DisplayName("Verify username validation constraints when updating faculty username")
    void testUpdateFacultyUsernameValidationRules() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = departmentDAO.insert(conn, new Department("IT4", "Information Tech"));

            Faculty f = new Faculty();
            f.setDepartmentId(dept.getId());
            f.setFacultyId("FAC_USER_04");
            f.setFirstName("Barbara");
            f.setLastName("Liskov");
            f.setEmail("bliskov@college.edu");
            Faculty created = facultyService.createFaculty(admin, f, "pass123");

            // Empty or blank
            assertThrows(ValidationException.class, () ->
                    facultyService.updateFacultyUsername(admin, created.getId(), "   "));

            // Too short (< 3 chars)
            assertThrows(ValidationException.class, () ->
                    facultyService.updateFacultyUsername(admin, created.getId(), "ab"));

            // Too long (> 50 chars)
            assertThrows(ValidationException.class, () ->
                    facultyService.updateFacultyUsername(admin, created.getId(), "a".repeat(51)));

            // Non-existent faculty ID
            assertThrows(ValidationException.class, () ->
                    facultyService.updateFacultyUsername(admin, 999999, "new.user"));
        }
    }
}
