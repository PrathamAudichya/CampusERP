package com.college.service;

import com.college.TestDBHelper;
import com.college.dao.*;
import com.college.exception.AuthenticationException;
import com.college.exception.ValidationException;
import com.college.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Authoritative JUnit 5 test suite verifying V2-1 Remediation requirements:
 * Enrollment Lifecycle & Student Self-Enrollment, Faculty Scoping,
 * Attendance Calculation & Batch Integrity, Marks Authorization.
 */
public class V2_1_RemediationTest {

    private static EnrollmentService enrollmentService;
    private static AttendanceService attendanceService;
    private static MarkService markService;
    private static ReportService reportService;

    private static UserDAO userDAO;
    private static StudentDAO studentDAO;
    private static FacultyDAO facultyDAO;
    private static CourseDAO courseDAO;
    private static DepartmentDAO departmentDAO;
    private static CourseAssignmentDAO assignmentDAO;
    private static EnrollmentDAO enrollmentDAO;
    private static AttendanceDAO attendanceDAO;
    private static ExaminationDAO examinationDAO;
    private static MarkDAO markDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        enrollmentService = new EnrollmentService();
        attendanceService = new AttendanceService();
        markService = new MarkService();
        reportService = new ReportService();

        userDAO = new UserDAO();
        studentDAO = new StudentDAO();
        facultyDAO = new FacultyDAO();
        courseDAO = new CourseDAO();
        departmentDAO = new DepartmentDAO();
        assignmentDAO = new CourseAssignmentDAO();
        enrollmentDAO = new EnrollmentDAO();
        attendanceDAO = new AttendanceDAO();
        examinationDAO = new ExaminationDAO();
        markDAO = new MarkDAO();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    private User createAdmin(Connection conn, String username) {
        User u = new User(username, "$2a$12$e8Y6lXoHlA...", Role.ADMIN);
        u.setActive(true);
        return userDAO.insert(conn, u);
    }

    private Faculty createFaculty(Connection conn, String username, int deptId, String facultyCode) {
        User u = userDAO.insert(conn, new User(username, "$2a$12$e8Y6lXoHlA...", Role.FACULTY));
        Faculty f = new Faculty(u.getId(), deptId, facultyCode, "Faculty", "Instructor",
                username + "@col.edu", "555-0101", "Systems", "2020-01-01");
        f.setActive(true);
        return facultyDAO.insert(conn, f);
    }

    private Student createStudent(Connection conn, String username, int deptId, String studentCode, String sem) {
        User u = userDAO.insert(conn, new User(username, "$2a$12$e8Y6lXoHlA...", Role.STUDENT));
        Student s = new Student(u.getId(), deptId, studentCode, "Alice", "Tester",
                username + "@col.edu", "555-0202", "2004-05-10", "2026-08-01", sem);
        s.setActive(true);
        return studentDAO.insert(conn, s);
    }

    // =========================================================================
    // SECTION A: ENROLLMENT LIFECYCLE & STUDENT SELF-ENROLLMENT
    // =========================================================================

    @Test
    @DisplayName("A1. Student can successfully self-enroll into an active course")
    void testStudentCanEnrollThemselves() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS201", "Data Structures", 4, "1", 30));
            Student student = createStudent(conn, "student_a1", dept.getId(), "STU_A1", "1");
            User studentUser = userDAO.findById(conn, student.getUserId());

            Enrollment enr = enrollmentService.selfEnroll(studentUser, course.getId(), "2026-2027");
            assertNotNull(enr);
            assertEquals(student.getId(), enr.getStudentId());
            assertEquals(course.getId(), enr.getCourseId());
            assertEquals("2026-2027", enr.getAcademicYear());
            assertEquals("ACTIVE", enr.getStatus());
        }
    }

    @Test
    @DisplayName("A2. Student cannot enroll another student's profile directly")
    void testStudentCannotEnrollAnotherStudent() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS202", "Algorithms", 4, "1", 30));

            Student student1 = createStudent(conn, "student_a2_1", dept.getId(), "STU_A2_1", "1");
            Student student2 = createStudent(conn, "student_a2_2", dept.getId(), "STU_A2_2", "1");
            User user1 = userDAO.findById(conn, student1.getUserId());

            // student1 tries to enroll student2
            assertThrows(AuthenticationException.class, () ->
                    enrollmentService.enroll(user1, student2.getId(), course.getId(), "2026-2027"));
        }
    }

    @Test
    @DisplayName("A3. Duplicate active enrollment is strictly rejected")
    void testDuplicateEnrollmentRejected() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS203", "Database Systems", 4, "1", 30));
            Student student = createStudent(conn, "student_a3", dept.getId(), "STU_A3", "1");
            User studentUser = userDAO.findById(conn, student.getUserId());

            enrollmentService.selfEnroll(studentUser, course.getId(), "2026-2027");

            // Attempting to enroll again must throw ValidationException
            ValidationException ex = assertThrows(ValidationException.class, () ->
                    enrollmentService.selfEnroll(studentUser, course.getId(), "2026-2027"));
            assertTrue(ex.getMessage().contains("already actively enrolled"));
        }
    }

    @Test
    @DisplayName("A4. Inactive course registration is rejected")
    void testInactiveCourseRejected() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = new Course(dept.getId(), "CS204", "Obsolete Systems", 4, "1", 30);
            course.setActive(false);
            course = courseDAO.insert(conn, course);

            Student student = createStudent(conn, "student_a4", dept.getId(), "STU_A4", "1");
            User studentUser = userDAO.findById(conn, student.getUserId());

            final int cid = course.getId();
            ValidationException ex = assertThrows(ValidationException.class, () ->
                    enrollmentService.selfEnroll(studentUser, cid, "2026-2027"));
            assertTrue(ex.getMessage().contains("inactive"));
        }
    }

    @Test
    @DisplayName("A5. Inactive student registration is rejected")
    void testInactiveStudentRejected() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS205", "Operating Systems", 4, "1", 30));

            Student student = createStudent(conn, "student_a5", dept.getId(), "STU_A5", "1");
            studentDAO.setActive(conn, student.getId(), false);

            User studentUser = userDAO.findById(conn, student.getUserId());

            final int cid = course.getId();
            assertThrows(ValidationException.class, () ->
                    enrollmentService.selfEnroll(studentUser, cid, "2026-2027"));
        }
    }

    @Test
    @DisplayName("A6. Course maximum capacity limit is strictly enforced")
    void testCapacityLimitEnforced() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS206", "Seminar", 1, "1", 1)); // Capacity: 1

            Student s1 = createStudent(conn, "student_a6_1", dept.getId(), "STU_A6_1", "1");
            Student s2 = createStudent(conn, "student_a6_2", dept.getId(), "STU_A6_2", "1");

            User u1 = userDAO.findById(conn, s1.getUserId());
            User u2 = userDAO.findById(conn, s2.getUserId());

            // s1 fills the course
            enrollmentService.selfEnroll(u1, course.getId(), "2026-2027");

            // s2 exceeds capacity
            final int cid = course.getId();
            ValidationException ex = assertThrows(ValidationException.class, () ->
                    enrollmentService.selfEnroll(u2, cid, "2026-2027"));
            assertTrue(ex.getMessage().contains("maximum capacity"));
        }
    }

    @Test
    @DisplayName("A7. Dropped enrollment can be reactivated to ACTIVE upon re-enrollment")
    void testDroppedEnrollmentReactivation() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn, "admin_a7");
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS207", "Networks", 3, "1", 20));

            Student student = createStudent(conn, "student_a7", dept.getId(), "STU_A7", "1");
            User studentUser = userDAO.findById(conn, student.getUserId());

            // Initial enrollment
            Enrollment enr = enrollmentService.selfEnroll(studentUser, course.getId(), "2026-2027");
            assertEquals("ACTIVE", enr.getStatus());

            // Drop enrollment
            enrollmentService.dropEnrollment(studentUser, enr.getId());
            Enrollment dropped = enrollmentDAO.findById(conn, enr.getId());
            assertEquals("DROPPED", dropped.getStatus());

            // Re-enroll -> reactivates same record to ACTIVE
            Enrollment reactivated = enrollmentService.selfEnroll(studentUser, course.getId(), "2026-2027");
            assertEquals(enr.getId(), reactivated.getId());
            assertEquals("ACTIVE", reactivated.getStatus());
        }
    }

    @Test
    @DisplayName("A8. Admin enrollment, drop and explicit reactivation work properly")
    void testAdminEnrollmentDropAndReactivate() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn, "admin_a8");
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS208", "Compilers", 4, "1", 20));
            Student student = createStudent(conn, "student_a8", dept.getId(), "STU_A8", "1");

            // Admin enrolls
            Enrollment enr = enrollmentService.enroll(admin, student.getId(), course.getId(), "2026-2027");
            assertEquals("ACTIVE", enr.getStatus());

            // Admin drops
            enrollmentService.dropEnrollment(admin, enr.getId());
            assertEquals("DROPPED", enrollmentDAO.findById(conn, enr.getId()).getStatus());

            // Admin explicitly reactivates
            Enrollment reactivated = enrollmentService.reactivateEnrollment(admin, enr.getId());
            assertEquals("ACTIVE", reactivated.getStatus());
        }
    }

    @Test
    @DisplayName("A9. Faculty direct enrollment or drop mutation is strictly rejected")
    void testFacultyDirectEnrollmentRejected() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS209", "Software Eng", 3, "1", 20));
            Student student = createStudent(conn, "student_a9", dept.getId(), "STU_A9", "1");

            Faculty faculty = createFaculty(conn, "faculty_a9", dept.getId(), "FAC_A9");
            User facUser = userDAO.findById(conn, faculty.getUserId());

            assertThrows(AuthenticationException.class, () ->
                    enrollmentService.enroll(facUser, student.getId(), course.getId(), "2026-2027"));

            assertThrows(AuthenticationException.class, () ->
                    enrollmentService.dropEnrollment(facUser, 1));
        }
    }

    // =========================================================================
    // SECTION B: FACULTY AUTHORIZATION & ROSTER SCOPING
    // =========================================================================

    @Test
    @DisplayName("B1. Assigned faculty can view course rosters for their assigned courses")
    void testAssignedFacultyCanViewRoster() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS301", "Discrete Math", 3, "1", 30));

            Faculty faculty = createFaculty(conn, "faculty_b1", dept.getId(), "FAC_B1");
            User facUser = userDAO.findById(conn, faculty.getUserId());

            // Assign faculty to course
            assignmentDAO.insert(conn, new CourseAssignment(course.getId(), faculty.getId(), "2026-2027"));

            // Enroll a student
            Student student = createStudent(conn, "student_b1", dept.getId(), "STU_B1", "1");
            User stuUser = userDAO.findById(conn, student.getUserId());
            enrollmentService.selfEnroll(stuUser, course.getId(), "2026-2027");

            List<Enrollment> roster = enrollmentService.getEnrollmentsByCourseAndYear(facUser, course.getId(), "2026-2027");
            assertNotNull(roster);
            assertEquals(1, roster.size());
            assertEquals(student.getId(), roster.get(0).getStudentId());
        }
    }

    @Test
    @DisplayName("B2. Unassigned faculty cannot view roster of unassigned courses")
    void testUnassignedFacultyCannotViewAnotherRoster() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course courseAssigned = courseDAO.insert(conn, new Course(dept.getId(), "CS302A", "Course A", 3, "1", 30));
            Course courseUnassigned = courseDAO.insert(conn, new Course(dept.getId(), "CS302B", "Course B", 3, "1", 30));

            Faculty faculty = createFaculty(conn, "faculty_b2", dept.getId(), "FAC_B2");
            User facUser = userDAO.findById(conn, faculty.getUserId());

            assignmentDAO.insert(conn, new CourseAssignment(courseAssigned.getId(), faculty.getId(), "2026-2027"));

            // Querying unassigned course must throw AuthenticationException
            final int unassignedId = courseUnassigned.getId();
            assertThrows(AuthenticationException.class, () ->
                    enrollmentService.getEnrollmentsByCourseAndYear(facUser, unassignedId, "2026-2027"));
        }
    }

    @Test
    @DisplayName("B3. Inactive faculty account/profile is rejected from roster access")
    void testInactiveFacultyCannotAccessRoster() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS303", "Algorithms II", 3, "1", 30));

            Faculty faculty = createFaculty(conn, "faculty_b3", dept.getId(), "FAC_B3");
            assignmentDAO.insert(conn, new CourseAssignment(course.getId(), faculty.getId(), "2026-2027"));

            facultyDAO.setActive(conn, faculty.getId(), false);
            User facUser = userDAO.findById(conn, faculty.getUserId());

            final int cid = course.getId();
            assertThrows(AuthenticationException.class, () ->
                    enrollmentService.getEnrollmentsByCourseAndYear(facUser, cid, "2026-2027"));
        }
    }

    @Test
    @DisplayName("B4. Admin can view course rosters without requiring faculty assignment")
    void testAdminCanAccessPermittedRosters() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn, "admin_b4");
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS304", "Architecture", 3, "1", 30));

            Student student = createStudent(conn, "student_b4", dept.getId(), "STU_B4", "1");
            User stuUser = userDAO.findById(conn, student.getUserId());
            enrollmentService.selfEnroll(stuUser, course.getId(), "2026-2027");

            List<Enrollment> roster = enrollmentService.getEnrollmentsByCourseAndYear(admin, course.getId(), "2026-2027");
            assertNotNull(roster);
            assertEquals(1, roster.size());
        }
    }

    @Test
    @DisplayName("B5. Student cannot access course-wide rosters")
    void testStudentCannotViewCourseWideRosters() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS305", "Theory", 3, "1", 30));

            Student student = createStudent(conn, "student_b5", dept.getId(), "STU_B5", "1");
            User stuUser = userDAO.findById(conn, student.getUserId());

            final int cid = course.getId();
            assertThrows(AuthenticationException.class, () ->
                    enrollmentService.getEnrollmentsByCourseAndYear(stuUser, cid, "2026-2027"));
        }
    }

    // =========================================================================
    // SECTION C: ATTENDANCE REMEDIATION & CALCULATION INTEGRITY
    // =========================================================================

    @Test
    @DisplayName("C1. Centralized attendance percentage calculation: PRESENT=1.0, LATE=0.5, ABSENT=0.0")
    void testAttendancePercentageFormula() {
        // Zero sessions
        assertEquals(0.0, AttendanceService.calculatePercentage(0, 0, 0));

        // 100% attendance (2 present, 0 late, 2 total)
        assertEquals(100.0, AttendanceService.calculatePercentage(2, 0, 2));

        // 75% attendance (1 present, 1 late, 2 total) -> (1 + 0.5) / 2 = 75%
        assertEquals(75.0, AttendanceService.calculatePercentage(1, 1, 2));

        // 50% attendance (0 present, 2 late, 2 total) -> (0 + 1.0) / 2 = 50%
        assertEquals(50.0, AttendanceService.calculatePercentage(0, 2, 2));

        // 0% attendance (0 present, 0 late, 2 total) -> 0%
        assertEquals(0.0, AttendanceService.calculatePercentage(0, 0, 2));

        // Rounded to 2 decimals
        // 1 present, 1 late out of 3 sessions -> (1 + 0.5) / 3 = 50.0%
        assertEquals(50.0, AttendanceService.calculatePercentage(1, 1, 3));
    }

    @Test
    @DisplayName("C2. Attendance batch saves successfully for active course enrollment")
    void testAttendanceBatchSavesSuccessfully() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS401", "Security", 3, "1", 30));

            Faculty faculty = createFaculty(conn, "faculty_c2", dept.getId(), "FAC_C2");
            User facUser = userDAO.findById(conn, faculty.getUserId());
            assignmentDAO.insert(conn, new CourseAssignment(course.getId(), faculty.getId(), "2026-2027"));

            Student s1 = createStudent(conn, "student_c2_1", dept.getId(), "STU_C2_1", "1");
            Student s2 = createStudent(conn, "student_c2_2", dept.getId(), "STU_C2_2", "1");

            Enrollment e1 = enrollmentService.selfEnroll(userDAO.findById(conn, s1.getUserId()), course.getId(), "2026-2027");
            Enrollment e2 = enrollmentService.selfEnroll(userDAO.findById(conn, s2.getUserId()), course.getId(), "2026-2027");

            List<AttendanceService.AttendanceEntry> entries = List.of(
                    new AttendanceService.AttendanceEntry(e1.getId(), "PRESENT", "On time"),
                    new AttendanceService.AttendanceEntry(e2.getId(), "LATE", "10 min late")
            );

            attendanceService.markAttendanceBatch(facUser, course.getId(), "2026-2027", "2026-10-01", entries);

            List<Attendance> a1 = attendanceDAO.findByEnrollment(conn, e1.getId());
            assertEquals(1, a1.size());
            assertEquals("PRESENT", a1.get(0).getStatus());

            List<Attendance> a2 = attendanceDAO.findByEnrollment(conn, e2.getId());
            assertEquals(1, a2.size());
            assertEquals("LATE", a2.get(0).getStatus());
        }
    }

    @Test
    @DisplayName("C3. Duplicate enrollment IDs in batch are rejected in Pass 1")
    void testAttendanceBatchDuplicateEntriesRejected() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS402", "Cloud", 3, "1", 30));

            Faculty faculty = createFaculty(conn, "faculty_c3", dept.getId(), "FAC_C3");
            User facUser = userDAO.findById(conn, faculty.getUserId());
            assignmentDAO.insert(conn, new CourseAssignment(course.getId(), faculty.getId(), "2026-2027"));

            Student s1 = createStudent(conn, "student_c3", dept.getId(), "STU_C3", "1");
            Enrollment e1 = enrollmentService.selfEnroll(userDAO.findById(conn, s1.getUserId()), course.getId(), "2026-2027");

            List<AttendanceService.AttendanceEntry> entries = List.of(
                    new AttendanceService.AttendanceEntry(e1.getId(), "PRESENT", ""),
                    new AttendanceService.AttendanceEntry(e1.getId(), "LATE", "")
            );

            ValidationException ex = assertThrows(ValidationException.class, () ->
                    attendanceService.markAttendanceBatch(facUser, course.getId(), "2026-2027", "2026-10-02", entries));
            assertTrue(ex.getMessage().contains("Duplicate enrollment ID"));
        }
    }

    @Test
    @DisplayName("C4. Invalid status in batch (including EXCUSED) is rejected")
    void testAttendanceBatchInvalidStatusRejected() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS403", "AI", 3, "1", 30));

            Faculty faculty = createFaculty(conn, "faculty_c4", dept.getId(), "FAC_C4");
            User facUser = userDAO.findById(conn, faculty.getUserId());
            assignmentDAO.insert(conn, new CourseAssignment(course.getId(), faculty.getId(), "2026-2027"));

            Student s1 = createStudent(conn, "student_c4", dept.getId(), "STU_C4", "1");
            Enrollment e1 = enrollmentService.selfEnroll(userDAO.findById(conn, s1.getUserId()), course.getId(), "2026-2027");

            List<AttendanceService.AttendanceEntry> entries = List.of(
                    new AttendanceService.AttendanceEntry(e1.getId(), "EXCUSED", "Medical leave")
            );

            ValidationException ex = assertThrows(ValidationException.class, () ->
                    attendanceService.markAttendanceBatch(facUser, course.getId(), "2026-2027", "2026-10-03", entries));
            assertTrue(ex.getMessage().contains("Invalid attendance status"));
        }
    }

    @Test
    @DisplayName("C5. Invalid batch rolls back completely and leaves no partial rows")
    void testAttendanceBatchRollsBackOnFailure() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS404", "Robotics", 3, "1", 30));

            Faculty faculty = createFaculty(conn, "faculty_c5", dept.getId(), "FAC_C5");
            User facUser = userDAO.findById(conn, faculty.getUserId());
            assignmentDAO.insert(conn, new CourseAssignment(course.getId(), faculty.getId(), "2026-2027"));

            Student s1 = createStudent(conn, "student_c5", dept.getId(), "STU_C5", "1");
            Enrollment e1 = enrollmentService.selfEnroll(userDAO.findById(conn, s1.getUserId()), course.getId(), "2026-2027");

            // Valid entry + invalid non-existent enrollment ID (999999)
            List<AttendanceService.AttendanceEntry> entries = List.of(
                    new AttendanceService.AttendanceEntry(e1.getId(), "PRESENT", ""),
                    new AttendanceService.AttendanceEntry(999999, "PRESENT", "")
            );

            assertThrows(ValidationException.class, () ->
                    attendanceService.markAttendanceBatch(facUser, course.getId(), "2026-2027", "2026-10-04", entries));

            // Verify e1 has NO attendance records saved
            List<Attendance> saved = attendanceDAO.findByEnrollment(conn, e1.getId());
            assertEquals(0, saved.size(), "Batch must roll back completely on validation error");
        }
    }

    @Test
    @DisplayName("C6. Defaulters report isolates students falling below the 75% threshold")
    void testAttendanceDefaultersReport() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn, "admin_c6");
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS405", "Distributed Systems", 3, "1", 30));

            Student sGood = createStudent(conn, "student_c6_good", dept.getId(), "STU_GOOD", "1");
            Student sDefaulter = createStudent(conn, "student_c6_def", dept.getId(), "STU_DEF", "1");

            Enrollment eGood = enrollmentService.selfEnroll(userDAO.findById(conn, sGood.getUserId()), course.getId(), "2026-2027");
            Enrollment eDef = enrollmentService.selfEnroll(userDAO.findById(conn, sDefaulter.getUserId()), course.getId(), "2026-2027");

            // Session 1: Both present
            attendanceDAO.upsert(conn, eGood.getId(), "2026-09-01", "PRESENT", "");
            attendanceDAO.upsert(conn, eDef.getId(), "2026-09-01", "PRESENT", "");

            // Session 2: Good is present (100%), Defaulter is absent (50%)
            attendanceDAO.upsert(conn, eGood.getId(), "2026-09-02", "PRESENT", "");
            attendanceDAO.upsert(conn, eDef.getId(), "2026-09-02", "ABSENT", "");

            List<Map<String, Object>> defaulters = reportService.getAttendanceDefaultersReport(
                    admin, "2026-2027", course.getId(), 75.0);

            assertNotNull(defaulters);
            assertEquals(1, defaulters.size());
            assertEquals("STU_DEF", defaulters.get(0).get("student_id_number"));
            Number pct = (Number) defaulters.get(0).get("attendance_percentage");
            assertEquals(50.0, pct.doubleValue(), 0.01);
        }
    }

    // =========================================================================
    // SECTION D: MARKS & COMPLETED ENROLLMENT CONSTRAINTS
    // =========================================================================

    @Test
    @DisplayName("D1. Marks entry succeeds for ACTIVE enrollments but fails for unassigned courses")
    void testMarksActiveEnrollmentAndScoping() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course cAssigned = courseDAO.insert(conn, new Course(dept.getId(), "CS501", "Assigned", 3, "1", 30));
            Course cUnassigned = courseDAO.insert(conn, new Course(dept.getId(), "CS502", "Unassigned", 3, "1", 30));

            Faculty faculty = createFaculty(conn, "faculty_d1", dept.getId(), "FAC_D1");
            User facUser = userDAO.findById(conn, faculty.getUserId());
            assignmentDAO.insert(conn, new CourseAssignment(cAssigned.getId(), faculty.getId(), "2026-2027"));

            Student student = createStudent(conn, "student_d1", dept.getId(), "STU_D1", "1");
            User stuUser = userDAO.findById(conn, student.getUserId());

            Enrollment enrAssigned = enrollmentService.selfEnroll(stuUser, cAssigned.getId(), "2026-2027");
            Enrollment enrUnassigned = enrollmentService.selfEnroll(stuUser, cUnassigned.getId(), "2026-2027");

            Examination examAssigned = examinationDAO.insert(conn, new Examination(cAssigned.getId(), "Midterm", "MIDTERM", "2026-10-15", 100, "2026-2027"));
            Examination examUnassigned = examinationDAO.insert(conn, new Examination(cUnassigned.getId(), "Midterm", "MIDTERM", "2026-10-15", 100, "2026-2027"));

            // 1. Marking assigned course succeeds
            Mark mark = markService.saveMark(facUser, enrAssigned.getId(), examAssigned.getId(), 85.50, "Good");
            assertNotNull(mark);
            assertEquals("A", mark.getGrade());

            // 2. Marking unassigned course fails
            assertThrows(Exception.class, () ->
                    markService.saveMark(facUser, enrUnassigned.getId(), examUnassigned.getId(), 85.50, ""));
        }
    }

    @Test
    @DisplayName("D2. Student can view their own marks but cannot view another student's marks")
    void testStudentMarksScoping() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn, "admin_d2");
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS503", "Security", 3, "1", 30));

            Student s1 = createStudent(conn, "student_d2_1", dept.getId(), "STU_D2_1", "1");
            Student s2 = createStudent(conn, "student_d2_2", dept.getId(), "STU_D2_2", "1");

            Enrollment e1 = enrollmentService.selfEnroll(userDAO.findById(conn, s1.getUserId()), course.getId(), "2026-2027");
            Enrollment e2 = enrollmentService.selfEnroll(userDAO.findById(conn, s2.getUserId()), course.getId(), "2026-2027");

            Examination exam = examinationDAO.insert(conn, new Examination(course.getId(), "Final", "FINAL", "2026-11-20", 100, "2026-2027"));
            markService.saveMark(admin, e1.getId(), exam.getId(), 92.0, "Excellent");
            markService.saveMark(admin, e2.getId(), exam.getId(), 78.0, "Good");

            User u1 = userDAO.findById(conn, s1.getUserId());
            User u2 = userDAO.findById(conn, s2.getUserId());

            // s1 can view own marks
            List<Mark> m1 = markService.getMarksByEnrollment(u1, e1.getId());
            assertEquals(1, m1.size());

            // s1 cannot view s2's marks
            assertThrows(AuthenticationException.class, () ->
                    markService.getMarksByEnrollment(u1, e2.getId()));
        }
    }

    @Test
    @DisplayName("D3. COMPLETED enrollment cannot be dropped or reactivated")
    void testCompletedEnrollmentCannotBeDroppedOrReactivated() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn, "admin_d3");
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS504", "Senior Capstone", 4, "1", 30));

            Student student = createStudent(conn, "student_d3", dept.getId(), "STU_D3", "1");
            Enrollment enr = enrollmentService.selfEnroll(userDAO.findById(conn, student.getUserId()), course.getId(), "2026-2027");

            // Transition status to COMPLETED
            enrollmentDAO.updateStatus(conn, enr.getId(), "COMPLETED");

            // Attempting to drop COMPLETED enrollment must fail
            ValidationException dropEx = assertThrows(ValidationException.class, () ->
                    enrollmentService.dropEnrollment(admin, enr.getId()));
            assertTrue(dropEx.getMessage().contains("COMPLETED"));

            // Attempting to reactivate COMPLETED enrollment must fail
            ValidationException reactEx = assertThrows(ValidationException.class, () ->
                    enrollmentService.reactivateEnrollment(admin, enr.getId()));
            assertTrue(reactEx.getMessage().contains("DROPPED"));
        }
    }

    @Test
    @DisplayName("D4. Course Catalogue for student provides live capacity and enrolled indicators")
    void testCourseCatalogForStudent() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course c1 = courseDAO.insert(conn, new Course(dept.getId(), "CS601", "AI Primer", 3, "1", 2));
            Course c2 = courseDAO.insert(conn, new Course(dept.getId(), "CS602", "Ethics in Tech", 2, "1", 10));

            Student student = createStudent(conn, "student_d4", dept.getId(), "STU_D4", "1");
            User stuUser = userDAO.findById(conn, student.getUserId());

            // Before enrollment: c1 has 2 available seats, not enrolled
            List<EnrollmentService.CourseCatalogItem> cat1 = enrollmentService.getCourseCatalogForStudent(stuUser, "2026-2027");
            assertTrue(cat1.stream().anyMatch(c -> c.getCourseCode().equals("CS601") && c.getAvailableSeats() == 2 && !c.isAlreadyEnrolled()));

            // Enroll in c1
            enrollmentService.selfEnroll(stuUser, c1.getId(), "2026-2027");

            // After enrollment: c1 is enrolled, available seats = 1
            List<EnrollmentService.CourseCatalogItem> cat2 = enrollmentService.getCourseCatalogForStudent(stuUser, "2026-2027");
            assertTrue(cat2.stream().anyMatch(c -> c.getCourseCode().equals("CS601") && c.getAvailableSeats() == 1 && c.isAlreadyEnrolled()));
        }
    }
}

