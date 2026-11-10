package com.college.service;

import com.college.config.DBConnection;
import com.college.dao.AcademicProgramDAO;
import com.college.dao.StudentCohortDAO;
import com.college.dao.StudentDAO;
import com.college.exception.ValidationException;
import com.college.model.AcademicProgram;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.StudentCohort;
import com.college.model.User;
import com.college.util.ValidationUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service managing Academic Cohorts, expected graduation computation,
 * student cohort assignment, and legacy record reconciliation.
 */
public class StudentCohortService {

    private final StudentCohortDAO cohortDAO;
    private final AcademicProgramDAO programDAO;
    private final StudentDAO studentDAO;
    private final AuditService auditService;

    public StudentCohortService() {
        this(new StudentCohortDAO(), new AcademicProgramDAO(), new StudentDAO(), new AuditService());
    }

    public StudentCohortService(StudentCohortDAO cohortDAO, AcademicProgramDAO programDAO,
                                StudentDAO studentDAO, AuditService auditService) {
        this.cohortDAO = cohortDAO;
        this.programDAO = programDAO;
        this.studentDAO = studentDAO;
        this.auditService = auditService;
    }

    /**
     * Calculates expected graduation academic year based on admission year and programme duration.
     */
    public String calculateExpectedGraduationYear(String admissionYear, int durationYears) {
        if (admissionYear == null || admissionYear.trim().isEmpty()) {
            throw new ValidationException("Admission year cannot be empty.");
        }
        String adm = admissionYear.trim();
        if (adm.contains("-")) {
            String[] parts = adm.split("-");
            try {
                int start = Integer.parseInt(parts[0].trim());
                int end = Integer.parseInt(parts[1].trim());
                return (start + durationYears) + "-" + (end + durationYears);
            } catch (NumberFormatException ignored) {}
        }

        try {
            int year = Integer.parseInt(adm);
            return String.valueOf(year + durationYears);
        } catch (NumberFormatException ignored) {
            return adm + "+" + durationYears + "y";
        }
    }

    /**
     * Creates an academic cohort with auto-generated cohort code (e.g. BTECH-CSE-2024).
     */
    public StudentCohort createCohort(User currentUser, int programId, String admissionYear, int currentSemester) {
        StudentCohort c = createCohort(currentUser, programId, admissionYear);
        if (currentSemester > 1) {
            c.setCurrentSemester(currentSemester);
            cohortDAO.update(c);
        }
        return c;
    }

    public StudentCohort createCohort(User currentUser, int programId, String admissionYear) {
        AuthService.requireRole(currentUser, Role.ADMIN);

        AcademicProgram program = programDAO.findById(programId);
        if (program == null || !program.isActive()) {
            throw new ValidationException("Academic programme not found or inactive (ID: " + programId + ")");
        }

        ValidationUtil.requireNonEmpty(admissionYear, "Admission year");
        String admYear = admissionYear.trim();

        String codeSuffix = admYear.contains("-") ? admYear.split("-")[0].trim() : admYear;
        String cohortCode = program.getCode() + "-" + codeSuffix;

        StudentCohort existing = cohortDAO.findByCode(cohortCode);
        if (existing != null) {
            throw new ValidationException("Cohort code already exists: " + cohortCode);
        }

        String expectedGrad = calculateExpectedGraduationYear(admYear, program.getDurationYears());
        StudentCohort cohort = new StudentCohort(cohortCode, programId, admYear, expectedGrad, 1, "ACTIVE");

        StudentCohort saved = cohortDAO.insert(cohort);
        auditService.log(currentUser.getId(), "CREATE_COHORT", "STUDENT_COHORT", saved.getId(),
                null, "Created cohort: " + saved.getCohortCode() + " for " + program.getCode());

        return saved;
    }

    /**
     * Creates a cohort from a complete model entity.
     */
    public StudentCohort createCohort(User currentUser, StudentCohort cohort) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        if (cohort == null) throw new ValidationException("Cohort details cannot be null.");

        ValidationUtil.requireNonEmpty(cohort.getCohortCode(), "Cohort code");
        ValidationUtil.requireNonEmpty(cohort.getAdmissionYear(), "Admission year");

        AcademicProgram program = programDAO.findById(cohort.getProgramId());
        if (program == null) {
            throw new ValidationException("Academic programme not found (ID: " + cohort.getProgramId() + ")");
        }

        String code = cohort.getCohortCode().trim().toUpperCase();
        cohort.setCohortCode(code);

        if (cohortDAO.findByCode(code) != null) {
            throw new ValidationException("Cohort code already exists: " + code);
        }

        if (cohort.getExpectedGraduationYear() == null || cohort.getExpectedGraduationYear().trim().isEmpty()) {
            cohort.setExpectedGraduationYear(calculateExpectedGraduationYear(cohort.getAdmissionYear(), program.getDurationYears()));
        }

        StudentCohort saved = cohortDAO.insert(cohort);
        auditService.log(currentUser.getId(), "CREATE_COHORT", "STUDENT_COHORT", saved.getId(),
                null, "Created cohort: " + saved.getCohortCode());

        return saved;
    }

    /**
     * Assigns a student to an academic cohort and links the corresponding academic programme.
     */
    public void assignStudentToCohort(User currentUser, int studentId, int cohortId) {
        AuthService.requireRole(currentUser, Role.ADMIN);

        Student student = studentDAO.findById(studentId);
        if (student == null || !student.isActive()) {
            throw new ValidationException("Student not found or inactive (ID: " + studentId + ")");
        }

        StudentCohort cohort = cohortDAO.findById(cohortId);
        if (cohort == null || !"ACTIVE".equalsIgnoreCase(cohort.getStatus())) {
            throw new ValidationException("Cohort not found or inactive (ID: " + cohortId + ")");
        }

        AcademicProgram program = programDAO.findById(cohort.getProgramId());
        if (program == null || !program.isActive()) {
            throw new ValidationException("Associated academic programme is inactive.");
        }

        try (Connection conn = DBConnection.getConnection()) {
            studentDAO.updateProgramAndCohort(conn, studentId, cohort.getProgramId(), cohort.getId(),
                    cohort.getAdmissionYear(), cohort.getExpectedGraduationYear());
        } catch (SQLException e) {
            throw new ValidationException("Failed to assign student to cohort: " + e.getMessage());
        }

        auditService.log(currentUser.getId(), "ASSIGN_STUDENT_COHORT", "STUDENT", studentId,
                student.getCohortCode(), cohort.getCohortCode());
    }

    /**
     * Reconciles existing legacy student records that do not have programme or cohort mappings.
     * Maps students to default programmes by matching department ID and creates default cohorts.
     *
     * @return count of reconciled student records
     */
    public int reconcileLegacyStudents(User currentUser) {
        AuthService.requireRole(currentUser, Role.ADMIN);

        List<Student> allStudents = studentDAO.findAllActive();
        int reconciledCount = 0;

        for (Student s : allStudents) {
            if (s.getProgramId() == null || s.getProgramId() == 0) {
                List<AcademicProgram> deptProgs = programDAO.findByDepartment(s.getDepartmentId());
                if (!deptProgs.isEmpty()) {
                    AcademicProgram targetProg = deptProgs.get(0); // primary undergraduate programme for department
                    String admYear = "2024-2025";
                    String cohortCode = targetProg.getCode() + "-2024";

                    StudentCohort cohort = cohortDAO.findByCode(cohortCode);
                    if (cohort == null) {
                        cohort = cohortDAO.insert(new StudentCohort(
                                cohortCode,
                                targetProg.getId(),
                                admYear,
                                calculateExpectedGraduationYear(admYear, targetProg.getDurationYears()),
                                1,
                                "ACTIVE"
                        ));
                    }

                    try (Connection conn = DBConnection.getConnection()) {
                        studentDAO.updateProgramAndCohort(conn, s.getId(), targetProg.getId(), cohort.getId(),
                                admYear, cohort.getExpectedGraduationYear());
                        reconciledCount++;
                    } catch (SQLException ignored) {}
                }
            }
        }

        if (reconciledCount > 0) {
            auditService.log(currentUser.getId(), "RECONCILE_STUDENT_PROGRAMS", "STUDENT", 0,
                    null, "Reconciled " + reconciledCount + " legacy student records with academic programmes");
        }

        return reconciledCount;
    }

    public StudentCohort getCohortById(User currentUser, int id) {
        if (currentUser == null) throw new ValidationException("Authentication required.");
        return cohortDAO.findById(id);
    }

    public StudentCohort getCohortByCode(User currentUser, String code) {
        if (currentUser == null) throw new ValidationException("Authentication required.");
        ValidationUtil.requireNonEmpty(code, "Cohort code");
        return cohortDAO.findByCode(code.trim().toUpperCase());
    }

    public List<StudentCohort> getCohortsByProgram(User currentUser, int programId) {
        if (currentUser == null) throw new ValidationException("Authentication required.");
        return cohortDAO.findByProgramId(programId);
    }

    public List<StudentCohort> getAllActiveCohorts(User currentUser) {
        if (currentUser == null) throw new ValidationException("Authentication required.");
        return cohortDAO.findAllActive();
    }

    public List<Student> getCohortStudents(User currentUser, int cohortId) {
        if (currentUser == null) throw new ValidationException("Authentication required.");
        return studentDAO.findByCohortId(cohortId);
    }
}
