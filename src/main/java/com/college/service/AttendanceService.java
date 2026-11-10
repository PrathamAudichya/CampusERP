package com.college.service;

import com.college.config.DBConnection;
import com.college.dao.AttendanceDAO;
import com.college.dao.CourseAssignmentDAO;
import com.college.dao.CourseDAO;
import com.college.dao.EnrollmentDAO;
import com.college.dao.FacultyDAO;
import com.college.dao.StudentDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.DataAccessException;
import com.college.exception.ValidationException;
import com.college.model.Attendance;
import com.college.model.Course;
import com.college.model.CourseAssignment;
import com.college.model.Enrollment;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.User;
import com.college.util.ValidationUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service managing student session attendance, faculty assignment validation, and atomic batch marking.
 */
public class AttendanceService {

    public static final double DEFAULT_ATTENDANCE_THRESHOLD = 75.0;

    /**
     * Calculates institutional attendance percentage using the canonical policy:
     * PRESENT = 1.0, LATE = 0.5, ABSENT = 0.0.
     * Formula: (PRESENT + 0.5 * LATE) / Total Sessions * 100, rounded to 2 decimal places.
     *
     * @param presentCount number of PRESENT sessions
     * @param lateCount    number of LATE sessions
     * @param totalSessions total sessions conducted
     * @return attendance percentage (0.0 to 100.0)
     */
    public static double calculatePercentage(int presentCount, int lateCount, int totalSessions) {
        if (totalSessions <= 0) {
            return 0.0;
        }
        double weightedPresent = (presentCount * 1.0) + (lateCount * 0.5);
        return Math.round((weightedPresent * 100.0 / totalSessions) * 100.0) / 100.0;
    }

    /**
     * Calculates institutional attendance percentage across a collection of attendance records.
     *
     * @param attendanceRecords list of attendance entities
     * @return attendance percentage (0.0 to 100.0)
     */
    public static double calculatePercentage(List<Attendance> attendanceRecords) {
        if (attendanceRecords == null || attendanceRecords.isEmpty()) {
            return 0.0;
        }
        int present = 0;
        int late = 0;
        int total = 0;
        for (Attendance a : attendanceRecords) {
            if (a == null) continue;
            total++;
            if ("PRESENT".equalsIgnoreCase(a.getStatus())) {
                present++;
            } else if ("LATE".equalsIgnoreCase(a.getStatus())) {
                late++;
            }
        }
        return calculatePercentage(present, late, total);
    }

    private final AttendanceDAO attendanceDAO;
    private final EnrollmentDAO enrollmentDAO;
    private final CourseDAO courseDAO;
    private final CourseAssignmentDAO courseAssignmentDAO;
    private final FacultyDAO facultyDAO;
    private final StudentDAO studentDAO;
    private final AuditService auditService;

    public AttendanceService() {
        this.attendanceDAO = new AttendanceDAO();
        this.enrollmentDAO = new EnrollmentDAO();
        this.courseDAO = new CourseDAO();
        this.courseAssignmentDAO = new CourseAssignmentDAO();
        this.facultyDAO = new FacultyDAO();
        this.studentDAO = new StudentDAO();
        this.auditService = new AuditService();
    }

    public AttendanceService(AttendanceDAO attendanceDAO, EnrollmentDAO enrollmentDAO,
                             CourseDAO courseDAO, CourseAssignmentDAO courseAssignmentDAO,
                             FacultyDAO facultyDAO, StudentDAO studentDAO, AuditService auditService) {
        this.attendanceDAO = attendanceDAO;
        this.enrollmentDAO = enrollmentDAO;
        this.courseDAO = courseDAO;
        this.courseAssignmentDAO = courseAssignmentDAO;
        this.facultyDAO = facultyDAO;
        this.studentDAO = studentDAO;
        this.auditService = auditService;
    }

    /**
     * DTO representing a single student's attendance status in a batch submission.
     */
    public static class AttendanceEntry {
        private int enrollmentId;
        private String status;
        private String remarks;

        public AttendanceEntry() {
        }

        public AttendanceEntry(int enrollmentId, String status, String remarks) {
            this.enrollmentId = enrollmentId;
            this.status = status;
            this.remarks = remarks;
        }

        public int getEnrollmentId() {
            return enrollmentId;
        }

        public void setEnrollmentId(int enrollmentId) {
            this.enrollmentId = enrollmentId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getRemarks() {
            return remarks;
        }

        public void setRemarks(String remarks) {
            this.remarks = remarks;
        }
    }

    /**
     * Atomically validates and records session attendance for a batch of students.
     * Follows the 3-pass transactional workflow defined in Section 4.6 of the authoritative plan.
     *
     * @param currentUser  authenticated user (ADMIN or assigned FACULTY)
     * @param courseId     target course ID
     * @param academicYear target academic year
     * @param dateStr      ISO date string (YYYY-MM-DD)
     * @param entries      list of student attendance entries
     */
    public void markAttendanceBatch(User currentUser, int courseId, String academicYear,
                                    String dateStr, List<AttendanceEntry> entries) {
        markAttendanceBatch(currentUser, courseId, academicYear, dateStr, entries, null);
    }

    /**
     * Atomically validates and records session attendance for a batch of students with an optional session verification code.
     *
     * @param currentUser  authenticated user (ADMIN or assigned FACULTY)
     * @param courseId     target course ID
     * @param academicYear target academic year
     * @param dateStr      ISO date string (YYYY-MM-DD)
     * @param entries      list of student attendance entries
     * @param sessionCode  optional 6-digit session verification code
     */
    public void markAttendanceBatch(User currentUser, int courseId, String academicYear,
                                    String dateStr, List<AttendanceEntry> entries, String sessionCode) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY);
        ValidationUtil.requireNonEmpty(academicYear, "Academic year");
        LocalDate parsedDate = ValidationUtil.requireValidIsoDate(dateStr, "Attendance date");
        if (entries == null || entries.isEmpty()) {
            throw new ValidationException("Attendance entries list cannot be empty.");
        }

        // Pass 1: In-memory duplicate enrollment check and status validation
        Set<Integer> seenEnrollmentIds = new HashSet<>();
        for (AttendanceEntry entry : entries) {
            if (!seenEnrollmentIds.add(entry.getEnrollmentId())) {
                throw new ValidationException("Duplicate enrollment ID detected in batch: " + entry.getEnrollmentId());
            }
            if (entry.getStatus() == null || !Set.of("PRESENT", "ABSENT", "LATE").contains(entry.getStatus().toUpperCase())) {
                throw new ValidationException("Invalid attendance status for enrollment " + entry.getEnrollmentId() + ": " + entry.getStatus());
            }
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Course & Faculty Authorization
                Course course = courseDAO.findById(conn, courseId);
                if (course == null || !course.isActive()) {
                    throw new ValidationException("Course " + courseId + " does not exist or is inactive.");
                }

                if (currentUser.getRole() == Role.FACULTY) {
                    Faculty faculty = facultyDAO.findByUserId(conn, currentUser.getId());
                    if (faculty == null || !faculty.isActive()) {
                        throw new AuthenticationException("Active faculty profile not found.");
                    }
                    CourseAssignment ca = courseAssignmentDAO.findActiveByCourseAndFaculty(
                            conn, courseId, faculty.getId(), academicYear.trim());
                    if (ca == null) {
                        throw new AuthenticationException(String.format(
                                "Faculty %s is not assigned to course %s for academic year %s.",
                                faculty.getFacultyId(), course.getCourseCode(), academicYear.trim()));
                    }
                }

                // Pass 2: Comprehensive validation of all entries before applying modifications
                for (AttendanceEntry entry : entries) {
                    Enrollment enrollment = enrollmentDAO.findById(conn, entry.getEnrollmentId());
                    if (enrollment == null) {
                        throw new ValidationException("Enrollment record " + entry.getEnrollmentId() + " not found.");
                    }
                    if (!"ACTIVE".equalsIgnoreCase(enrollment.getStatus())) {
                        throw new ValidationException("Enrollment " + entry.getEnrollmentId() + " is not ACTIVE (status: " + enrollment.getStatus() + ").");
                    }
                    if (enrollment.getCourseId() != courseId) {
                        throw new ValidationException("Enrollment " + entry.getEnrollmentId() + " belongs to course "
                                + enrollment.getCourseId() + ", not " + courseId);
                    }
                    if (!academicYear.trim().equalsIgnoreCase(enrollment.getAcademicYear().trim())) {
                        throw new ValidationException("Enrollment " + entry.getEnrollmentId() + " belongs to academic year "
                                + enrollment.getAcademicYear() + ", not " + academicYear);
                    }

                    Student student = studentDAO.findById(conn, enrollment.getStudentId());
                    if (student == null || !student.isActive()) {
                        throw new ValidationException("Student linked to enrollment " + entry.getEnrollmentId() + " is inactive or not found.");
                    }
                }

                // Pass 3: Execution of upserts
                for (AttendanceEntry entry : entries) {
                    attendanceDAO.upsert(conn, entry.getEnrollmentId(), parsedDate.toString(),
                            entry.getStatus().toUpperCase(), entry.getRemarks(), sessionCode);
                }

                // Transactional audit log
                auditService.log(conn, currentUser.getId(), "BATCH_ATTENDANCE", "COURSE", courseId,
                        null, String.format("Marked attendance for %d students on %s (%s)", entries.size(), parsedDate, academicYear.trim()));

                conn.commit();
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
                    throw new DataAccessException("Batch attendance failed: " + ex.getMessage(), (SQLException) ex);
                } else if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                } else if (ex instanceof Error) {
                    throw (Error) ex;
                } else {
                    throw new RuntimeException("Unexpected error during batch attendance", ex);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Marks attendance for a single enrollment record.
     */
    public Attendance markAttendanceSingle(User currentUser, int enrollmentId, String dateStr,
                                           String status, String remarks) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY);
        LocalDate parsedDate = ValidationUtil.requireValidIsoDate(dateStr, "Attendance date");
        ValidationUtil.requireNonEmpty(status, "Attendance status");
        String upperStatus = status.trim().toUpperCase();
        if (!Set.of("PRESENT", "ABSENT", "LATE").contains(upperStatus)) {
            throw new ValidationException("Invalid attendance status: " + status);
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Enrollment enrollment = enrollmentDAO.findById(conn, enrollmentId);
                if (enrollment == null) {
                    throw new ValidationException("Enrollment " + enrollmentId + " not found.");
                }
                if (!"ACTIVE".equalsIgnoreCase(enrollment.getStatus())) {
                    throw new ValidationException("Cannot mark attendance for non-ACTIVE enrollment (status: " + enrollment.getStatus() + ").");
                }

                Course course = courseDAO.findById(conn, enrollment.getCourseId());
                if (course == null || !course.isActive()) {
                    throw new ValidationException("Course is inactive or not found.");
                }

                if (currentUser.getRole() == Role.FACULTY) {
                    Faculty faculty = facultyDAO.findByUserId(conn, currentUser.getId());
                    if (faculty == null || !faculty.isActive()) {
                        throw new AuthenticationException("Active faculty profile not found.");
                    }
                    CourseAssignment ca = courseAssignmentDAO.findActiveByCourseAndFaculty(
                            conn, course.getId(), faculty.getId(), enrollment.getAcademicYear());
                    if (ca == null) {
                        throw new AuthenticationException("Faculty is not assigned to this course.");
                    }
                }

                Student student = studentDAO.findById(conn, enrollment.getStudentId());
                if (student == null || !student.isActive()) {
                    throw new ValidationException("Student linked to enrollment is inactive or not found.");
                }

                Attendance saved = attendanceDAO.upsert(conn, enrollmentId, parsedDate.toString(), upperStatus, remarks);

                auditService.log(conn, currentUser.getId(), "MARK_ATTENDANCE", "ATTENDANCE", saved.getId(),
                        null, String.format("Attendance: %s on %s (Enrollment: %d)", upperStatus, parsedDate, enrollmentId));

                conn.commit();
                return saved;
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
                    throw new DataAccessException("Mark attendance failed: " + ex.getMessage(), (SQLException) ex);
                } else if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                } else if (ex instanceof Error) {
                    throw (Error) ex;
                } else {
                    throw new RuntimeException("Unexpected error during mark attendance", ex);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public List<Attendance> getAttendanceByEnrollment(User currentUser, int enrollmentId) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY, Role.STUDENT);
        Enrollment enrollment = enrollmentDAO.findById(enrollmentId);
        if (enrollment == null) {
            throw new ValidationException("Enrollment " + enrollmentId + " not found.");
        }

        // Authorization: Admin, assigned Faculty, or enrolled Student
        if (currentUser.getRole() == Role.STUDENT) {
            Student student = studentDAO.findById(enrollment.getStudentId());
            if (student == null || student.getUserId() != currentUser.getId()) {
                throw new AuthenticationException("Students can only view their own attendance records.");
            }
        } else if (currentUser.getRole() == Role.FACULTY) {
            Faculty faculty = facultyDAO.findByUserId(currentUser.getId());
            if (faculty == null || !faculty.isActive()) {
                throw new AuthenticationException("Active faculty profile not found.");
            }
            CourseAssignment ca = courseAssignmentDAO.findActiveByCourseAndFaculty(
                    enrollment.getCourseId(), faculty.getId(), enrollment.getAcademicYear());
            if (ca == null) {
                throw new AuthenticationException("Faculty can only view attendance for their assigned courses.");
            }
        }
        return attendanceDAO.findByEnrollment(enrollmentId);
    }

    public List<Attendance> getAttendanceByCourseAndDate(User currentUser, int courseId, String dateStr) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY);
        LocalDate parsedDate = ValidationUtil.requireValidIsoDate(dateStr, "Attendance date");

        if (currentUser.getRole() == Role.FACULTY) {
            Faculty faculty = facultyDAO.findByUserId(currentUser.getId());
            if (faculty == null || !faculty.isActive()) {
                throw new AuthenticationException("Active faculty profile not found.");
            }
            List<CourseAssignment> assignments = courseAssignmentDAO.findByFaculty(faculty.getId());
            boolean assigned = assignments.stream().anyMatch(ca -> ca.getCourseId() == courseId && ca.isActive());
            if (!assigned) {
                throw new AuthenticationException("Faculty can only view attendance for their assigned courses.");
            }
        }
        return attendanceDAO.findByCourseAndDate(courseId, parsedDate.toString());
    }

    /**
     * Generates a cryptographically random 6-digit session verification code.
     * Guaranteed to be between 100000 and 999999 inclusive.
     *
     * @return 6-digit code string
     */
    public String generateSessionCode() {
        int code = 100000 + new java.security.SecureRandom().nextInt(900000);
        return String.valueOf(code);
    }

    /**
     * Verifies whether a candidate date of birth matches the student's institutional record
     * without exposing or printing the DOB to public table views.
     *
     * @param studentId student ID
     * @param dobInput  candidate DOB string (YYYY-MM-DD)
     * @return true if matches institutional record; false otherwise
     */
    public boolean verifyStudentDob(int studentId, String dobInput) {
        if (dobInput == null || dobInput.trim().isEmpty()) {
            return false;
        }
        Student s = studentDAO.findById(studentId);
        if (s == null || s.getDateOfBirth() == null) {
            return false;
        }
        return s.getDateOfBirth().trim().equalsIgnoreCase(dobInput.trim());
    }
}

