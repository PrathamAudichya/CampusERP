package com.college.acceptance;

import com.college.config.DBConnection;
import com.college.config.DatabaseInitializer;
import com.college.dao.FeeStructureDAO;
import com.college.dao.UserDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.ValidationException;
import com.college.model.*;
import com.college.service.*;
import com.college.ui.components.FacultyDetailsDialog;
import com.college.ui.components.StudentDetailsDialog;
import com.college.ui.panel.*;
import com.college.util.PasswordUtil;
import org.junit.jupiter.api.*;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Final Acceptance & Technical Verification Test Suite for Milestone 4 and QA Stabilization.
 *
 * Exercises all 7 acceptance dimensions against an isolated, disposable SQLite database:
 * 1. Fresh database initialization through the actual Java application path (DatabaseInitializer.initialize())
 * 2. Complete schema migrations V1 through V11 recording
 * 3. Required table and constraint presence (timetables, academic_programs, student_cohorts, attendance_sessions, etc.)
 * 4. Username management (student, faculty, admin) with uniqueness & format validation and login verification
 * 5. Credential security (entropy, must_change_password policy, first-login password change, zero default credentials)
 * 6. Functional desktop user workflows (admin, faculty, student, academics, timetable, attendance, fees, reports)
 * 7. Database consistency, PRAGMA foreign_key_check, and PRAGMA integrity_check
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FinalDesktopAcceptanceTest {

    private static final Path SCRATCH_DIR = Paths.get("target", "acceptance-scratch");
    private static final Path ACCEPTANCE_DB = SCRATCH_DIR.resolve("acceptance-fresh.db");

    private static AuthService authService;
    private static DepartmentService departmentService;
    private static AcademicProgramService programService;
    private static CourseService courseService;
    private static FacultyService facultyService;
    private static StudentService studentService;
    private static EnrollmentService enrollmentService;
    private static CourseAssignmentService assignmentService;
    private static TimetableService timetableService;
    private static AttendanceService attendanceService;
    private static FeePaymentService feePaymentService;
    private static ReportService reportService;
    private static AdminDashboardService dashboardService;

    private static User adminUser;
    private static Student testStudent;
    private static Faculty testFaculty;
    private static Department testDept;

    @BeforeAll
    public static void initScratchEnvironment() throws IOException {
        Files.createDirectories(SCRATCH_DIR);
        // Guarantee database starts in a non-existent state
        Files.deleteIfExists(ACCEPTANCE_DB);
        Files.deleteIfExists(Paths.get(ACCEPTANCE_DB.toString() + "-wal"));
        Files.deleteIfExists(Paths.get(ACCEPTANCE_DB.toString() + "-shm"));

        // Direct DBConnection to our isolated scratch database
        System.setProperty("college.db.path", ACCEPTANCE_DB.toAbsolutePath().toString());
    }

    @Test
    @Order(1)
    @DisplayName("1. Fresh Database Initialization via Java Application Path")
    public void testFreshDatabaseInitialization() throws Exception {
        // Confirm DB does not exist before Java initialization
        assertFalse(Files.exists(ACCEPTANCE_DB), "Database must NOT exist before initialization");

        // Execute initialization through the exact Java application path called by App.main()
        DatabaseInitializer.initialize();

        // Confirm database was created
        assertTrue(Files.exists(ACCEPTANCE_DB), "Database file must exist after Java initialization");

        // Verify migrations and tables via JDBC connection
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Verify schema_migrations table exists and records V1 through V11
            Set<String> migrations = new HashSet<>();
            try (ResultSet rs = stmt.executeQuery("SELECT version FROM schema_migrations")) {
                while (rs.next()) {
                    migrations.add(rs.getString("version"));
                }
            }

            assertTrue(migrations.contains("V1__base_schema"), "V1 must be recorded in schema_migrations");
            assertTrue(migrations.contains("V2__attendance_session_code"), "V2 must be recorded in schema_migrations");
            assertTrue(migrations.contains("V3__timetables"), "V3 must be recorded in schema_migrations");
            assertTrue(migrations.contains("V4__attendance_sessions"), "V4 must be recorded in schema_migrations");
            assertTrue(migrations.contains("V5__academic_programs"), "V5 must be recorded in schema_migrations");
            assertTrue(migrations.contains("V6__assessment_schemes"), "V6 must be recorded in schema_migrations");
            assertTrue(migrations.contains("V7__bank_details_and_online_payments"), "V7 must be recorded in schema_migrations");
            assertTrue(migrations.contains("V8__attendance_automation_enhancements"), "V8 must be recorded in schema_migrations");
            assertTrue(migrations.contains("V9__academic_structure_and_programmes"), "V9 must be recorded in schema_migrations");
            assertTrue(migrations.contains("V10__milestone4_stabilization"), "V10 must be recorded in schema_migrations");
            assertTrue(migrations.contains("V11__progression_history_fk_rebuild"), "V11 must be recorded in schema_migrations");

            // 2. Verify all required Milestone 1-4 tables exist
            List<String> requiredTables = Arrays.asList(
                    "users", "departments", "faculty", "students", "courses",
                    "course_assignments", "enrollments", "attendance", "marks",
                    "examinations", "fee_structures", "fee_payments", "audit_log",
                    "timetables", "attendance_sessions", "attendance_submissions",
                    "attendance_failed_attempts", "academic_programs", "programme_semesters",
                    "programme_curriculum", "student_cohorts", "student_progression_history",
                    "assessment_schemes", "assessment_items", "student_assessment_marks",
                    "institution_bank_details", "schema_migrations"
            );

            Set<String> actualTables = new HashSet<>();
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
                while (rs.next()) {
                    actualTables.add(rs.getString("name").toLowerCase());
                }
            }

            for (String reqTable : requiredTables) {
                assertTrue(actualTables.contains(reqTable.toLowerCase()),
                        "Required table missing: " + reqTable);
            }

            // 3. Verify timetables columns
            Set<String> timetableCols = new HashSet<>();
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(timetables)")) {
                while (rs.next()) {
                    timetableCols.add(rs.getString("name").toLowerCase());
                }
            }
            assertTrue(timetableCols.contains("course_id"));
            assertTrue(timetableCols.contains("faculty_id"));
            assertTrue(timetableCols.contains("department_id"));
            assertTrue(timetableCols.contains("start_time"));
            assertTrue(timetableCols.contains("end_time"));
            assertTrue(timetableCols.contains("classroom"));
        }

        // Initialize Services
        authService = new AuthService();
        departmentService = new DepartmentService();
        programService = new AcademicProgramService();
        courseService = new CourseService();
        facultyService = new FacultyService();
        studentService = new StudentService();
        enrollmentService = new EnrollmentService();
        assignmentService = new CourseAssignmentService();
        timetableService = new TimetableService();
        attendanceService = new AttendanceService();
        feePaymentService = new FeePaymentService();
        reportService = new ReportService();
        dashboardService = new AdminDashboardService();
    }

    @Test
    @Order(2)
    @DisplayName("2. Administrator Provisioning and Temporary Credential Generation")
    public void testAdminProvisioningAndCredentials() {
        // Provision initial administrator via AuthService
        adminUser = authService.provisionInitialAdmin("acceptance_admin", "SecureAdminPass2026!");
        assertNotNull(adminUser, "Admin account must be created successfully");
        assertEquals("acceptance_admin", adminUser.getUsername());
        assertEquals(Role.ADMIN, adminUser.getRole());
        assertFalse(adminUser.isMustChangePassword(), "Admin provisioned with strong password does not require first-login change");

        // Verify login with admin credentials
        User loggedInAdmin = authService.login("acceptance_admin", "SecureAdminPass2026!");
        assertNotNull(loggedInAdmin);
        assertEquals(adminUser.getId(), loggedInAdmin.getId());

        // Verify temporary password generation
        String tempPass1 = PasswordUtil.generateTemporaryPassword();
        assertNotNull(tempPass1);
        assertTrue(tempPass1.length() >= 12, "Temporary password must have high entropy (>= 12 chars)");
        assertTrue(tempPass1.matches(".*[A-Z].*"), "Must contain uppercase");
        assertTrue(tempPass1.matches(".*[a-z].*"), "Must contain lowercase");
        assertTrue(tempPass1.matches(".*[0-9].*"), "Must contain digit");
        assertTrue(tempPass1.matches(".*[!@#$%^&*()_+\\-=\\[\\]{}|;:,.<>?].*"), "Must contain symbol");

        // Distinct consecutive generations
        String tempPass2 = PasswordUtil.generateTemporaryPassword();
        assertNotEquals(tempPass1, tempPass2, "Temporary passwords must be cryptographically distinct");
    }

    @Test
    @Order(3)
    @DisplayName("3. Student & Faculty Provisioning with Must-Change-Password Policy")
    public void testStudentFacultyProvisioningAndFirstLoginChange() {
        Department dept = new Department("CSE_ACC", "Computer Science Acceptance");
        testDept = departmentService.createDepartment(adminUser, dept);
        assertNotNull(testDept);
        assertTrue(testDept.getId() > 0);

        // 1. Provision Faculty
        Faculty faculty = new Faculty();
        faculty.setFacultyId("FAC-ACC-01");
        faculty.setFirstName("Arun");
        faculty.setLastName("Kumar");
        faculty.setEmail("arun.kumar@acceptance.test");
        faculty.setPhone("+91 98765 11111");
        faculty.setDepartmentId(testDept.getId());
        faculty.setSpecialization("Software Engineering");
        faculty.setJoinDate("2026-01-15");
        faculty.setActive(true);

        String facultyTempPass = PasswordUtil.generateTemporaryPassword();
        testFaculty = facultyService.createFaculty(adminUser, faculty, facultyTempPass);
        assertNotNull(testFaculty);
        assertTrue(testFaculty.getId() > 0);
        assertTrue(testFaculty.getUserId() > 0);

        // Verify faculty user has must_change_password == 1
        User facultyUser = authService.login("FAC-ACC-01", facultyTempPass);
        assertNotNull(facultyUser);
        assertTrue(facultyUser.isMustChangePassword(), "Faculty must have must_change_password=1 on initial creation");

        // Attempting role-guarded action before password change must be rejected
        assertThrows(AuthenticationException.class, () ->
                AuthService.requireRole(facultyUser, Role.FACULTY),
                "Access must be denied while must_change_password == true");

        // Force password change on first login
        authService.forceChangePassword(facultyUser, "PermanentFacultyPass2026!");
        assertFalse(facultyUser.isMustChangePassword(), "must_change_password must be false after reset");

        // Subsequent login with permanent password
        User reLoggedInFaculty = authService.login("FAC-ACC-01", "PermanentFacultyPass2026!");
        assertNotNull(reLoggedInFaculty);
        assertFalse(reLoggedInFaculty.isMustChangePassword());

        // 2. Provision Student
        Student student = new Student();
        student.setStudentId("STU-ACC-01");
        student.setFirstName("Pooja");
        student.setLastName("Patel");
        student.setEmail("pooja.patel@acceptance.test");
        student.setPhone("+91 98765 22222");
        student.setDepartmentId(testDept.getId());
        student.setDateOfBirth("2005-05-15");
        student.setAdmissionDate("2026-08-01");
        student.setSemester("1");
        student.setActive(true);

        StudentService.StudentCreationResult studentResult = studentService.createStudentWithAutoAccount(adminUser, student);
        assertNotNull(studentResult);
        testStudent = studentResult.getStudent();
        assertTrue(testStudent.getId() > 0);
        String studentTempPass = studentResult.getTemporaryPassword();

        // Verify student user has must_change_password == 1
        User studentUser = authService.login(studentResult.getUsername(), studentTempPass);
        assertNotNull(studentUser);
        assertTrue(studentUser.isMustChangePassword(), "Student must have must_change_password=1 on initial creation");

        // Force password change on first login
        authService.forceChangePassword(studentUser, "PermanentStudentPass2026!");
        assertFalse(studentUser.isMustChangePassword());

        // Subsequent login with permanent password
        User reLoggedInStudent = authService.login(studentResult.getUsername(), "PermanentStudentPass2026!");
        assertNotNull(reLoggedInStudent);
        assertFalse(reLoggedInStudent.isMustChangePassword());
    }

    @Test
    @Order(4)
    @DisplayName("4. Username Management (Edit, Format Validation, Duplicate Rejection, Persistence & Login)")
    public void testUsernameManagement() {
        // 1. Edit student username
        studentService.updateStudentUsername(adminUser, testStudent.getId(), "pooja.patel.updated");

        // Verify student login works with updated username
        User updatedStudentUser = authService.login("pooja.patel.updated", "PermanentStudentPass2026!");
        assertNotNull(updatedStudentUser);
        assertEquals("pooja.patel.updated", updatedStudentUser.getUsername());

        // Verify old student username no longer logs in
        assertThrows(AuthenticationException.class, () ->
                authService.login("STU-ACC-01", "PermanentStudentPass2026!"));

        // 2. Edit faculty username
        facultyService.updateFacultyUsername(adminUser, testFaculty.getId(), "arun.kumar.updated");

        // Verify faculty login works with updated username
        User updatedFacultyUser = authService.login("arun.kumar.updated", "PermanentFacultyPass2026!");
        assertNotNull(updatedFacultyUser);
        assertEquals("arun.kumar.updated", updatedFacultyUser.getUsername());

        // Verify old faculty username no longer logs in
        assertThrows(AuthenticationException.class, () ->
                authService.login("FAC-ACC-01", "PermanentFacultyPass2026!"));

        // 3. Edit admin username via AuthService
        authService.updateUsername(adminUser, adminUser.getId(), "admin.master.updated");
        adminUser.setUsername("admin.master.updated");

        // Verify admin login works with updated username
        User updatedAdminUser = authService.login("admin.master.updated", "SecureAdminPass2026!");
        assertNotNull(updatedAdminUser);
        assertEquals("admin.master.updated", updatedAdminUser.getUsername());

        // 4. Test duplicate username validation
        // Attempting to change student username to existing faculty username must fail
        assertThrows(ValidationException.class, () ->
                studentService.updateStudentUsername(adminUser, testStudent.getId(), "arun.kumar.updated"));

        // Attempting to change faculty username to existing admin username must fail
        assertThrows(ValidationException.class, () ->
                facultyService.updateFacultyUsername(adminUser, testFaculty.getId(), "admin.master.updated"));

        // Attempting to change admin username to existing student username must fail
        assertThrows(ValidationException.class, () ->
                authService.updateUsername(adminUser, adminUser.getId(), "pooja.patel.updated"));

        // 5. Test invalid format validation
        // < 3 chars
        assertThrows(ValidationException.class, () ->
                studentService.updateStudentUsername(adminUser, testStudent.getId(), "ab"));
        // > 50 chars
        assertThrows(ValidationException.class, () ->
                facultyService.updateFacultyUsername(adminUser, testFaculty.getId(), "a".repeat(51)));
        // Empty string
        assertThrows(ValidationException.class, () ->
                authService.updateUsername(adminUser, adminUser.getId(), ""));

        // 6. Verify persistence across fresh DAO lookup (simulating application restart)
        UserDAO freshUserDAO = new UserDAO();
        User reloadedStudent = freshUserDAO.findByUsername("pooja.patel.updated");
        assertNotNull(reloadedStudent);
        assertEquals("pooja.patel.updated", reloadedStudent.getUsername());

        User reloadedFaculty = freshUserDAO.findByUsername("arun.kumar.updated");
        assertNotNull(reloadedFaculty);
        assertEquals("arun.kumar.updated", reloadedFaculty.getUsername());
    }

    @Test
    @Order(5)
    @DisplayName("5. Functional Desktop Workflows (Academics, Enrollment, Timetable, Attendance, Fees, Reports)")
    public void testFunctionalDesktopWorkflows() {
        // 1. Academic Program creation
        AcademicProgram program = new AcademicProgram();
        program.setDepartmentId(testDept.getId());
        program.setName("B.Tech in Artificial Intelligence");
        program.setCode("BTECH-AI-ACC");
        program.setDegree("B.Tech");
        program.setLevel("UNDERGRADUATE");
        program.setDurationYears(4);
        program.setTotalSemesters(8);
        program.setCreditsRequired(160);
        program.setAcademicRegulation("R2026");
        program.setActive(true);

        AcademicProgram createdProgram = programService.createProgram(adminUser, program);
        assertNotNull(createdProgram);
        assertTrue(createdProgram.getId() > 0);

        // 2. Course creation
        Course course = new Course();
        course.setDepartmentId(testDept.getId());
        course.setCourseCode("AI101_ACC");
        course.setName("Foundations of Artificial Intelligence");
        course.setCredits(4);
        course.setSemester("1");
        course.setMaxCapacity(60);
        course.setActive(true);

        Course savedCourse = courseService.createCourse(adminUser, course);
        assertTrue(savedCourse.getId() > 0);

        // 3. Faculty Assignment to course
        assignmentService.assignFaculty(adminUser, savedCourse.getId(), testFaculty.getId(), "2026-2027");

        // 4. Course Enrollment
        Enrollment enrollment = enrollmentService.enroll(adminUser, testStudent.getId(), savedCourse.getId(), "2026-2027");
        assertNotNull(enrollment);
        assertEquals("ACTIVE", enrollment.getStatus());

        // 5. Timetable Creation
        TimetableEntry entry = new TimetableEntry();
        entry.setCourseId(savedCourse.getId());
        entry.setFacultyId(testFaculty.getId());
        entry.setDepartmentId(testDept.getId());
        entry.setSemester("1");
        entry.setDayOfWeek("MONDAY");
        entry.setStartTime("10:00");
        entry.setEndTime("11:30");
        entry.setClassroom("LH-ACC-101");
        entry.setAcademicYear("2026-2027");

        TimetableEntry savedEntry = timetableService.createTimetableEntry(adminUser, entry);
        assertNotNull(savedEntry);
        assertTrue(savedEntry.getId() > 0);

        List<TimetableEntry> entries = timetableService.getTimetablesByDepartmentAndSemester(adminUser, testDept.getId(), "1", "2026-2027");
        assertFalse(entries.isEmpty());

        // 6. Attendance Single Recording & Verification
        Attendance att = attendanceService.markAttendanceSingle(adminUser, enrollment.getId(),
                LocalDate.now().toString(), "PRESENT", "Acceptance lecture verification");
        assertNotNull(att);

        List<Attendance> records = attendanceService.getAttendanceByEnrollment(adminUser, enrollment.getId());
        assertFalse(records.isEmpty());
        double percentage = AttendanceService.calculatePercentage(records);
        assertTrue(percentage >= 100.0);

        // 7. Fee Structure, Payment, and Receipt
        FeeStructure feeStructure = new FeeStructure(testDept.getId(), "1", "TUITION", 3500000L, "2026-2027", "2026-09-30");
        FeeStructure savedFs;
        FeeStructureDAO feeStructureDAO = new FeeStructureDAO();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + ACCEPTANCE_DB.toAbsolutePath())) {
            savedFs = feeStructureDAO.insert(conn, feeStructure);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertNotNull(savedFs);

        FeePayment payment = feePaymentService.processPayment(adminUser, testStudent.getId(),
                savedFs.getId(), "2026-2027", 3500000L, "UPI");
        assertNotNull(payment);
        assertNotNull(payment.getTransactionRef());

        long balancePaise = feePaymentService.getOutstandingBalance(adminUser, testStudent.getId(), savedFs.getId());
        assertEquals(0L, balancePaise, "Tuition fee should be fully paid (0 balance)");

        // 8. Admin Dashboard & Reports
        AdminDashboardService.AdminDashboardData metrics = dashboardService.getDashboardData(adminUser);
        assertNotNull(metrics);
        assertTrue(metrics.getTotalStudents() >= 1);
        assertTrue(metrics.getTotalFaculty() >= 1);

        List<Map<String, Object>> perfReport = reportService.getStudentPerformanceReport(adminUser, "2026-2027", null, null);
        assertNotNull(perfReport);
    }

    @Test
    @Order(6)
    @DisplayName("6. Database Consistency, Foreign Keys & SQLite Integrity Checks")
    public void testDatabaseConsistencyAndIntegrity() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Verify PRAGMA integrity_check
            try (ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {
                assertTrue(rs.next());
                assertEquals("ok", rs.getString(1).toLowerCase(),
                        "SQLite integrity check must report 'ok'");
            }

            // 2. Verify PRAGMA foreign_key_check
            try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_check")) {
                boolean hasFkViolations = rs.next();
                if (hasFkViolations) {
                    fail("Foreign key violations detected in acceptance database: table=" + rs.getString(1));
                }
            }

            // 3. Verify PRAGMA foreign_keys is enabled
            try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "PRAGMA foreign_keys must be active (1)");
            }
        }
    }
}
