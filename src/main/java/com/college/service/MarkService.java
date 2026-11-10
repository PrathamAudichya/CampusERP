package com.college.service;

import com.college.config.DBConnection;
import com.college.dao.CourseAssignmentDAO;
import com.college.dao.CourseDAO;
import com.college.dao.EnrollmentDAO;
import com.college.dao.ExaminationDAO;
import com.college.dao.FacultyDAO;
import com.college.dao.MarkDAO;
import com.college.dao.StudentDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.DataAccessException;
import com.college.exception.ValidationException;
import com.college.model.Course;
import com.college.model.CourseAssignment;
import com.college.model.Enrollment;
import com.college.model.Examination;
import com.college.model.Faculty;
import com.college.model.Mark;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.User;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Service managing student examination evaluations, grading rules, and cross-table validation.
 */
public class MarkService {

    private final MarkDAO markDAO;
    private final ExaminationDAO examinationDAO;
    private final EnrollmentDAO enrollmentDAO;
    private final CourseDAO courseDAO;
    private final CourseAssignmentDAO courseAssignmentDAO;
    private final FacultyDAO facultyDAO;
    private final StudentDAO studentDAO;
    private final AuditService auditService;

    public MarkService() {
        this.markDAO = new MarkDAO();
        this.examinationDAO = new ExaminationDAO();
        this.enrollmentDAO = new EnrollmentDAO();
        this.courseDAO = new CourseDAO();
        this.courseAssignmentDAO = new CourseAssignmentDAO();
        this.facultyDAO = new FacultyDAO();
        this.studentDAO = new StudentDAO();
        this.auditService = new AuditService();
    }

    public MarkService(MarkDAO markDAO, ExaminationDAO examinationDAO, EnrollmentDAO enrollmentDAO,
                       CourseDAO courseDAO, CourseAssignmentDAO courseAssignmentDAO,
                       FacultyDAO facultyDAO, StudentDAO studentDAO, AuditService auditService) {
        this.markDAO = markDAO;
        this.examinationDAO = examinationDAO;
        this.enrollmentDAO = enrollmentDAO;
        this.courseDAO = courseDAO;
        this.courseAssignmentDAO = courseAssignmentDAO;
        this.facultyDAO = facultyDAO;
        this.studentDAO = studentDAO;
        this.auditService = auditService;
    }

    /**
     * Atomically validates and records or updates a student's examination mark.
     * Follows the cross-table validation rules defined in Section 4.5 of the authoritative plan.
     *
     * @param currentUser   authenticated user (ADMIN or assigned FACULTY)
     * @param enrollmentId  student enrollment ID
     * @param examinationId examination assessment ID
     * @param marksObtained marks score
     * @param remarks       optional instructor remarks
     * @return persisted/updated Mark entity
     */
    public Mark saveMark(User currentUser, int enrollmentId, int examinationId,
                         double marksObtained, String remarks) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY);

        // 1. Double.isFinite validation (strictly rejects NaN, +Infinity, -Infinity)
        if (!Double.isFinite(marksObtained)) {
            throw new ValidationException("Marks obtained must be a finite numeric value. Received: " + marksObtained);
        }
        if (marksObtained < 0.0) {
            throw new ValidationException("Marks obtained cannot be negative: " + marksObtained);
        }

        // 2. Precision validation: at most 2 decimal places using exact BigDecimal scale analysis
        BigDecimal marksBd = BigDecimal.valueOf(marksObtained).stripTrailingZeros();
        if (marksBd.scale() > 2) {
            throw new ValidationException("Marks obtained cannot have more than 2 decimal places: " + marksObtained);
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 3. Examination validation
                Examination exam = examinationDAO.findById(conn, examinationId);
                if (exam == null) {
                    throw new ValidationException("Examination " + examinationId + " not found.");
                }
                if (!exam.isActive()) {
                    throw new ValidationException("Cannot enter marks for an inactive examination.");
                }
                if (marksObtained > exam.getTotalMarks()) {
                    throw new ValidationException(String.format(
                            "Marks obtained (%.2f) exceeds examination maximum marks (%d).",
                            marksObtained, exam.getTotalMarks()));
                }

                // 4. Enrollment validation
                Enrollment enrollment = enrollmentDAO.findById(conn, enrollmentId);
                if (enrollment == null) {
                    throw new ValidationException("Enrollment " + enrollmentId + " not found.");
                }
                if (!"ACTIVE".equalsIgnoreCase(enrollment.getStatus())) {
                    throw new ValidationException("Cannot enter marks for non-ACTIVE enrollment (status: " + enrollment.getStatus() + ").");
                }

                // 5. Course and Academic Year matching
                if (exam.getCourseId() != enrollment.getCourseId()) {
                    throw new ValidationException(String.format(
                            "Examination course (%d) does not match enrollment course (%d).",
                            exam.getCourseId(), enrollment.getCourseId()));
                }
                if (!exam.getAcademicYear().trim().equalsIgnoreCase(enrollment.getAcademicYear().trim())) {
                    throw new ValidationException(String.format(
                            "Examination academic year (%s) does not match enrollment academic year (%s).",
                            exam.getAcademicYear(), enrollment.getAcademicYear()));
                }

                // 6. Active Student & Course checks
                Student student = studentDAO.findById(conn, enrollment.getStudentId());
                if (student == null || !student.isActive()) {
                    throw new ValidationException("Student linked to enrollment is inactive or deleted.");
                }
                Course course = courseDAO.findById(conn, enrollment.getCourseId());
                if (course == null || !course.isActive()) {
                    throw new ValidationException("Course linked to enrollment is inactive or deleted.");
                }

                // 7. Faculty authorization
                if (currentUser.getRole() == Role.FACULTY) {
                    Faculty faculty = facultyDAO.findByUserId(conn, currentUser.getId());
                    if (faculty == null || !faculty.isActive()) {
                        throw new AuthenticationException("Active faculty profile not found.");
                    }
                    CourseAssignment ca = courseAssignmentDAO.findActiveByCourseAndFaculty(
                            conn, exam.getCourseId(), faculty.getId(), exam.getAcademicYear().trim());
                    if (ca == null) {
                        throw new AuthenticationException(String.format(
                                "Faculty %s is not assigned to course %s for academic year %s.",
                                faculty.getFacultyId(), course.getCourseCode(), exam.getAcademicYear()));
                    }
                }

                // 8. Grade calculation & upsert
                String grade = calculateGrade(marksObtained, exam.getTotalMarks());
                Mark mark = new Mark();
                mark.setEnrollmentId(enrollmentId);
                mark.setExaminationId(examinationId);
                mark.setMarksObtained(marksObtained);
                mark.setGrade(grade);
                mark.setRemarks(remarks);

                Mark savedMark = markDAO.upsert(conn, mark);

                // 9. Transactional audit log
                auditService.log(conn, currentUser.getId(), "SAVE_MARK", "MARK", savedMark.getId(),
                        null, String.format("Score: %.2f/%d (%s) for student %s on exam %s",
                                marksObtained, exam.getTotalMarks(), grade, student.getStudentId(), exam.getExamName()));

                conn.commit();
                return savedMark;
            } catch (Throwable ex) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                if (ex instanceof ValidationException) {
                    throw (ValidationException) ex;
                } else if (ex instanceof AuthenticationException) {
                    throw (AuthenticationException) ex;
                } else if (ex instanceof DataAccessException) {
                    throw (DataAccessException) ex;
                } else if (ex instanceof SQLException) {
                    throw new DataAccessException("Database operation failed: " + ex.getMessage(), (SQLException) ex);
                } else if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                } else if (ex instanceof Error) {
                    throw (Error) ex;
                } else {
                    throw new RuntimeException("Unexpected error during save mark", ex);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save mark: " + e.getMessage(), e);
        }
    }

    /**
     * Authoritative institutional grade calculation based on percentage score.
     *
     * @param marksObtained marks scored
     * @param totalMarks    maximum possible marks
     * @return letter grade (A+, A, B, C, D, E, F)
     */
    public static String calculateGrade(double marksObtained, int totalMarks) {
        if (totalMarks <= 0) {
            return "F";
        }
        double percentage = (marksObtained * 100.0) / totalMarks;
        if (percentage >= 90.0) {
            return "A+";
        }
        if (percentage >= 80.0) {
            return "A";
        }
        if (percentage >= 70.0) {
            return "B";
        }
        if (percentage >= 60.0) {
            return "C";
        }
        if (percentage >= 50.0) {
            return "D";
        }
        if (percentage >= 40.0) {
            return "E";
        }
        return "F";
    }

    public Mark getMarkByEnrollmentAndExamination(User currentUser, int enrollmentId, int examinationId) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY, Role.STUDENT);
        Enrollment enrollment = enrollmentDAO.findById(enrollmentId);
        if (enrollment == null) {
            throw new ValidationException("Enrollment " + enrollmentId + " not found.");
        }

        if (currentUser.getRole() == Role.STUDENT) {
            Student student = studentDAO.findById(enrollment.getStudentId());
            if (student == null || student.getUserId() != currentUser.getId()) {
                throw new AuthenticationException("Students can only view their own marks.");
            }
        } else if (currentUser.getRole() == Role.FACULTY) {
            Faculty faculty = facultyDAO.findByUserId(currentUser.getId());
            if (faculty == null || !faculty.isActive()) {
                throw new AuthenticationException("Active faculty profile not found.");
            }
            CourseAssignment ca = courseAssignmentDAO.findActiveByCourseAndFaculty(
                    enrollment.getCourseId(), faculty.getId(), enrollment.getAcademicYear());
            if (ca == null) {
                throw new AuthenticationException("Faculty can only view marks for their assigned courses.");
            }
        }
        return markDAO.findByEnrollmentAndExamination(enrollmentId, examinationId);
    }

    public List<Mark> getMarksByEnrollment(User currentUser, int enrollmentId) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY, Role.STUDENT);
        Enrollment enrollment = enrollmentDAO.findById(enrollmentId);
        if (enrollment == null) {
            throw new ValidationException("Enrollment " + enrollmentId + " not found.");
        }

        if (currentUser.getRole() == Role.STUDENT) {
            Student student = studentDAO.findById(enrollment.getStudentId());
            if (student == null || student.getUserId() != currentUser.getId()) {
                throw new AuthenticationException("Students can only view their own marks.");
            }
        } else if (currentUser.getRole() == Role.FACULTY) {
            Faculty faculty = facultyDAO.findByUserId(currentUser.getId());
            if (faculty == null || !faculty.isActive()) {
                throw new AuthenticationException("Active faculty profile not found.");
            }
            CourseAssignment ca = courseAssignmentDAO.findActiveByCourseAndFaculty(
                    enrollment.getCourseId(), faculty.getId(), enrollment.getAcademicYear());
            if (ca == null) {
                throw new AuthenticationException("Faculty can only view marks for their assigned courses.");
            }
        }
        return markDAO.findByEnrollment(enrollmentId);
    }

    public List<Mark> getMarksByExamination(User currentUser, int examinationId) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY);
        Examination exam = examinationDAO.findById(examinationId);
        if (exam == null) {
            throw new ValidationException("Examination " + examinationId + " not found.");
        }

        if (currentUser.getRole() == Role.FACULTY) {
            Faculty faculty = facultyDAO.findByUserId(currentUser.getId());
            if (faculty == null || !faculty.isActive()) {
                throw new AuthenticationException("Active faculty profile not found.");
            }
            CourseAssignment ca = courseAssignmentDAO.findActiveByCourseAndFaculty(
                    exam.getCourseId(), faculty.getId(), exam.getAcademicYear());
            if (ca == null) {
                throw new AuthenticationException("Faculty can only view marks for their assigned courses.");
            }
        }
        return markDAO.findByExamination(examinationId);
    }
}

