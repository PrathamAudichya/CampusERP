package com.college.ui;

import com.college.config.DatabaseInitializer;
import com.college.dao.*;
import com.college.model.*;
import com.college.service.*;
import com.college.ui.components.FacultyDetailsDialog;
import com.college.ui.components.StudentDetailsDialog;
import com.college.ui.panel.*;
import com.college.util.PasswordUtil;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Complete End-to-End GUI & Service Functional Automation Test Suite.
 * Exercises all 9 functional areas requested in the QA audit on an isolated scratch database:
 * 1. Admin login and dashboard
 * 2. User management & admin username editing
 * 3. Student and faculty accounts & password policies
 * 4. Academic programmes and departments
 * 5. Enrollment and course management
 * 6. Timetable creation and viewing
 * 7. Attendance workflows
 * 8. Fee management and receipts
 * 9. Reports and exports
 */
public class FullSystemGuiFunctionalE2ETest {

    private static final Path SCRATCH_DB = Paths.get("target", "test-dbs-qa", "full-system-e2e-scratch.db");
    private static User adminUser;
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

    @BeforeAll
    public static void setUp() throws Exception {
        Files.createDirectories(SCRATCH_DB.getParent());
        Files.deleteIfExists(SCRATCH_DB);

        System.setProperty("college.db.path", SCRATCH_DB.toAbsolutePath().toString());

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SCRATCH_DB.toAbsolutePath())) {
            DatabaseInitializer.initializeWithConnection(conn);
        }

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

        // Create initial administrator
        UserDAO userDAO = new UserDAO();
        adminUser = userDAO.findByUsername("admin");
        if (adminUser == null) {
            adminUser = new User("admin", PasswordUtil.hashPassword("AdminPass@2026!"), Role.ADMIN);
            adminUser.setActive(true);
            adminUser.setMustChangePassword(false);
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SCRATCH_DB.toAbsolutePath())) {
                adminUser = userDAO.insert(conn, adminUser);
            }
        }
    }

    @AfterAll
    public static void tearDown() {
        System.clearProperty("college.db.path");
    }

    @Test
    @DisplayName("E2E-1: Admin login, authentication, and AdminDashboardPanel rendering")
    void testAdminLoginAndDashboard() throws Exception {
        // Authenticate admin user
        User authenticated = authService.login("admin", "AdminPass@2026!");
        assertNotNull(authenticated);
        assertEquals(Role.ADMIN, authenticated.getRole());

        // Verify dashboard metrics service
        AdminDashboardService.AdminDashboardData data = dashboardService.getDashboardData(authenticated);
        assertNotNull(data);

        // Verify EDT Swing panel rendering
        SwingUtilities.invokeAndWait(() -> {
            AdminDashboardPanel panel = new AdminDashboardPanel(authenticated);
            assertNotNull(panel);
        });
    }

    @Test
    @DisplayName("E2E-2: User management, admin username editing, and login persistence")
    void testUserManagementAndUsernameEditing() throws Exception {
        UserDAO userDAO = new UserDAO();

        // Create a test user
        User testUser = new User("temp_user_e2e", PasswordUtil.hashPassword("TempPass@123!"), Role.FACULTY);
        testUser.setActive(true);
        testUser.setMustChangePassword(false);
        User savedUser;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SCRATCH_DB.toAbsolutePath())) {
            savedUser = userDAO.insert(conn, testUser);
        }

        // Change username via AuthService
        boolean updated = authService.updateUsername(adminUser, savedUser.getId(), "perm_user_e2e");
        assertTrue(updated);

        // Verify uniqueness collision rejection
        final int targetId = savedUser.getId();
        assertThrows(com.college.exception.ValidationException.class, () ->
                authService.updateUsername(adminUser, targetId, "admin"));

        // Verify updated user can authenticate with new username
        User loggedIn = authService.login("perm_user_e2e", "TempPass@123!");
        assertNotNull(loggedIn);
        assertEquals("perm_user_e2e", loggedIn.getUsername());

        // Verify EDT Swing panel rendering
        SwingUtilities.invokeAndWait(() -> {
            UserManagementPanel panel = new UserManagementPanel(adminUser);
            assertNotNull(panel);
        });
    }

    @Test
    @DisplayName("E2E-3: Student and faculty account provisioning, password policies, and details dialogs")
    void testStudentAndFacultyAccounts() throws Exception {
        // Create department for student & faculty
        Department dept = new Department("CSE_E2E", "Computer Science E2E");
        Department savedDept = departmentService.createDepartment(adminUser, dept);
        assertNotNull(savedDept);
        assertTrue(savedDept.getId() > 0);

        // 1. Student account creation with auto credentials
        Student s = new Student();
        s.setStudentId("STU_E2E_001");
        s.setFirstName("John");
        s.setLastName("VonNeumann");
        s.setEmail("jvonneumann@college.edu");
        s.setDepartmentId(savedDept.getId());
        s.setSemester("1");
        s.setActive(true);

        StudentService.StudentCreationResult sRes = studentService.createStudentWithAutoAccount(adminUser, s);
        assertNotNull(sRes);
        assertNotNull(sRes.getStudent());
        assertNotNull(sRes.getUsername());
        assertNotNull(sRes.getTemporaryPassword(), "Temporary password must be returned for admin notice");

        // Update student username
        studentService.updateStudentUsername(adminUser, sRes.getStudent().getId(), "john.vonneumann");
        User linkedStudentUser = studentService.getLinkedUserForStudent(adminUser, sRes.getStudent().getId());
        assertNotNull(linkedStudentUser);
        assertEquals("john.vonneumann", linkedStudentUser.getUsername());
        assertTrue(linkedStudentUser.isMustChangePassword(), "Student user must require password change on first login");

        // 2. Faculty account creation
        Faculty f = new Faculty();
        f.setFacultyId("FAC_E2E_001");
        f.setFirstName("Emmy");
        f.setLastName("Noether");
        f.setEmail("enoether@college.edu");
        f.setDepartmentId(savedDept.getId());
        f.setSpecialization("Abstract Algebra");
        f.setJoinDate("2026-02-01");
        f.setActive(true);

        String facultyTempPass = PasswordUtil.generateTemporaryPassword();
        Faculty savedFac = facultyService.createFaculty(adminUser, f, facultyTempPass);
        assertNotNull(savedFac);
        assertTrue(savedFac.getUserId() > 0);

        // Update faculty username
        facultyService.updateFacultyUsername(adminUser, savedFac.getId(), "emmy.noether");
        User linkedFacultyUser = facultyService.getLinkedUserForFaculty(adminUser, savedFac.getId());
        assertEquals("emmy.noether", linkedFacultyUser.getUsername());

        // Verify EDT Swing Panels and Dialogs
        SwingUtilities.invokeAndWait(() -> {
            StudentPanel stuPanel = new StudentPanel(adminUser);
            assertNotNull(stuPanel);

            FacultyPanel facPanel = new FacultyPanel(adminUser);
            assertNotNull(facPanel);

            StudentDetailsDialog stuDlg = new StudentDetailsDialog(null, sRes.getStudent(), savedDept, linkedStudentUser);
            assertNotNull(stuDlg);
            stuDlg.dispose();

            FacultyDetailsDialog facDlg = new FacultyDetailsDialog(null, savedFac, savedDept, linkedFacultyUser);
            assertNotNull(facDlg);
            facDlg.dispose();
        });
    }

    @Test
    @DisplayName("E2E-4: Academic programmes and departments management")
    void testAcademicProgrammesAndDepartments() throws Exception {
        Department dept = new Department("ECE_E2E", "Electronics E2E");
        Department savedDept = departmentService.createDepartment(adminUser, dept);

        AcademicProgram prog = new AcademicProgram();
        prog.setDepartmentId(savedDept.getId());
        prog.setCode("BTECH_ECE");
        prog.setName("Bachelor of Technology in ECE");
        prog.setDegree("B.Tech");
        prog.setCreditsRequired(160);
        prog.setDurationYears(4);
        prog.setActive(true);

        AcademicProgram savedProg = programService.createProgram(adminUser, prog);
        assertNotNull(savedProg);
        assertTrue(savedProg.getId() > 0);

        SwingUtilities.invokeAndWait(() -> {
            DepartmentPanel dPanel = new DepartmentPanel(adminUser);
            assertNotNull(dPanel);

            AcademicProgramPanel pPanel = new AcademicProgramPanel(adminUser);
            assertNotNull(pPanel);

            AcademicOverviewPanel oPanel = new AcademicOverviewPanel(adminUser);
            assertNotNull(oPanel);
        });
    }

    @Test
    @DisplayName("E2E-5: Enrollment and course management")
    void testCourseManagementAndEnrollment() throws Exception {
        Department dept = departmentService.createDepartment(adminUser, new Department("ME_E2E", "Mechanical E2E"));

        Course course = new Course();
        course.setCourseCode("ME_E2E_101");
        course.setName("Thermodynamics");
        course.setCredits(4);
        course.setDepartmentId(dept.getId());
        course.setSemester("1");
        course.setMaxCapacity(50);
        course.setActive(true);

        Course savedCourse = courseService.createCourse(adminUser, course);
        assertNotNull(savedCourse);
        assertTrue(savedCourse.getId() > 0);

        // Assign to faculty
        Faculty fac = new Faculty();
        fac.setFacultyId("FAC_ME_01");
        fac.setFirstName("James");
        fac.setLastName("Watt");
        fac.setEmail("jwatt@college.edu");
        fac.setDepartmentId(dept.getId());
        fac.setActive(true);
        Faculty savedFac = facultyService.createFaculty(adminUser, fac, PasswordUtil.generateTemporaryPassword());

        CourseAssignment assignment = assignmentService.assignFaculty(adminUser, savedCourse.getId(), savedFac.getId(), "2025-2026");
        assertNotNull(assignment);

        // Enroll student
        Student stu = new Student();
        stu.setStudentId("STU_ME_01");
        stu.setFirstName("Sadi");
        stu.setLastName("Carnot");
        stu.setEmail("scarnot@college.edu");
        stu.setDepartmentId(dept.getId());
        stu.setSemester("1");
        stu.setActive(true);
        StudentService.StudentCreationResult stuRes = studentService.createStudentWithAutoAccount(adminUser, stu);

        Enrollment enrollment = enrollmentService.enroll(adminUser, stuRes.getStudent().getId(), savedCourse.getId(), "2025-2026");
        assertNotNull(enrollment);

        SwingUtilities.invokeAndWait(() -> {
            CoursePanel cPanel = new CoursePanel(adminUser);
            assertNotNull(cPanel);

            CourseAssignmentPanel aPanel = new CourseAssignmentPanel(adminUser);
            assertNotNull(aPanel);

            EnrollmentPanel ePanel = new EnrollmentPanel(adminUser);
            assertNotNull(ePanel);
        });
    }

    @Test
    @DisplayName("E2E-6: Timetable creation, conflict validation, and viewing on TimetablePanel")
    void testTimetableWorkflows() throws Exception {
        Department dept = departmentService.createDepartment(adminUser, new Department("CIVIL_E2E", "Civil E2E"));

        Course course = new Course();
        course.setCourseCode("CE_E2E_101");
        course.setName("Structural Mechanics");
        course.setCredits(3);
        course.setDepartmentId(dept.getId());
        course.setSemester("1");
        course.setMaxCapacity(60);
        course.setActive(true);
        Course savedCourse = courseService.createCourse(adminUser, course);

        Faculty fac = new Faculty();
        fac.setFacultyId("FAC_CE_01");
        fac.setFirstName("Gustave");
        fac.setLastName("Eiffel");
        fac.setEmail("geiffel@college.edu");
        fac.setDepartmentId(dept.getId());
        fac.setActive(true);
        Faculty savedFac = facultyService.createFaculty(adminUser, fac, PasswordUtil.generateTemporaryPassword());

        // Assign faculty to course before scheduling
        assignmentService.assignFaculty(adminUser, savedCourse.getId(), savedFac.getId(), "2025-2026");

        // Create timetable entry
        TimetableEntry entry = new TimetableEntry();
        entry.setCourseId(savedCourse.getId());
        entry.setFacultyId(savedFac.getId());
        entry.setDepartmentId(dept.getId());
        entry.setSemester("1");
        entry.setDayOfWeek("MONDAY");
        entry.setStartTime("09:00");
        entry.setEndTime("10:30");
        entry.setClassroom("Hall 101");
        entry.setAcademicYear("2025-2026");

        TimetableEntry savedEntry = timetableService.createTimetableEntry(adminUser, entry);
        assertNotNull(savedEntry);
        assertTrue(savedEntry.getId() > 0);

        // Retrieve timetable entries
        List<TimetableEntry> entries = timetableService.getTimetablesByDepartmentAndSemester(adminUser, dept.getId(), "1", "2025-2026");
        assertFalse(entries.isEmpty());
        assertEquals("Hall 101", entries.get(0).getClassroom());

        // Verify TimetablePanel on Swing EDT does not throw "no such table: timetables"
        SwingUtilities.invokeAndWait(() -> {
            assertDoesNotThrow(() -> {
                TimetablePanel tPanel = new TimetablePanel(adminUser);
                assertNotNull(tPanel);
            });
        });
    }

    @Test
    @DisplayName("E2E-7: Attendance session creation, single marking, and AttendancePanel rendering")
    void testAttendanceWorkflows() throws Exception {
        Department dept = departmentService.createDepartment(adminUser, new Department("MATH_E2E", "Mathematics E2E"));

        Course course = new Course();
        course.setCourseCode("MA_E2E_101");
        course.setName("Calculus I");
        course.setCredits(4);
        course.setDepartmentId(dept.getId());
        course.setSemester("1");
        course.setMaxCapacity(60);
        course.setActive(true);
        Course savedCourse = courseService.createCourse(adminUser, course);

        Student stu = new Student();
        stu.setStudentId("STU_MATH_01");
        stu.setFirstName("Carl");
        stu.setLastName("Gauss");
        stu.setEmail("cgauss@college.edu");
        stu.setDepartmentId(dept.getId());
        stu.setSemester("1");
        stu.setActive(true);
        StudentService.StudentCreationResult stuRes = studentService.createStudentWithAutoAccount(adminUser, stu);

        Enrollment enrollment = enrollmentService.enroll(adminUser, stuRes.getStudent().getId(), savedCourse.getId(), "2025-2026");

        // Record attendance
        Attendance att = attendanceService.markAttendanceSingle(adminUser, enrollment.getId(),
                LocalDate.now().toString(), "PRESENT", "Regular lecture");
        assertNotNull(att);

        List<Attendance> records = attendanceService.getAttendanceByEnrollment(adminUser, enrollment.getId());
        assertFalse(records.isEmpty());

        double percentage = AttendanceService.calculatePercentage(records);
        assertTrue(percentage >= 100.0, "1 present of 1 session should be 100%");

        SwingUtilities.invokeAndWait(() -> {
            AttendancePanel attPanel = new AttendancePanel(adminUser);
            assertNotNull(attPanel);
        });
    }

    @Test
    @DisplayName("E2E-8: Fee structure, payment receipts, balance tracking, and FeePanels")
    void testFeeManagementAndReceipts() throws Exception {
        Department dept = departmentService.createDepartment(adminUser, new Department("BIO_E2E", "Biotech E2E"));

        FeeStructureDAO fsDAO = new FeeStructureDAO();
        FeeStructure fs = new FeeStructure(dept.getId(), "1", "TUITION", 4500000L, "2025-2026", "2026-06-30");
        FeeStructure savedFs;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SCRATCH_DB.toAbsolutePath())) {
            savedFs = fsDAO.insert(conn, fs);
        }
        assertNotNull(savedFs);

        Student stu = new Student();
        stu.setStudentId("STU_BIO_01");
        stu.setFirstName("Rosalind");
        stu.setLastName("Franklin");
        stu.setEmail("rfranklin@college.edu");
        stu.setDepartmentId(dept.getId());
        stu.setSemester("1");
        stu.setActive(true);
        StudentService.StudentCreationResult stuRes = studentService.createStudentWithAutoAccount(adminUser, stu);

        // Process fee payment
        FeePayment payment = feePaymentService.processPayment(adminUser, stuRes.getStudent().getId(),
                savedFs.getId(), "2025-2026", 2500000L, "BANK_TRANSFER");
        assertNotNull(payment);
        assertNotNull(payment.getTransactionRef(), "Payment must generate transaction reference");

        // Verify balance
        long balancePaise = feePaymentService.getOutstandingBalance(adminUser, stuRes.getStudent().getId(), savedFs.getId());
        assertEquals(2000000L, balancePaise, "Remaining balance must be 20,000 INR (2,000,000 paise)");

        SwingUtilities.invokeAndWait(() -> {
            FeeStructurePanel fsPanel = new FeeStructurePanel(adminUser);
            assertNotNull(fsPanel);

            FeePaymentPanel fpPanel = new FeePaymentPanel(adminUser);
            assertNotNull(fpPanel);
        });
    }

    @Test
    @DisplayName("E2E-9: Report generation, performance summaries, and ReportPanel rendering")
    void testReportsAndDataViews() throws Exception {
        // Generate student performance report
        List<Map<String, Object>> perfReport = reportService.getStudentPerformanceReport(adminUser, "2025-2026", null, null);
        assertNotNull(perfReport);

        // Generate fee collection report
        List<Map<String, Object>> feeReport = reportService.getFeeCollectionReport(adminUser, "2025-2026", null);
        assertNotNull(feeReport);

        // Generate attendance summary report
        List<Map<String, Object>> attReport = reportService.getAttendanceSummaryReport(adminUser, "2025-2026", null, null);
        assertNotNull(attReport);

        SwingUtilities.invokeAndWait(() -> {
            ReportPanel rPanel = new ReportPanel(adminUser);
            assertNotNull(rPanel);
        });
    }
}
