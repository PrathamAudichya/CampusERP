package com.college.service;

import com.college.TestDBHelper;
import com.college.dao.DepartmentDAO;
import com.college.dao.StudentDAO;
import com.college.dao.UserDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.ValidationException;
import com.college.model.Department;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.User;
import com.college.util.PasswordUtil;
import com.college.util.ValidationUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enterprise credential management test suite for Campus ERP v2.0.6.
 *
 * Covers:
 * - Automatic student account provisioning linked to Student Registration Number
 * - Secure random temporary password generation meeting complexity policy
 * - First-login mandatory password change workflow and access gating
 * - Self-service student profile password change with validation
 * - Admin student credential management (reset, username update, force change)
 * - RBAC authorization enforcement and backward compatibility
 */
public class StudentCredentialManagementTest {

    private static StudentService studentService;
    private static AuthService authService;
    private static UserDAO userDAO;
    private static DepartmentDAO departmentDAO;
    private static StudentDAO studentDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        userDAO = new UserDAO();
        departmentDAO = new DepartmentDAO();
        studentDAO = new StudentDAO();
        authService = new AuthService(userDAO, new AuditService());
        studentService = new StudentService(studentDAO, userDAO, departmentDAO, new AuditService(), authService);
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    private User createAdmin(Connection conn) {
        User admin = new User("admin_cred", PasswordUtil.hashPassword("AdminPass@2026"), Role.ADMIN);
        admin.setActive(true);
        return userDAO.insert(conn, admin);
    }

    private Department createDepartment(Connection conn) {
        return departmentDAO.insert(conn, new Department("CS", "Computer Science"));
    }

    private Student buildSampleStudent(int deptId, String studentId, String email) {
        Student s = new Student();
        s.setDepartmentId(deptId);
        s.setStudentId(studentId);
        s.setFirstName("Ada");
        s.setLastName("Lovelace");
        s.setEmail(email);
        s.setPhone("555-0199");
        s.setDateOfBirth("2005-12-10");
        s.setAdmissionDate("2026-08-01");
        s.setSemester("1");
        return s;
    }

    @Test
    @DisplayName("Auto-provision student account with Student ID as username and strong temporary password")
    void testAutoStudentAccountCreationAndTemporaryPassword() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = createDepartment(conn);

            Student studentInput = buildSampleStudent(dept.getId(), "STU2026888", "ada.lovelace@campus.edu");
            StudentService.StudentCreationResult result = studentService.createStudentWithAutoAccount(admin, studentInput);

            assertNotNull(result, "Creation result should not be null");
            assertNotNull(result.getStudent(), "Created student should not be null");
            assertTrue(result.getStudent().getId() > 0, "Student should have generated database ID");
            assertEquals("STU2026888", result.getStudent().getStudentId());

            // Temporary password checks
            String tempPassword = result.getTemporaryPassword();
            assertNotNull(tempPassword, "Temporary password must not be null");
            assertEquals(12, tempPassword.length(), "Temporary password must be 12 characters");
            assertDoesNotThrow(() -> ValidationUtil.validateStrongPassword(tempPassword),
                    "Temporary password must strictly satisfy strong password complexity policy");

            // Verify linked user account
            User linkedUser = userDAO.findById(conn, result.getStudent().getUserId());
            assertNotNull(linkedUser, "Linked user account must exist in users table");
            assertTrue(com.college.util.UsernameUtil.isValidInstitutionalUsername(linkedUser.getUsername()) || "STU2026888".equals(linkedUser.getUsername()),
                    "Username must be valid institutional username or registration number");
            assertEquals(Role.STUDENT, linkedUser.getRole(), "User role must be STUDENT");
            assertTrue(linkedUser.isMustChangePassword(),
                    "must_change_password flag must be set to true on auto-provisioned accounts");
            assertTrue(linkedUser.isActive(), "Account must be active");
            assertTrue(PasswordUtil.verifyPassword(tempPassword, linkedUser.getPasswordHash()),
                    "Temporary password must verify against stored BCrypt hash");
        }
    }

    @Test
    @DisplayName("Verify generated temporary passwords consistently satisfy complexity policy (100 iterations)")
    void testTemporaryPasswordSatisfiesStrictComplexityPolicy() {
        for (int i = 0; i < 100; i++) {
            String tempPass = PasswordUtil.generateTemporaryPassword();
            assertEquals(12, tempPass.length(), "Each password must be 12 characters");
            assertDoesNotThrow(() -> ValidationUtil.validateStrongPassword(tempPass),
                    "Iteration " + i + " failed complexity check: " + tempPass);

            boolean hasUpper = false;
            boolean hasLower = false;
            boolean hasDigit = false;
            boolean hasSpecial = false;
            for (char c : tempPass.toCharArray()) {
                if (Character.isUpperCase(c)) hasUpper = true;
                else if (Character.isLowerCase(c)) hasLower = true;
                else if (Character.isDigit(c)) hasDigit = true;
                else if (!Character.isWhitespace(c)) hasSpecial = true;
            }
            assertTrue(hasUpper, "Password must have uppercase: " + tempPass);
            assertTrue(hasLower, "Password must have lowercase: " + tempPass);
            assertTrue(hasDigit, "Password must have digit: " + tempPass);
            assertTrue(hasSpecial, "Password must have special symbol: " + tempPass);
        }
    }

    @Test
    @DisplayName("Prevent duplicate student registration ID or username collision")
    void testDuplicateUsernameOrStudentIdPrevention() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = createDepartment(conn);

            Student s1 = buildSampleStudent(dept.getId(), "STU2026DUP", "student1@campus.edu");
            studentService.createStudentWithAutoAccount(admin, s1);

            // Attempt duplicate student ID
            Student s2 = buildSampleStudent(dept.getId(), "STU2026DUP", "student2@campus.edu");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> studentService.createStudentWithAutoAccount(admin, s2));
            assertTrue(ex.getMessage().contains("already exists"),
                    "Should reject duplicate student ID: " + ex.getMessage());
        }
    }

    @Test
    @DisplayName("Enforce first-login mandatory password change: gate access and update credentials")
    void testFirstLoginMandatoryPasswordChangeEnforcement() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = createDepartment(conn);

            Student s = buildSampleStudent(dept.getId(), "STU2026LOGIN", "login.test@campus.edu");
            StudentService.StudentCreationResult result = studentService.createStudentWithAutoAccount(admin, s);
            String tempPass = result.getTemporaryPassword();

            // 1. Authenticate with temporary password
            User sessionUser = authService.login("STU2026LOGIN", tempPass);
            assertNotNull(sessionUser, "Login with temporary password must succeed");
            assertTrue(sessionUser.isMustChangePassword(),
                    "Session user must reflect must_change_password == true");

            // 2. Gating: requireRole must block access while must_change_password is true
            AuthenticationException gateEx = assertThrows(AuthenticationException.class,
                    () -> AuthService.requireRole(sessionUser, Role.STUDENT));
            assertTrue(gateEx.getMessage().contains("Password change required"),
                    "Access must be blocked before password change: " + gateEx.getMessage());

            // 3. Complete forced password change
            String newPassword = "StudentNewSecurePass@2026";
            authService.forceChangePassword(sessionUser, newPassword);

            // 4. Verification: session flag cleared, requireRole succeeds
            assertFalse(sessionUser.isMustChangePassword(), "Session flag must be cleared");
            assertDoesNotThrow(() -> AuthService.requireRole(sessionUser, Role.STUDENT),
                    "Access must now be permitted after password change");

            // 5. Verification: Database reflects change
            User dbUser = userDAO.findById(conn, sessionUser.getId());
            assertFalse(dbUser.isMustChangePassword(), "DB must_change_password must be 0");
            assertTrue(PasswordUtil.verifyPassword(newPassword, dbUser.getPasswordHash()),
                    "New password hash must match in DB");

            // 6. Verification: Old temporary password no longer works
            assertThrows(AuthenticationException.class,
                    () -> authService.login("STU2026LOGIN", tempPass),
                    "Old temporary password must be rejected");

            // 7. Verification: New password logs in successfully with mustChangePassword == false
            User newSession = authService.login("STU2026LOGIN", newPassword);
            assertFalse(newSession.isMustChangePassword(), "New session must have mustChangePassword == false");
        }
    }

    @Test
    @DisplayName("Self-service student profile password change with strict validation")
    void testStudentSelfServicePasswordChange() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = createDepartment(conn);

            Student s = buildSampleStudent(dept.getId(), "STU2026SELF", "self.service@campus.edu");
            StudentService.StudentCreationResult res = studentService.createStudentWithAutoAccount(admin, s);
            User studentUser = userDAO.findById(conn, res.getStudent().getUserId());

            // Clear first-login requirement to simulate normal active student
            userDAO.setMustChangePassword(conn, studentUser.getId(), false);
            studentUser.setMustChangePassword(false);

            String initialPass = res.getTemporaryPassword();
            String newValidPass = "SelfUpdatedPassword#2026";

            // Reject invalid current password
            assertThrows(ValidationException.class,
                    () -> studentService.changeOwnPassword(studentUser, "WrongPassword@1", newValidPass),
                    "Must reject incorrect current password");

            // Reject weak new password
            assertThrows(ValidationException.class,
                    () -> studentService.changeOwnPassword(studentUser, initialPass, "weak"),
                    "Must reject weak new password");

            // Reject identical new password
            assertThrows(ValidationException.class,
                    () -> studentService.changeOwnPassword(studentUser, initialPass, initialPass),
                    "Must reject identical new password");

            // Successful self-service password update
            assertDoesNotThrow(() -> studentService.changeOwnPassword(studentUser, initialPass, newValidPass),
                    "Valid password change must succeed");

            // Verify in DB
            User updated = userDAO.findById(conn, studentUser.getId());
            assertTrue(PasswordUtil.verifyPassword(newValidPass, updated.getPasswordHash()));

            // Verify non-student cannot use self-service endpoint
            assertThrows(AuthenticationException.class,
                    () -> studentService.changeOwnPassword(admin, "pass", newValidPass),
                    "Non-student accounts cannot use student self-service password change");
        }
    }

    @Test
    @DisplayName("Admin resets student password: generates new temporary password and sets must_change_password")
    void testAdminResetStudentPassword() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = createDepartment(conn);

            Student s = buildSampleStudent(dept.getId(), "STU2026RST", "reset.test@campus.edu");
            StudentService.StudentCreationResult res = studentService.createStudentWithAutoAccount(admin, s);
            int studentId = res.getStudent().getId();

            // Clear must_change_password initially
            userDAO.setMustChangePassword(conn, res.getStudent().getUserId(), false);

            // Admin resets password
            String newTempPass = studentService.resetStudentPassword(admin, studentId);
            assertNotNull(newTempPass);
            assertEquals(12, newTempPass.length());
            assertDoesNotThrow(() -> ValidationUtil.validateStrongPassword(newTempPass));

            // Verify DB state
            User updatedUser = userDAO.findById(conn, res.getStudent().getUserId());
            assertTrue(updatedUser.isMustChangePassword(),
                    "Admin reset must set must_change_password = true");
            assertTrue(PasswordUtil.verifyPassword(newTempPass, updatedUser.getPasswordHash()),
                    "New temporary password must authenticate");

            // Unauthorized attempt by student or faculty rejected
            User studentUser = new User("other_stu", "$2a$12$hash", Role.STUDENT);
            studentUser.setActive(true);
            User savedOther = userDAO.insert(conn, studentUser);
            assertThrows(AuthenticationException.class,
                    () -> studentService.resetStudentPassword(savedOther, studentId),
                    "Non-admin cannot reset student passwords");
        }
    }

    @Test
    @DisplayName("Admin updates student username with duplicate prevention")
    void testAdminUpdateStudentUsername() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = createDepartment(conn);

            Student s1 = buildSampleStudent(dept.getId(), "STU2026UN1", "user1@campus.edu");
            StudentService.StudentCreationResult res1 = studentService.createStudentWithAutoAccount(admin, s1);

            Student s2 = buildSampleStudent(dept.getId(), "STU2026UN2", "user2@campus.edu");
            studentService.createStudentWithAutoAccount(admin, s2);

            // Update s1 username
            studentService.updateStudentUsername(admin, res1.getStudent().getId(), "custom.username.ada");
            User updated = userDAO.findById(conn, res1.getStudent().getUserId());
            assertEquals("custom.username.ada", updated.getUsername());

            // Attempt duplicate username (same as s2's username)
            assertThrows(ValidationException.class,
                    () -> studentService.updateStudentUsername(admin, res1.getStudent().getId(), "STU2026UN2"),
                    "Should reject collision with existing username");

            // Non-admin rejected
            assertThrows(AuthenticationException.class,
                    () -> studentService.updateStudentUsername(updated, res1.getStudent().getId(), "hacker"),
                    "Non-admin cannot update usernames");
        }
    }

    @Test
    @DisplayName("Admin forces password change flag on student account")
    void testAdminForcePasswordChange() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = createDepartment(conn);

            Student s = buildSampleStudent(dept.getId(), "STU2026FRC", "force@campus.edu");
            StudentService.StudentCreationResult res = studentService.createStudentWithAutoAccount(admin, s);
            userDAO.setMustChangePassword(conn, res.getStudent().getUserId(), false);

            User userBefore = userDAO.findById(conn, res.getStudent().getUserId());
            assertFalse(userBefore.isMustChangePassword());

            studentService.forcePasswordChange(admin, res.getStudent().getId());

            User userAfter = userDAO.findById(conn, res.getStudent().getUserId());
            assertTrue(userAfter.isMustChangePassword(),
                    "must_change_password must be set to true by admin");
        }
    }

    @Test
    @DisplayName("Maintain backward compatibility with existing student credentials (ajones / password123)")
    void testExistingStudentAccountCompatibility() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            // Seed a legacy style account
            Department dept = createDepartment(conn);
            String legacyHash = PasswordUtil.hashPassword("password123");
            User legacyUser = new User("ajones", legacyHash, Role.STUDENT);
            legacyUser.setMustChangePassword(false);
            legacyUser.setActive(true);
            User savedUser = userDAO.insert(conn, legacyUser);

            Student legacyStudent = new Student();
            legacyStudent.setUserId(savedUser.getId());
            legacyStudent.setDepartmentId(dept.getId());
            legacyStudent.setStudentId("STU2026001");
            legacyStudent.setFirstName("Alice");
            legacyStudent.setLastName("Jones");
            legacyStudent.setEmail("ajones@college.edu");
            legacyStudent.setAdmissionDate("2026-08-01");
            legacyStudent.setSemester("1");
            legacyStudent.setActive(true);
            studentDAO.insert(conn, legacyStudent);

            // Authenticate
            User sessionUser = authService.login("ajones", "password123");
            assertNotNull(sessionUser);
            assertEquals("ajones", sessionUser.getUsername());
            assertFalse(sessionUser.isMustChangePassword(),
                    "Existing active student accounts must NOT be forced to change password");
            assertDoesNotThrow(() -> AuthService.requireRole(sessionUser, Role.STUDENT),
                    "Existing student can access system without interruption");
        }
    }
}

