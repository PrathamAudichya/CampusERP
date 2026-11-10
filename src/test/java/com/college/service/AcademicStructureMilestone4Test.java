package com.college.service;

import com.college.TestDBHelper;
import com.college.dao.*;
import com.college.exception.AuthenticationException;
import com.college.exception.ValidationException;
import com.college.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive automated test suite for Milestone 4: Institutional Academic Structure,
 * Degree Programmes, Curriculum Architecture, Cohorts, and Semester Progression.
 */
public class AcademicStructureMilestone4Test {

    private static User adminUser;
    private static User facultyUser;
    private static User studentUser;

    private DepartmentService departmentService;
    private AcademicProgramService programService;
    private ProgrammeCurriculumService curriculumService;
    private StudentCohortService cohortService;
    private StudentProgressionService progressionService;
    private AcademicReportService reportService;
    private StudentService studentService;

    private DepartmentDAO departmentDAO;
    private AcademicProgramDAO programDAO;
    private ProgrammeSemesterDAO semesterDAO;
    private ProgrammeCurriculumDAO curriculumDAO;
    private StudentCohortDAO cohortDAO;
    private StudentProgressionDAO progressionDAO;
    private StudentDAO studentDAO;
    private CourseDAO courseDAO;
    private UserDAO userDAO;

    @BeforeAll
    static void initSuite() throws Exception {
        TestDBHelper.initTestDatabase();
        adminUser = new User("admin_m4", "$2a$12$dummyHashM4", Role.ADMIN);
        adminUser.setId(1);

        facultyUser = new User("fac_m4", "$2a$12$dummyHashM4", Role.FACULTY);
        facultyUser.setId(2);

        studentUser = new User("stu_m4", "$2a$12$dummyHashM4", Role.STUDENT);
        studentUser.setId(3);
    }

    @BeforeEach
    void setUp() throws Exception {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }

        departmentDAO = new DepartmentDAO();
        programDAO = new AcademicProgramDAO();
        semesterDAO = new ProgrammeSemesterDAO();
        curriculumDAO = new ProgrammeCurriculumDAO();
        cohortDAO = new StudentCohortDAO();
        progressionDAO = new StudentProgressionDAO();
        studentDAO = new StudentDAO();
        courseDAO = new CourseDAO();
        userDAO = new UserDAO();

        AuditService auditService = new AuditService();

        User savedAdmin = userDAO.insert(new User("admin_m4", "$2a$12$dummyHashM4", Role.ADMIN));
        adminUser.setId(savedAdmin.getId());
        User savedFac = userDAO.insert(new User("fac_m4", "$2a$12$dummyHashM4", Role.FACULTY));
        facultyUser.setId(savedFac.getId());
        User savedStu = userDAO.insert(new User("stu_m4", "$2a$12$dummyHashM4", Role.STUDENT));
        studentUser.setId(savedStu.getId());

        departmentService = new DepartmentService(departmentDAO, auditService);
        programService = new AcademicProgramService(programDAO, semesterDAO, departmentDAO, studentDAO, auditService);
        curriculumService = new ProgrammeCurriculumService(curriculumDAO, programDAO, courseDAO, auditService);
        cohortService = new StudentCohortService(cohortDAO, programDAO, studentDAO, auditService);
        progressionService = new StudentProgressionService(progressionDAO, studentDAO, programDAO, auditService);
        reportService = new AcademicReportService();
        studentService = new StudentService(studentDAO, userDAO, departmentDAO, programDAO, cohortDAO, auditService, new AuthService(userDAO, auditService));
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    // =========================================================================
    // 1. Department Architecture Tests
    // =========================================================================

    @Test
    void testDepartmentCreationAndSchoolFiltering() {
        Department dept1 = new Department(
                "CSE_TEST", "Computer Science & Engineering Test", "School of Engineering",
                "Advanced computing department", "Dr. Alan Turing", "hod.cse@example.test", "+91 99999 11111"
        );
        Department created1 = departmentService.createDepartment(adminUser, dept1);
        assertNotNull(created1);
        assertTrue(created1.getId() > 0);
        assertEquals("CSE_TEST", created1.getCode());
        assertEquals("School of Engineering", created1.getSchool());

        Department dept2 = new Department(
                "MBA_TEST", "Management Studies Test", "School of Business",
                "Executive management department", "Dr. Peter Drucker", "hod.mba@example.test", "+91 99999 22222"
        );
        departmentService.createDepartment(adminUser, dept2);

        // Filter by school
        List<Department> engDepts = departmentService.getDepartmentsBySchool(adminUser, "School of Engineering");
        assertEquals(1, engDepts.size());
        assertEquals("CSE_TEST", engDepts.get(0).getCode());

        List<String> schools = departmentService.getAllSchools(adminUser);
        assertTrue(schools.contains("School of Engineering"));
        assertTrue(schools.contains("School of Business"));
    }

    @Test
    void testDepartmentCodeUniquenessEnforced() {
        Department dept1 = new Department("CSE_UNIQUE", "CS Dept 1", "School of Eng", "Desc", "HOD", "hod@example.test", "123");
        departmentService.createDepartment(adminUser, dept1);

        Department dept2 = new Department("CSE_UNIQUE", "CS Dept 2", "School of Eng", "Desc", "HOD", "hod2@example.test", "123");
        ValidationException ex = assertThrows(ValidationException.class, () -> departmentService.createDepartment(adminUser, dept2));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    void testDepartmentDeactivationProtectionWhenRecordsAssociated() {
        Department dept = departmentService.createDepartment(adminUser,
                new Department("DEACT_TEST", "Deactivation Test Dept", "School of Science", "Desc", "HOD", "deact@example.test", "123"));

        AcademicProgram prog = new AcademicProgram(
                dept.getId(), "B.Tech Test", "PROG_DEACT", "B.Tech", "UNDERGRADUATE", 4, 8
        );
        programService.createProgram(adminUser, prog);

        // Deactivation must be blocked
        assertFalse(departmentDAO.canDeactivate(dept.getId()));
        ValidationException ex = assertThrows(ValidationException.class, () -> departmentService.deactivateDepartment(adminUser, dept.getId()));
        assertTrue(ex.getMessage().contains("Cannot deactivate department"));
    }

    // =========================================================================
    // 2. Academic Programme & Semester Generation Tests
    // =========================================================================

    @Test
    void testProgramDurationSemesterCalculation() {
        Department dept = departmentService.createDepartment(adminUser,
                new Department("CALC_DEPT", "Calc Dept", "School of Eng", "Desc", "HOD", "calc@example.test", "123"));

        // 1 Year -> 2 Semesters
        AcademicProgram p1 = new AcademicProgram(dept.getId(), "Certificate Course", "CERT1", "Cert", "DIPLOMA", 1, 0);
        AcademicProgram created1 = programService.createProgram(adminUser, p1);
        assertEquals(2, created1.getTotalSemesters());

        // 2 Years -> 4 Semesters
        AcademicProgram p2 = new AcademicProgram(dept.getId(), "Master of Science", "MSC2", "M.Sc", "POSTGRADUATE", 2, 0);
        AcademicProgram created2 = programService.createProgram(adminUser, p2);
        assertEquals(4, created2.getTotalSemesters());

        // 3 Years -> 6 Semesters
        AcademicProgram p3 = new AcademicProgram(dept.getId(), "Bachelor of Science", "BSC3", "B.Sc", "UNDERGRADUATE", 3, 0);
        AcademicProgram created3 = programService.createProgram(adminUser, p3);
        assertEquals(6, created3.getTotalSemesters());

        // 4 Years -> 8 Semesters
        AcademicProgram p4 = new AcademicProgram(dept.getId(), "Bachelor of Tech", "BTECH4", "B.Tech", "UNDERGRADUATE", 4, 0);
        AcademicProgram created4 = programService.createProgram(adminUser, p4);
        assertEquals(8, created4.getTotalSemesters());

        // 5 Years -> 10 Semesters
        AcademicProgram p5 = new AcademicProgram(dept.getId(), "Dual Degree", "DUAL5", "B.Tech+M.Tech", "UNDERGRADUATE", 5, 0);
        AcademicProgram created5 = programService.createProgram(adminUser, p5);
        assertEquals(10, created5.getTotalSemesters());
    }

    @Test
    void testResearchProgrammeMilestoneStructure() {
        Department dept = departmentService.createDepartment(adminUser,
                new Department("RES_DEPT", "Research Dept", "School of Research", "Desc", "HOD", "res@example.test", "123"));

        AcademicProgram phd = new AcademicProgram(dept.getId(), "Doctor of Philosophy", "PHD_TEST", "Ph.D.", "RESEARCH", 5, 10);
        phd.setResearch(true);
        AcademicProgram created = programService.createProgram(adminUser, phd);

        List<ProgrammeSemester> sems = semesterDAO.findByProgramId(created.getId());
        assertEquals(10, sems.size());

        // First 2 terms are Coursework
        assertEquals("COURSEWORK", sems.get(0).getTermType());
        assertEquals("COURSEWORK", sems.get(1).getTermType());

        // Middle terms are Research Terms
        assertEquals("RESEARCH_TERM", sems.get(2).getTermType());
        assertEquals("RESEARCH_TERM", sems.get(6).getTermType());

        // Final terms are Thesis Preparation & Defense
        assertEquals("THESIS", sems.get(7).getTermType());
        assertEquals("THESIS", sems.get(8).getTermType());
        assertEquals("THESIS", sems.get(9).getTermType());
        assertTrue(sems.get(9).getSemesterLabel().contains("Defense") || sems.get(9).getSemesterLabel().contains("Submission"));
    }

    // =========================================================================
    // 3. Curriculum Mapping & Circular Prerequisite Tests
    // =========================================================================

    @Test
    void testCurriculumMappingAndDuplicatePrevention() {
        Department dept = departmentService.createDepartment(adminUser,
                new Department("CURR_DEPT", "Curriculum Dept", "School of Eng", "Desc", "HOD", "curr@example.test", "123"));

        AcademicProgram prog = programService.createProgram(adminUser,
                new AcademicProgram(dept.getId(), "B.Tech CSE", "BTECH_CURR", "B.Tech", "UNDERGRADUATE", 4, 8));

        Course c1 = courseDAO.insert(new Course(dept.getId(), "CS101", "Intro to Programming", 4, "1", 60));
        Course c2 = courseDAO.insert(new Course(dept.getId(), "CS102", "Data Structures", 4, "2", 60));

        ProgrammeCurriculum m1 = curriculumService.mapCourseToSemester(adminUser,
                new ProgrammeCurriculum(prog.getId(), 1, c1.getId(), "CORE", true, "R2026", null));
        assertNotNull(m1);
        assertTrue(m1.getId() > 0);

        // Duplicate mapping to same program, semester, course & regulation must fail
        ProgrammeCurriculum dup = new ProgrammeCurriculum(prog.getId(), 1, c1.getId(), "CORE", true, "R2026", null);
        ValidationException ex = assertThrows(ValidationException.class, () -> curriculumService.mapCourseToSemester(adminUser, dup));
        assertTrue(ex.getMessage().contains("already mapped"));

        // Mapping to invalid semester (> totalSemesters) must fail
        ProgrammeCurriculum outOfBounds = new ProgrammeCurriculum(prog.getId(), 9, c2.getId(), "CORE", true, "R2026", null);
        ValidationException semEx = assertThrows(ValidationException.class, () -> curriculumService.mapCourseToSemester(adminUser, outOfBounds));
        assertTrue(semEx.getMessage().contains("Invalid semester") || semEx.getMessage().contains("allowed range"));
    }

    @Test
    void testCircularPrerequisiteDetection() {
        Department dept = departmentService.createDepartment(adminUser,
                new Department("PREREQ_DEPT", "Prereq Dept", "School of Eng", "Desc", "HOD", "prereq@example.test", "123"));

        AcademicProgram prog = programService.createProgram(adminUser,
                new AcademicProgram(dept.getId(), "B.Tech Prereq", "BTECH_PREREQ", "B.Tech", "UNDERGRADUATE", 4, 8));

        Course cA = courseDAO.insert(new Course(dept.getId(), "CRS_A", "Course A", 3, "1", 60));
        Course cB = courseDAO.insert(new Course(dept.getId(), "CRS_B", "Course B", 3, "2", 60));
        Course cC = courseDAO.insert(new Course(dept.getId(), "CRS_C", "Course C", 3, "3", 60));

        // cA -> no prereq
        ProgrammeCurriculum m1 = curriculumService.mapCourseToSemester(adminUser, new ProgrammeCurriculum(prog.getId(), 1, cA.getId(), "CORE", true, "R2026", null));

        // cB -> prerequisite is cA
        curriculumService.mapCourseToSemester(adminUser, new ProgrammeCurriculum(prog.getId(), 2, cB.getId(), "CORE", true, "R2026", cA.getId()));

        // cC -> prerequisite is cB
        curriculumService.mapCourseToSemester(adminUser, new ProgrammeCurriculum(prog.getId(), 3, cC.getId(), "CORE", true, "R2026", cB.getId()));

        // Now attempt to update cA to depend on cC: cA -> cC -> cB -> cA (Cycle!)
        m1.setPrerequisiteCourseId(cC.getId());
        ValidationException cycleEx = assertThrows(ValidationException.class, () -> curriculumService.updateCurriculumMapping(adminUser, m1));
        assertTrue(cycleEx.getMessage().contains("Circular prerequisite"));
    }

    // =========================================================================
    // 4. Student Cohort Management Tests
    // =========================================================================

    @Test
    void testCohortCreationAndExpectedGraduationCalculation() {
        Department dept = departmentService.createDepartment(adminUser,
                new Department("COH_DEPT", "Cohort Dept", "School of Eng", "Desc", "HOD", "coh@example.test", "123"));

        AcademicProgram prog4yr = programService.createProgram(adminUser,
                new AcademicProgram(dept.getId(), "B.Tech Cohort", "BTECH_COH", "B.Tech", "UNDERGRADUATE", 4, 8));

        StudentCohort cohort = cohortService.createCohort(adminUser, prog4yr.getId(), "2026-2027", 1);
        assertNotNull(cohort);
        assertEquals("BTECH_COH-2026", cohort.getCohortCode());
        assertEquals("2030-2031", cohort.getExpectedGraduationYear());
        assertEquals("ACTIVE", cohort.getStatus());
    }

    // =========================================================================
    // 5. Semester Progression Workflow Tests
    // =========================================================================

    @Test
    void testStudentPromotionAndProgressionHistory() {
        Department dept = departmentService.createDepartment(adminUser,
                new Department("PROG_DEPT", "Progression Dept", "School of Eng", "Desc", "HOD", "prog@example.test", "123"));

        AcademicProgram prog = programService.createProgram(adminUser,
                new AcademicProgram(dept.getId(), "B.Tech Progression", "BTECH_P", "B.Tech", "UNDERGRADUATE", 4, 8));

        StudentCohort cohort = cohortService.createCohort(adminUser, prog.getId(), "2026-2027", 1);

        Student s = new Student();
        s.setStudentId("STU_PROG_001");
        s.setFirstName("Rohan");
        s.setLastName("Verma");
        s.setEmail("rohan.verma@example.test");
        s.setDepartmentId(dept.getId());
        s.setProgramId(prog.getId());
        s.setCohortId(cohort.getId());
        s.setSemester("1");

        Student createdStudent = studentService.createStudentWithAutoAccount(adminUser, s).getStudent();
        assertEquals("1", createdStudent.getSemester());

        // Promote to Semester 2
        StudentProgressionHistory progressed = progressionService.promoteStudent(adminUser, createdStudent.getId(), "2026-2027", "Passed all Sem 1 core examinations with CGPA 8.5");
        assertEquals(2, progressed.getNewSemester());
        Student updatedStudent = studentDAO.findById(createdStudent.getId());
        assertEquals("2", updatedStudent.getSemester());

        List<StudentProgressionHistory> history = progressionService.getHistoryForStudent(adminUser, createdStudent.getId());
        assertEquals(1, history.size());
        StudentProgressionHistory entry = history.get(0);
        assertEquals(1, entry.getPreviousSemester());
        assertEquals(2, entry.getNewSemester());
        assertEquals("PROMOTED", entry.getAction());
        assertEquals(adminUser.getId(), entry.getPerformedBy());
        assertTrue(entry.getReason().contains("Passed all Sem 1 core"));
    }

    @Test
    void testProgressionRequiresMandatoryReason() {
        Department dept = departmentService.createDepartment(adminUser,
                new Department("REAS_DEPT", "Reason Dept", "School of Eng", "Desc", "HOD", "reas@example.test", "123"));

        AcademicProgram prog = programService.createProgram(adminUser,
                new AcademicProgram(dept.getId(), "B.Tech Reason", "BTECH_R", "B.Tech", "UNDERGRADUATE", 4, 8));

        Student s = new Student();
        s.setStudentId("STU_REAS_001");
        s.setFirstName("Pooja");
        s.setLastName("Nair");
        s.setEmail("pooja.nair@example.test");
        s.setDepartmentId(dept.getId());
        s.setProgramId(prog.getId());
        s.setSemester("1");

        Student createdStudent = studentService.createStudentWithAutoAccount(adminUser, s).getStudent();

        // Promotion without reason must be rejected
        ValidationException ex = assertThrows(ValidationException.class, () ->
                progressionService.promoteStudent(adminUser, createdStudent.getId(), "2026-2027", ""));
        assertTrue(ex.getMessage().contains("Progression reason"));
    }

    @Test
    void testProgressionCannotExceedProgramMaxSemesters() {
        Department dept = departmentService.createDepartment(adminUser,
                new Department("MAX_DEPT", "Max Sem Dept", "School of Eng", "Desc", "HOD", "max@example.test", "123"));

        // 1 Year program = 2 semesters max
        AcademicProgram prog = programService.createProgram(adminUser,
                new AcademicProgram(dept.getId(), "PG Diploma", "PGD_MAX", "PGD", "POSTGRADUATE", 1, 2));

        Student s = new Student();
        s.setStudentId("STU_MAX_001");
        s.setFirstName("Sneha");
        s.setLastName("Kapoor");
        s.setEmail("sneha.kapoor@example.test");
        s.setDepartmentId(dept.getId());
        s.setProgramId(prog.getId());
        s.setSemester("2");

        Student createdStudent = studentService.createStudentWithAutoAccount(adminUser, s).getStudent();

        // Attempting to promote beyond semester 2 must be rejected
        ValidationException ex = assertThrows(ValidationException.class, () ->
                progressionService.promoteStudent(adminUser, createdStudent.getId(), "2026-2027", "Attempting promotion beyond graduation"));
        assertTrue(ex.getMessage().contains("maximum semester"));
    }

    // =========================================================================
    // 6. Role-Based Access Control Tests
    // =========================================================================

    @Test
    void testRoleAuthorizationRejections() {
        Department dept = new Department("RBAC_DEPT", "RBAC Dept", "School", "Desc", "HOD", "rbac@example.test", "123");

        // Faculty cannot create department
        assertThrows(AuthenticationException.class, () -> departmentService.createDepartment(facultyUser, dept));

        // Student cannot create department
        assertThrows(AuthenticationException.class, () -> departmentService.createDepartment(studentUser, dept));

        // Faculty cannot create program
        AcademicProgram p = new AcademicProgram(1, "Prog", "P_RBAC", "B.Tech", "UG", 4, 8);
        assertThrows(AuthenticationException.class, () -> programService.createProgram(facultyUser, p));

        // Faculty cannot promote student
        assertThrows(AuthenticationException.class, () -> progressionService.promoteStudent(facultyUser, 1, "2026-2027", "Reason"));
    }

    // =========================================================================
    // 7. Milestone 4 Stabilization Tests
    // =========================================================================

    @Test
    void testStudentCohortProgramMismatchRejection() throws Exception {
        Department dept = departmentService.createDepartment(adminUser,
                new Department("CSE_MIS", "CSE Mismatch", "School of Eng", "Desc", "HOD", "mis@example.test", "123"));

        AcademicProgram prog1 = programService.createProgram(adminUser,
                new AcademicProgram(dept.getId(), "B.Tech CSE", "BT_MIS_1", "B.Tech", "UNDERGRADUATE", 4, 8));
        AcademicProgram prog2 = programService.createProgram(adminUser,
                new AcademicProgram(dept.getId(), "M.Tech CSE", "MT_MIS_2", "M.Tech", "POSTGRADUATE", 2, 4));

        StudentCohort cohort1 = cohortService.createCohort(adminUser,
                new StudentCohort("COHORT_MIS_1", prog1.getId(), "2026", "2030", 1, "ACTIVE"));

        // Case 1: Service validation rejects student creation when cohort does not match program
        Student s = new Student();
        s.setStudentId("STU_MIS_001");
        s.setFirstName("Rohan");
        s.setLastName("Varma");
        s.setEmail("rohan.mis@example.test");
        s.setDepartmentId(dept.getId());
        s.setProgramId(prog2.getId()); // M.Tech
        s.setCohortId(cohort1.getId()); // Belongs to B.Tech!
        s.setSemester("1");

        ValidationException ex = assertThrows(ValidationException.class, () ->
                studentService.createStudentWithAutoAccount(adminUser, s));
        assertTrue(ex.getMessage().contains("does not match student's programme ID"));

        // Case 2: Service validation rejects assignProgramAndCohort mismatch
        s.setCohortId(null);
        Student created = studentService.createStudentWithAutoAccount(adminUser, s).getStudent();
        ValidationException assignEx = assertThrows(ValidationException.class, () ->
                studentService.assignProgramAndCohort(adminUser, created.getId(), prog2.getId(), cohort1.getId(), "2026", "2030"));
        assertTrue(assignEx.getMessage().contains("does not match student's programme ID"));

        // Case 3: Database trigger rejects raw INSERT if mismatch bypassed
        try (Connection conn = TestDBHelper.getTestConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO students (user_id, department_id, program_id, cohort_id, student_id, first_name, last_name, email, semester, active) " +
                     "VALUES (?, ?, ?, ?, 'RAW_MIS_01', 'Raw', 'User', 'raw.mis@example.test', '1', 1)")) {
            ps.setInt(1, adminUser.getId());
            ps.setInt(2, dept.getId());
            ps.setInt(3, prog2.getId()); // M.Tech
            ps.setInt(4, cohort1.getId()); // B.Tech cohort
            java.sql.SQLException sqlEx = assertThrows(java.sql.SQLException.class, ps::executeUpdate);
            assertTrue(sqlEx.getMessage().toLowerCase().contains("cohort does not belong to the selected programme"));
        }
    }

    @Test
    void testRepeatSemesterIncrementsGraduationYear() {
        Department dept = departmentService.createDepartment(adminUser,
                new Department("REP_DEPT", "Repeat Dept", "School of Eng", "Desc", "HOD", "rep@example.test", "123"));

        AcademicProgram prog = programService.createProgram(adminUser,
                new AcademicProgram(dept.getId(), "B.Tech ME", "BT_REP_1", "B.Tech", "UNDERGRADUATE", 4, 8));

        Student s = new Student();
        s.setStudentId("STU_REP_001");
        s.setFirstName("Vikram");
        s.setLastName("Malhotra");
        s.setEmail("vikram.rep@example.test");
        s.setDepartmentId(dept.getId());
        s.setProgramId(prog.getId());
        s.setSemester("3");
        s.setExpectedGraduationYear("2028");

        Student created = studentService.createStudentWithAutoAccount(adminUser, s).getStudent();
        assertEquals("2028", created.getExpectedGraduationYear());
        assertEquals("3", created.getSemester());

        // Process REPEATED action
        StudentProgressionHistory history = progressionService.repeatSemester(
                adminUser, created.getId(), "2026-2027", "Medical leave during term");
        assertNotNull(history);
        assertEquals("REPEATED", history.getAction());
        assertEquals(3, history.getPreviousSemester());
        assertEquals(3, history.getNewSemester());

        // Refresh student from DB and verify semester is still 3 and graduation year incremented to 2029
        Student reloaded = studentDAO.findById(created.getId());
        assertEquals("3", reloaded.getSemester());
        assertEquals("2029", reloaded.getExpectedGraduationYear());
    }

    @Test
    void testProgressionHistoryImmutabilityTriggers() throws Exception {
        Department dept = departmentService.createDepartment(adminUser,
                new Department("IMM_DEPT", "Imm Dept", "School of Eng", "Desc", "HOD", "imm@example.test", "123"));

        AcademicProgram prog = programService.createProgram(adminUser,
                new AcademicProgram(dept.getId(), "B.Tech CE", "BT_IMM_1", "B.Tech", "UNDERGRADUATE", 4, 8));

        Student s = new Student();
        s.setStudentId("STU_IMM_001");
        s.setFirstName("Deepak");
        s.setLastName("Chopra");
        s.setEmail("deepak.imm@example.test");
        s.setDepartmentId(dept.getId());
        s.setProgramId(prog.getId());
        s.setSemester("1");

        Student created = studentService.createStudentWithAutoAccount(adminUser, s).getStudent();

        StudentProgressionHistory history = progressionService.promoteStudent(
                adminUser, created.getId(), "2026-2027", "Cleared sem 1 exams");
        assertNotNull(history);
        assertTrue(history.getId() > 0);

        try (Connection conn = TestDBHelper.getTestConnection()) {
            // Attempt raw UPDATE on progression history record -> Must fail
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "UPDATE student_progression_history SET reason = 'tampered' WHERE id = ?")) {
                ps.setInt(1, history.getId());
                java.sql.SQLException sqlEx = assertThrows(java.sql.SQLException.class, ps::executeUpdate);
                assertTrue(sqlEx.getMessage().toLowerCase().contains("immutable and cannot be updated"));
            }

            // Attempt raw DELETE on progression history record -> Must fail
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM student_progression_history WHERE id = ?")) {
                ps.setInt(1, history.getId());
                java.sql.SQLException sqlEx = assertThrows(java.sql.SQLException.class, ps::executeUpdate);
                assertTrue(sqlEx.getMessage().toLowerCase().contains("immutable and cannot be deleted"));
            }
        }
    }

    @Test
    void testDuplicateProgressionActionPrevention() {
        Department dept = departmentService.createDepartment(adminUser,
                new Department("DUP_DEPT", "Dup Dept", "School of Eng", "Desc", "HOD", "dup@example.test", "123"));

        AcademicProgram prog = programService.createProgram(adminUser,
                new AcademicProgram(dept.getId(), "B.Tech EE", "BT_DUP_1", "B.Tech", "UNDERGRADUATE", 4, 8));

        Student s = new Student();
        s.setStudentId("STU_DUP_001");
        s.setFirstName("Anil");
        s.setLastName("Deshmukh");
        s.setEmail("anil.dup@example.test");
        s.setDepartmentId(dept.getId());
        s.setProgramId(prog.getId());
        s.setSemester("1");

        Student created = studentService.createStudentWithAutoAccount(adminUser, s).getStudent();

        // First repeat semester succeeds
        StudentProgressionHistory h1 = progressionService.repeatSemester(
                adminUser, created.getId(), "2026-2027", "Repeated term 1");
        assertNotNull(h1);

        // Attempting same action in same academic year for same student must be rejected
        ValidationException ex = assertThrows(ValidationException.class, () ->
                progressionService.repeatSemester(adminUser, created.getId(), "2026-2027", "Duplicate repetition attempt"));
        assertTrue(ex.getMessage().contains("already been recorded"));
    }
}
