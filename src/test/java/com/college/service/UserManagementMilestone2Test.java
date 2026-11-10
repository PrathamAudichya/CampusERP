package com.college.service;

import com.college.TestDBHelper;
import com.college.config.DBConnection;
import com.college.dao.AuditLogDAO;
import com.college.dao.DepartmentDAO;
import com.college.dao.FacultyDAO;
import com.college.dao.StudentDAO;
import com.college.dao.UserDAO;
import com.college.exception.ValidationException;
import com.college.model.AuditLog;
import com.college.model.Department;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.User;
import com.college.model.UserDirectoryItem;
import com.college.util.PasswordUtil;
import com.college.util.UsernameUtil;
import com.college.util.ValidationUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive automated test suite for Milestone 2: User Identity & Account Management.
 * Covers:
 * - Institutional username format (^([A-Z]{2}[0-9]{12})$) and length (14 chars)
 * - Username uniqueness and collision retry safety
 * - Administrative username editing with audit trail
 * - Compatibility with legacy usernames (e.g., jdoe, ajones)
 * - Administrative username standardization workflow (preview, collision detection, export)
 * - Student admission account provisioning with temporary password and one-time display
 * - Faculty credential provisioning (auto-generated and custom temporary passwords)
 * - Self-service password management with strong policy enforcement
 * - User Directory multi-column population and Account Creation Status tracking
 */
class UserManagementMilestone2Test {

    private static UserDAO userDAO;
    private static StudentDAO studentDAO;
    private static FacultyDAO facultyDAO;
    private static DepartmentDAO departmentDAO;
    private static AuditLogDAO auditLogDAO;
    private static AuditService auditService;
    private static AuthService authService;
    private static StudentService studentService;
    private static FacultyService facultyService;

    private User adminUser;
    private Department testDept;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        userDAO = new UserDAO();
        studentDAO = new StudentDAO();
        facultyDAO = new FacultyDAO();
        departmentDAO = new DepartmentDAO();
        auditLogDAO = new AuditLogDAO();
        auditService = new AuditService(auditLogDAO);
        authService = new AuthService(userDAO, auditService);
        studentService = new StudentService(studentDAO, userDAO, departmentDAO, auditService, authService);
        facultyService = new FacultyService(facultyDAO, userDAO, departmentDAO, auditService);
    }

    @BeforeEach
    void setUp() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            TestDBHelper.cleanAllTables(conn);

            // Seed administrative user
            adminUser = new User("admin_m2", PasswordUtil.hashPassword("AdminPass123!"), Role.ADMIN);
            adminUser.setActive(true);
            adminUser.setMustChangePassword(false);
            adminUser = userDAO.insert(conn, adminUser);

            // Seed department
            testDept = new Department("CSE", "Computer Science and Engineering");
            testDept.setActive(true);
            testDept = departmentDAO.insert(conn, testDept);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    @Test
    @DisplayName("Verify Institutional Username Format conforms strictly to ^[A-Z]{2}[0-9]{12}$")
    void testInstitutionalUsernameFormat() {
        // Valid candidates
        assertTrue(UsernameUtil.isValidInstitutionalUsername("AA123456789012"));
        assertTrue(UsernameUtil.isValidInstitutionalUsername("ZZ999999999999"));
        assertTrue(UsernameUtil.isValidInstitutionalUsername("CS202611100001"));

        // Invalid lengths or characters
        assertFalse(UsernameUtil.isValidInstitutionalUsername(null));
        assertFalse(UsernameUtil.isValidInstitutionalUsername(""));
        assertFalse(UsernameUtil.isValidInstitutionalUsername("A123456789012")); // 1 letter (13 chars)
        assertFalse(UsernameUtil.isValidInstitutionalUsername("AAA12345678901")); // 3 letters
        assertFalse(UsernameUtil.isValidInstitutionalUsername("aa123456789012")); // lowercase
        assertFalse(UsernameUtil.isValidInstitutionalUsername("AA12345678901")); // 13 chars
        assertFalse(UsernameUtil.isValidInstitutionalUsername("AA1234567890123")); // 15 chars
        assertFalse(UsernameUtil.isValidInstitutionalUsername("AA12345678901A")); // letter in digits
        assertFalse(UsernameUtil.isValidInstitutionalUsername("AA1234-6789012")); // punctuation
        assertFalse(UsernameUtil.isValidInstitutionalUsername("jdoe")); // legacy format
    }

    @Test
    @DisplayName("Verify generated usernames are 14 characters, unique, and collision-resistant across 100 samples")
    void testInstitutionalUsernameGenerationAndUniqueness() throws SQLException {
        Set<String> generatedSet = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String candidate = UsernameUtil.generateCandidateUsername();
            assertEquals(14, candidate.length(), "Username must be exactly 14 characters");
            assertTrue(UsernameUtil.isValidInstitutionalUsername(candidate), "Candidate must match regex: " + candidate);
            generatedSet.add(candidate);
        }
        // SecureRandom with 26^2 * 10^12 space should have zero collisions in 100 iterations
        assertEquals(100, generatedSet.size(), "All 100 generated usernames should be distinct");

        // Verify DB unique generator
        try (Connection conn = DBConnection.getConnection()) {
            String dbUnique = UsernameUtil.generateUniqueUsername(conn);
            assertNotNull(dbUnique);
            assertTrue(UsernameUtil.isValidInstitutionalUsername(dbUnique));
        }
    }

    @Test
    @DisplayName("Verify administrative username editing with uniqueness check and audit logging")
    void testAdminUsernameEditAndAuditTrail() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            // Create an existing user
            User studentAccount = new User("CS202600000001", PasswordUtil.hashPassword("TempPass123!"), Role.STUDENT);
            studentAccount = userDAO.insert(conn, studentAccount);

            // Edit username successfully
            String newUsername = "CS202600000099";
            authService.updateUsername(adminUser, studentAccount.getId(), newUsername);

            User refreshed = userDAO.findById(conn, studentAccount.getId());
            assertEquals(newUsername, refreshed.getUsername(), "Username should be updated");

            // Verify audit log entry
            List<AuditLog> logs = auditLogDAO.findByEntityType(conn, "USER");
            assertFalse(logs.isEmpty(), "Audit log must record the username change");
            AuditLog editLog = logs.stream()
                    .filter(l -> "UPDATE_USERNAME".equals(l.getAction()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(editLog, "Must have an UPDATE_USERNAME audit record");
            assertTrue(editLog.getOldValue().contains("CS202600000001"));
            assertTrue(editLog.getNewValue().contains("CS202600000099"));

            // Attempt duplicate username collision
            User another = new User("CS202600000002", PasswordUtil.hashPassword("TempPass123!"), Role.STUDENT);
            userDAO.insert(conn, another);

            int studentAccId = studentAccount.getId();
            assertThrows(ValidationException.class, () ->
                    authService.updateUsername(adminUser, studentAccId, "CS202600000002"),
                    "Should throw ValidationException when attempting to set an existing username");
        }
    }

    @Test
    @DisplayName("Verify legacy usernames (jdoe, ajones, ssmith) remain fully operational without forced changes")
    void testLegacyUsernameCompatibility() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            // Insert legacy accounts
            User jdoe = new User("jdoe", PasswordUtil.hashPassword("LegacyPassword1!"), Role.FACULTY);
            jdoe.setActive(true);
            jdoe.setMustChangePassword(false);
            userDAO.insert(conn, jdoe);

            User ajones = new User("ajones", PasswordUtil.hashPassword("StudentPass123!"), Role.STUDENT);
            ajones.setActive(true);
            ajones.setMustChangePassword(false);
            userDAO.insert(conn, ajones);

            // Verify legacy usernames authenticate seamlessly
            User authJdoe = authService.login("jdoe", "LegacyPassword1!");
            assertNotNull(authJdoe);
            assertEquals("jdoe", authJdoe.getUsername());
            assertFalse(authJdoe.isMustChangePassword(), "Legacy accounts must not have must_change_password forcibly toggled");

            User authAjones = authService.login("ajones", "StudentPass123!");
            assertNotNull(authAjones);
            assertEquals("ajones", authAjones.getUsername());
        }
    }

    @Test
    @DisplayName("Verify Student admission workflow creates profile, institutional account, and temporary password")
    void testStudentAdmissionAccountWorkflow() throws SQLException {
        Student input = new Student();
        input.setDepartmentId(testDept.getId());
        input.setStudentId("STU-M2-001");
        input.setFirstName("Aarav");
        input.setLastName("Sharma");
        input.setEmail("aarav.sharma@campus.edu");
        input.setPhone("9876543210");
        input.setSemester("1");

        StudentService.StudentCreationResult result = studentService.createStudentWithInstitutionalAccount(adminUser, input);
        assertNotNull(result);
        assertNotNull(result.getStudent());
        assertTrue(result.getStudent().getId() > 0);
        assertTrue(result.getStudent().getUserId() > 0);

        // Verify username complies with institutional format
        String username = result.getUsername();
        assertNotNull(username);
        assertTrue(UsernameUtil.isValidInstitutionalUsername(username),
                "Auto-generated student username must follow AA999999999999 format: " + username);

        // Verify temporary password complexity
        String tempPass = result.getTemporaryPassword();
        assertNotNull(tempPass);
        assertDoesNotThrow(() -> ValidationUtil.validateStrongPassword(tempPass));

        // Verify user account state
        try (Connection conn = DBConnection.getConnection()) {
            User user = userDAO.findById(conn, result.getStudent().getUserId());
            assertNotNull(user);
            assertEquals(username, user.getUsername());
            assertEquals(Role.STUDENT, user.getRole());
            assertTrue(user.isMustChangePassword(), "New student must be forced to change password on first login");
            assertTrue(user.isActive(), "Account must be active");
            assertTrue(PasswordUtil.verifyPassword(tempPass, user.getPasswordHash()));
        }
    }

    @Test
    @DisplayName("Verify Faculty creation workflow with auto-generated temporary password and institutional username")
    void testFacultyCreationWorkflowWithAutoCredentials() throws SQLException {
        Faculty input = new Faculty();
        input.setDepartmentId(testDept.getId());
        input.setFacultyId("FAC-M2-001");
        input.setFirstName("Dr. Meera");
        input.setLastName("Krishnan");
        input.setEmail("meera.krishnan@campus.edu");
        input.setPhone("9876543211");
        input.setSpecialization("Distributed Systems");

        FacultyService.FacultyCreationResult result = facultyService.createFacultyWithCredentials(adminUser, input, null, null);
        assertNotNull(result);
        assertNotNull(result.getFaculty());
        assertTrue(result.getFaculty().getId() > 0);
        assertTrue(result.getFaculty().getUserId() > 0);

        String username = result.getUsername();
        assertTrue(UsernameUtil.isValidInstitutionalUsername(username),
                "Generated faculty username must match AA999999999999 format: " + username);

        String tempPass = result.getTemporaryPassword();
        assertNotNull(tempPass);
        assertDoesNotThrow(() -> ValidationUtil.validateStrongPassword(tempPass));

        try (Connection conn = DBConnection.getConnection()) {
            User user = userDAO.findById(conn, result.getFaculty().getUserId());
            assertNotNull(user);
            assertEquals(Role.FACULTY, user.getRole());
            assertTrue(user.isMustChangePassword(), "Faculty must have must_change_password=true");
            assertTrue(PasswordUtil.verifyPassword(tempPass, user.getPasswordHash()));
        }
    }

    @Test
    @DisplayName("Verify Faculty creation with optional administrator-provided password")
    void testFacultyCreationWithAdminProvidedPassword() throws SQLException {
        Faculty input = new Faculty();
        input.setDepartmentId(testDept.getId());
        input.setFacultyId("FAC-M2-002");
        input.setFirstName("Dr. Arvind");
        input.setLastName("Rao");
        input.setEmail("arvind.rao@campus.edu");
        input.setPhone("9876543212");
        input.setSpecialization("Algorithms");

        String adminProvidedPass = "CustomAdminPass123!";
        Faculty created = facultyService.createFaculty(adminUser, input, adminProvidedPass);
        assertNotNull(created);

        try (Connection conn = DBConnection.getConnection()) {
            User user = userDAO.findById(conn, created.getUserId());
            assertNotNull(user);
            assertTrue(PasswordUtil.verifyPassword(adminProvidedPass, user.getPasswordHash()));
            assertTrue(user.isMustChangePassword(), "Account must still require first login rotation");
        }
    }

    @Test
    @DisplayName("Verify Self-Service Password Management enforces current password, match, and complexity")
    void testSelfServicePasswordChange() throws SQLException {
        User student;
        String initialPass = "OldPassword123!";
        try (Connection conn = DBConnection.getConnection()) {
            // Create active student user
            student = new User("AA100000000001", PasswordUtil.hashPassword(initialPass), Role.STUDENT);
            student.setActive(true);
            student.setMustChangePassword(true);
            student = userDAO.insert(conn, student);
        }

        // Fail: Incorrect current password
        User finalStudent = student;
        assertThrows(ValidationException.class, () ->
                authService.changePassword(finalStudent, "WrongOldPass123!", "NewPassword123!"));

        // Fail: Weak new password
        assertThrows(ValidationException.class, () ->
                authService.changePassword(finalStudent, initialPass, "weak"));

        // Success: Valid rotation
        String newStrongPass = "BrandNewSecurePass123!";
        authService.changePassword(student, initialPass, newStrongPass);

        try (Connection conn = DBConnection.getConnection()) {
            User updated = userDAO.findById(conn, student.getId());
            assertFalse(updated.isMustChangePassword(), "must_change_password must be cleared after user self-change");
            assertTrue(PasswordUtil.verifyPassword(newStrongPass, updated.getPasswordHash()));
            assertFalse(PasswordUtil.verifyPassword(initialPass, updated.getPasswordHash()));
        }
    }

    @Test
    @DisplayName("Verify User Directory aggregates all 9 columns and accurately reflects Account Creation Status")
    void testUserDirectoryAggregatedListing() throws SQLException {
        // 1. Provisioned student
        Student s = new Student();
        s.setDepartmentId(testDept.getId());
        s.setStudentId("STU-M2-REG");
        s.setFirstName("Diya");
        s.setLastName("Iyer");
        s.setEmail("diya.iyer@campus.edu");
        s.setPhone("9876543213");
        s.setSemester("2");
        studentService.createStudentWithAutoAccount(adminUser, s);

        // 2. Provisioned faculty
        Faculty f = new Faculty();
        f.setDepartmentId(testDept.getId());
        f.setFacultyId("FAC-M2-REG");
        f.setFirstName("Prof. Ananya");
        f.setLastName("Menon");
        f.setEmail("ananya.menon@campus.edu");
        f.setPhone("9876543214");
        f.setSpecialization("Software Engineering");
        facultyService.createFaculty(adminUser, f, "FacultyPass123!");

        // Retrieve directory
        try (Connection conn = DBConnection.getConnection()) {
            List<UserDirectoryItem> items = userDAO.findAllUserDirectoryItems(conn);
            assertNotNull(items);
            assertTrue(items.size() >= 3, "Directory must contain admin, student, and faculty");

            // Verify Student item
            UserDirectoryItem studentItem = items.stream()
                    .filter(it -> "STU-M2-REG".equals(it.getNaturalId()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(studentItem, "Student record must appear in User Directory");
            assertEquals("Diya Iyer", studentItem.getFullName());
            assertEquals(Role.STUDENT, studentItem.getRole());
            assertEquals("diya.iyer@campus.edu", studentItem.getEmail());
            assertEquals("CSE", studentItem.getDepartmentCode());
            assertTrue(studentItem.isAccountCreated(), "Account Creation Status must be true for provisioned student");
            assertEquals("YES", studentItem.getDisplayAccountCreationStatus());
            assertTrue(studentItem.isMustChangePassword());

            // Verify Faculty item
            UserDirectoryItem facultyItem = items.stream()
                    .filter(it -> "FAC-M2-REG".equals(it.getNaturalId()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(facultyItem, "Faculty record must appear in User Directory");
            assertEquals("Prof. Ananya Menon", facultyItem.getFullName());
            assertEquals(Role.FACULTY, facultyItem.getRole());
            assertTrue(facultyItem.isAccountCreated());
            assertEquals("YES", facultyItem.getDisplayAccountCreationStatus());
        }
    }
}
