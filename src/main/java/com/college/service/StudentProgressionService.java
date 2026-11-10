package com.college.service;

import com.college.config.DBConnection;
import com.college.dao.AcademicProgramDAO;
import com.college.dao.StudentDAO;
import com.college.dao.StudentProgressionDAO;
import com.college.exception.ValidationException;
import com.college.model.AcademicProgram;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.StudentProgressionHistory;
import com.college.model.User;
import com.college.util.ValidationUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Service orchestrating controlled student academic semester progression,
 * promotions, holds, repetitions, and atomic historical audit records.
 */
public class StudentProgressionService {

    private final StudentProgressionDAO progressionDAO;
    private final StudentDAO studentDAO;
    private final AcademicProgramDAO programDAO;
    private final AuditService auditService;

    public StudentProgressionService() {
        this(new StudentProgressionDAO(), new StudentDAO(), new AcademicProgramDAO(), new AuditService());
    }

    public StudentProgressionService(StudentProgressionDAO progressionDAO, StudentDAO studentDAO,
                                     AcademicProgramDAO programDAO, AuditService auditService) {
        this.progressionDAO = progressionDAO;
        this.studentDAO = studentDAO;
        this.programDAO = programDAO;
        this.auditService = auditService;
    }

    /**
     * Promotes an eligible student to the next academic semester within transactional boundaries.
     */
    public StudentProgressionHistory promoteStudent(User currentUser, int studentId, String academicYear, String reason) {
        return processProgression(currentUser, studentId, "PROMOTED", null, academicYear, reason);
    }

    /**
     * Holds a student in their current semester (e.g. attendance shortage or pending clearance).
     */
    public StudentProgressionHistory holdPromotion(User currentUser, int studentId, String academicYear, String reason) {
        return processProgression(currentUser, studentId, "HELD", null, academicYear, reason);
    }

    /**
     * Records an academic semester repetition.
     */
    public StudentProgressionHistory repeatSemester(User currentUser, int studentId, String academicYear, String reason) {
        return processProgression(currentUser, studentId, "REPEATED", null, academicYear, reason);
    }

    /**
     * Records a lateral entry semester placement (e.g. diploma holders directly admitted into Semester 3).
     */
    public StudentProgressionHistory lateralEntry(User currentUser, int studentId, int targetSemester, String academicYear, String reason) {
        if (targetSemester <= 1) {
            throw new ValidationException("Lateral entry target semester must be greater than 1.");
        }
        return processProgression(currentUser, studentId, "LATERAL_ENTRY", targetSemester, academicYear, reason);
    }

    private StudentProgressionHistory processProgression(User currentUser, int studentId, String action,
                                                        Integer targetSem, String academicYear, String reason) {
        AuthService.requireRole(currentUser, Role.ADMIN);

        ValidationUtil.requireNonEmpty(academicYear, "Academic year");
        ValidationUtil.requireNonEmpty(reason, "Progression reason");

        Student student = studentDAO.findById(studentId);
        if (student == null || !student.isActive()) {
            throw new ValidationException("Student not found or inactive (ID: " + studentId + ")");
        }

        int currentSem;
        try {
            currentSem = Integer.parseInt(student.getSemester().replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            currentSem = 1;
        }

        int maxSemesters = 8;
        if (student.getProgramId() != null && student.getProgramId() > 0) {
            AcademicProgram prog = programDAO.findById(student.getProgramId());
            if (prog != null) {
                maxSemesters = prog.getTotalSemesters();
            }
        }

        int nextSem;
        switch (action) {
            case "PROMOTED":
                if (currentSem >= maxSemesters) {
                    throw new ValidationException(String.format(
                            "Cannot promote student %s beyond final maximum semester %d of programme.",
                            student.getStudentId(), maxSemesters));
                }
                nextSem = currentSem + 1;
                break;
            case "HELD":
            case "REPEATED":
                nextSem = currentSem;
                break;
            case "LATERAL_ENTRY":
                if (targetSem == null || targetSem > maxSemesters) {
                    throw new ValidationException(String.format(
                            "Lateral entry semester %d exceeds programme limit of %d.",
                            targetSem, maxSemesters));
                }
                nextSem = targetSem;
                break;
            default:
                throw new ValidationException("Unknown progression action: " + action);
        }

        if (progressionDAO.existsAction(studentId, currentSem, nextSem, academicYear.trim(), action)) {
            throw new ValidationException(String.format(
                    "Progression action '%s' from semester %d to %d in academic year '%s' has already been recorded for student %s.",
                    action, currentSem, nextSem, academicYear.trim(), student.getStudentId()));
        }

        String newGraduationYear = null;
        if ("REPEATED".equals(action)) {
            String currentGradYear = student.getExpectedGraduationYear();
            if (currentGradYear != null && !currentGradYear.trim().isEmpty()) {
                try {
                    int parsedYear = Integer.parseInt(currentGradYear.trim());
                    newGraduationYear = String.valueOf(parsedYear + 1);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        StudentProgressionHistory record;
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                studentDAO.updateProgression(conn, studentId, String.valueOf(nextSem));
                if (newGraduationYear != null) {
                    studentDAO.updateExpectedGraduationYear(conn, studentId, newGraduationYear);
                }

                StudentProgressionHistory history = new StudentProgressionHistory(
                        studentId, currentSem, nextSem, academicYear.trim(), action, currentUser.getId(), reason.trim());
                record = progressionDAO.insertHistory(conn, history);

                String auditOld = "Sem " + currentSem;
                String auditNew = "Sem " + nextSem + " (" + action + ": " + reason.trim() +
                        (newGraduationYear != null ? ", expected graduation updated to " + newGraduationYear : "") + ")";
                auditService.log(conn, currentUser.getId(), "STUDENT_PROGRESSION", "STUDENT", studentId,
                        auditOld, auditNew);

                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                if (ex instanceof ValidationException) throw (ValidationException) ex;
                throw new ValidationException("Failed to commit student progression: " + ex.getMessage(), ex);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new ValidationException("Database connection error during progression: " + e.getMessage(), e);
        }

        return record;
    }

    public List<StudentProgressionHistory> getStudentProgressionHistory(User currentUser, int studentId) {
        if (currentUser == null) throw new ValidationException("Authentication required.");
        if (currentUser.getRole() == Role.STUDENT) {
            Student s = studentDAO.findByUserId(currentUser.getId());
            if (s == null || s.getId() != studentId) {
                throw new ValidationException("Students can only view their own progression history.");
            }
        }
        return progressionDAO.findByStudentId(studentId);
    }

    public List<StudentProgressionHistory> getHistoryForStudent(User currentUser, int studentId) {
        return getStudentProgressionHistory(currentUser, studentId);
    }

    public List<StudentProgressionHistory> getAllProgressionHistory(User currentUser) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        return progressionDAO.findAllHistory();
    }
}
