package com.college.service;

import com.college.config.DBConnection;
import com.college.dao.CourseAssignmentDAO;
import com.college.dao.CourseDAO;
import com.college.dao.EnrollmentDAO;
import com.college.dao.FacultyDAO;
import com.college.dao.StudentDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.DataAccessException;
import com.college.exception.ValidationException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service managing student course enrollments, course capacity limits, and DROPPED status reactivation.
 */
public class EnrollmentService {

    private final EnrollmentDAO enrollmentDAO;
    private final CourseDAO courseDAO;
    private final StudentDAO studentDAO;
    private final AuditService auditService;
    private final FacultyDAO facultyDAO;
    private final CourseAssignmentDAO courseAssignmentDAO;

    public EnrollmentService() {
        this.enrollmentDAO = new EnrollmentDAO();
        this.courseDAO = new CourseDAO();
        this.studentDAO = new StudentDAO();
        this.auditService = new AuditService();
        this.facultyDAO = new FacultyDAO();
        this.courseAssignmentDAO = new CourseAssignmentDAO();
    }

    public EnrollmentService(EnrollmentDAO enrollmentDAO, CourseDAO courseDAO,
                             StudentDAO studentDAO, AuditService auditService) {
        this(enrollmentDAO, courseDAO, studentDAO, auditService, new FacultyDAO(), new CourseAssignmentDAO());
    }

    public EnrollmentService(EnrollmentDAO enrollmentDAO, CourseDAO courseDAO,
                             StudentDAO studentDAO, AuditService auditService,
                             FacultyDAO facultyDAO, CourseAssignmentDAO courseAssignmentDAO) {
        this.enrollmentDAO = enrollmentDAO;
        this.courseDAO = courseDAO;
        this.studentDAO = studentDAO;
        this.auditService = auditService;
        this.facultyDAO = facultyDAO;
        this.courseAssignmentDAO = courseAssignmentDAO;
    }

    /**
     * Enrolls a student in a course for a given academic year under strict capacity bounds.
     *
     * @param currentUser  authenticated user (ADMIN or the student themselves)
     * @param studentId    student ID
     * @param courseId     course ID
     * @param academicYear academic year (e.g. '2026-2027')
     * @return persisted or reactivated Enrollment
     */
    public Enrollment enroll(User currentUser, int studentId, int courseId, String academicYear) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.STUDENT);
        ValidationUtil.requireNonEmpty(academicYear, "Academic year");
        String trimmedYear = academicYear.trim();

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Step 1: Validate Student
                Student student = studentDAO.findById(conn, studentId);
                if (student == null || !student.isActive()) {
                    throw new ValidationException("Student " + studentId + " is inactive or not found.");
                }

                // Authorization check: Admin or the student themselves
                if (currentUser.getRole() == Role.STUDENT && currentUser.getId() != student.getUserId()) {
                    throw new AuthenticationException("Students can only manage their own enrollments.");
                }

                // Step 2: Validate Course
                Course course = courseDAO.findById(conn, courseId);
                if (course == null || !course.isActive()) {
                    throw new ValidationException("Course " + courseId + " is inactive or not found.");
                }

                // Step 3: Check existing enrollment in this course & academic year
                Enrollment existing = enrollmentDAO.findByStudentCourseYear(conn, studentId, courseId, trimmedYear);
                if (existing != null) {
                    if ("ACTIVE".equalsIgnoreCase(existing.getStatus())) {
                        throw new ValidationException(String.format(
                                "Student %s is already actively enrolled in course %s for academic year %s.",
                                student.getStudentId(), course.getCourseCode(), trimmedYear));
                    }
                    if ("COMPLETED".equalsIgnoreCase(existing.getStatus())) {
                        throw new ValidationException(String.format(
                                "Student %s has already completed course %s for academic year %s.",
                                student.getStudentId(), course.getCourseCode(), trimmedYear));
                    }
                    // Section 4.7: DROPPED Match Reactivation Behavior
                    checkCourseCapacity(conn, course, trimmedYear);

                    enrollmentDAO.updateStatus(conn, existing.getId(), "ACTIVE");
                    existing.setStatus("ACTIVE");

                    auditService.log(conn, currentUser.getId(), "REACTIVATE_ENROLLMENT", "ENROLLMENT", existing.getId(),
                            "status=DROPPED", String.format("Reactivated enrollment for student %s in course %s (%s)",
                                    student.getStudentId(), course.getCourseCode(), trimmedYear));

                    conn.commit();
                    return existing;
                }

                // Step 4: Validate course capacity guard
                checkCourseCapacity(conn, course, trimmedYear);

                // Step 5: Insert new active enrollment
                Enrollment enrollment = new Enrollment(studentId, courseId, trimmedYear);
                enrollment.setStatus("ACTIVE");
                Enrollment saved = enrollmentDAO.insert(conn, enrollment);

                auditService.log(conn, currentUser.getId(), "ENROLL_STUDENT", "ENROLLMENT", saved.getId(),
                        null, String.format("Enrolled student %s in course %s (%s)",
                                student.getStudentId(), course.getCourseCode(), trimmedYear));

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
                    throw new DataAccessException("Database operation failed: " + ex.getMessage(), (SQLException) ex);
                } else if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                } else if (ex instanceof Error) {
                    throw (Error) ex;
                } else {
                    throw new RuntimeException("Unexpected error during enrollment", ex);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Drops an active enrollment, changing status to DROPPED.
     */
    public void dropEnrollment(User currentUser, int enrollmentId) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.STUDENT);

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Enrollment enrollment = enrollmentDAO.findById(conn, enrollmentId);
                if (enrollment == null) {
                    throw new ValidationException("Enrollment record " + enrollmentId + " not found.");
                }
                if ("COMPLETED".equalsIgnoreCase(enrollment.getStatus())) {
                    throw new ValidationException("Cannot drop a COMPLETED enrollment (Enrollment ID: " + enrollmentId + ").");
                }
                if (!"ACTIVE".equalsIgnoreCase(enrollment.getStatus())) {
                    throw new ValidationException("Only ACTIVE enrollments can be dropped. Current status: " + enrollment.getStatus());
                }

                // Authorization check
                if (currentUser.getRole() == Role.STUDENT) {
                    Student student = studentDAO.findById(conn, enrollment.getStudentId());
                    if (student == null || student.getUserId() != currentUser.getId()) {
                        throw new AuthenticationException("Students can only drop their own enrollments.");
                    }
                }

                enrollmentDAO.updateStatus(conn, enrollmentId, "DROPPED");

                auditService.log(conn, currentUser.getId(), "DROP_ENROLLMENT", "ENROLLMENT", enrollmentId,
                        "status=ACTIVE", "status=DROPPED");

                conn.commit();
            } catch (Throwable ex) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                } else if (ex instanceof Error) {
                    throw (Error) ex;
                } else {
                    throw new DataAccessException(ex.getMessage(), (SQLException) ex);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Explicitly reactivates a DROPPED enrollment (ADMIN only).
     */
    public Enrollment reactivateEnrollment(User currentUser, int enrollmentId) {
        AuthService.requireRole(currentUser, Role.ADMIN);

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Enrollment enrollment = enrollmentDAO.findById(conn, enrollmentId);
                if (enrollment == null) {
                    throw new ValidationException("Enrollment record " + enrollmentId + " not found.");
                }
                if (!"DROPPED".equalsIgnoreCase(enrollment.getStatus())) {
                    throw new ValidationException("Only DROPPED enrollments can be reactivated. Current status: " + enrollment.getStatus());
                }

                Student student = studentDAO.findById(conn, enrollment.getStudentId());
                if (student == null || !student.isActive()) {
                    throw new ValidationException("Cannot reactivate enrollment: Student is inactive or not found.");
                }

                Course course = courseDAO.findById(conn, enrollment.getCourseId());
                if (course == null || !course.isActive()) {
                    throw new ValidationException("Cannot reactivate enrollment: Course is inactive or not found.");
                }

                checkCourseCapacity(conn, course, enrollment.getAcademicYear());

                enrollmentDAO.updateStatus(conn, enrollmentId, "ACTIVE");
                enrollment.setStatus("ACTIVE");

                auditService.log(conn, currentUser.getId(), "REACTIVATE_ENROLLMENT", "ENROLLMENT", enrollmentId,
                        "status=DROPPED", "status=ACTIVE");

                conn.commit();
                return enrollment;
            } catch (Throwable ex) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                } else if (ex instanceof Error) {
                    throw (Error) ex;
                } else {
                    throw new DataAccessException(ex.getMessage(), (SQLException) ex);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public List<Enrollment> getEnrollmentsByStudent(User currentUser, int studentId) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY, Role.STUDENT);
        Student student = studentDAO.findById(studentId);
        if (student == null) {
            throw new ValidationException("Student " + studentId + " not found.");
        }
        if (currentUser.getRole() == Role.STUDENT && currentUser.getId() != student.getUserId()) {
            throw new AuthenticationException("Students can only view their own enrollments.");
        }
        return enrollmentDAO.findByStudent(studentId);
    }

    public List<Enrollment> getEnrollmentsByCourseAndYear(User currentUser, int courseId, String academicYear) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY);
        ValidationUtil.requireNonEmpty(academicYear, "Academic year");
        String trimmedYear = academicYear.trim();

        if (currentUser.getRole() == Role.FACULTY) {
            if (!currentUser.isActive()) {
                throw new AuthenticationException("Faculty user account is inactive.");
            }
            Faculty faculty = facultyDAO.findByUserId(currentUser.getId());
            if (faculty == null || !faculty.isActive()) {
                throw new AuthenticationException("Active faculty profile not found.");
            }
            CourseAssignment ca = courseAssignmentDAO.findActiveByCourseAndFaculty(courseId, faculty.getId(), trimmedYear);
            if (ca == null) {
                throw new AuthenticationException(String.format(
                        "Faculty %s is not assigned to course %d for academic year %s.",
                        faculty.getFacultyId(), courseId, trimmedYear));
            }
        }
        return enrollmentDAO.findByCourseAndYear(courseId, trimmedYear);
    }

    /**
     * Self-enrolls the authenticated student into an eligible course.
     * Profile is derived strictly from authenticated user identity.
     *
     * @param currentUser  authenticated user (must have role STUDENT)
     * @param courseId     course ID to register for
     * @param academicYear academic year
     * @return persisted or reactivated Enrollment
     */
    public Enrollment selfEnroll(User currentUser, int courseId, String academicYear) {
        AuthService.requireRole(currentUser, Role.STUDENT);
        if (!currentUser.isActive()) {
            throw new AuthenticationException("Student user account is inactive.");
        }
        Student student = studentDAO.findByUserId(currentUser.getId());
        if (student == null || !student.isActive()) {
            throw new ValidationException("Active student profile not found for authenticated user.");
        }
        return enroll(currentUser, student.getId(), courseId, academicYear);
    }

    /**
     * Immutable course catalog item for student course exploration and self-registration.
     */
    public static class CourseCatalogItem {
        private final int courseId;
        private final String courseCode;
        private final String courseName;
        private final String departmentName;
        private final String semester;
        private final int credits;
        private final int maxCapacity;
        private final int enrolledCount;
        private final int availableSeats;
        private final boolean alreadyEnrolled;

        public CourseCatalogItem(int courseId, String courseCode, String courseName,
                                 String departmentName, String semester, int credits,
                                 int maxCapacity, int enrolledCount, int availableSeats,
                                 boolean alreadyEnrolled) {
            this.courseId = courseId;
            this.courseCode = courseCode;
            this.courseName = courseName;
            this.departmentName = departmentName;
            this.semester = semester;
            this.credits = credits;
            this.maxCapacity = maxCapacity;
            this.enrolledCount = enrolledCount;
            this.availableSeats = availableSeats;
            this.alreadyEnrolled = alreadyEnrolled;
        }

        public int getCourseId() { return courseId; }
        public String getCourseCode() { return courseCode; }
        public String getCourseName() { return courseName; }
        public String getDepartmentName() { return departmentName; }
        public String getSemester() { return semester; }
        public int getCredits() { return credits; }
        public int getMaxCapacity() { return maxCapacity; }
        public int getEnrolledCount() { return enrolledCount; }
        public int getAvailableSeats() { return availableSeats; }
        public boolean isAlreadyEnrolled() { return alreadyEnrolled; }
    }

    /**
     * Retrieves the active course catalogue with live capacity and registration eligibility
     * for the authenticated student.
     *
     * @param currentUser  authenticated user (must have role STUDENT)
     * @param academicYear target academic year
     * @return list of CourseCatalogItem records
     */
    public List<CourseCatalogItem> getCourseCatalogForStudent(User currentUser, String academicYear) {
        AuthService.requireRole(currentUser, Role.STUDENT);
        ValidationUtil.requireNonEmpty(academicYear, "Academic year");
        String trimmedYear = academicYear.trim();

        Student student = studentDAO.findByUserId(currentUser.getId());
        if (student == null || !student.isActive()) {
            throw new AuthenticationException("Active student profile not found for authenticated user.");
        }

        List<Course> allCourses = courseDAO.findAllActive();
        List<Enrollment> studentEnrollments = enrollmentDAO.findByStudentAndYear(student.getId(), trimmedYear);
        Set<Integer> activeEnrolledIds = studentEnrollments.stream()
                .filter(e -> "ACTIVE".equalsIgnoreCase(e.getStatus()))
                .map(Enrollment::getCourseId)
                .collect(Collectors.toSet());

        List<CourseCatalogItem> catalog = new ArrayList<>();
        for (Course course : allCourses) {
            int enrolled = enrollmentDAO.countActiveByCourseAndYear(course.getId(), trimmedYear);
            int capacity = course.getMaxCapacity() != null ? course.getMaxCapacity() : 0;
            int available = capacity > 0 ? Math.max(0, capacity - enrolled) : Integer.MAX_VALUE;
            boolean already = activeEnrolledIds.contains(course.getId());
            String dept = (course.getDepartmentName() != null && !course.getDepartmentName().isBlank())
                    ? course.getDepartmentName()
                    : "Dept #" + course.getDepartmentId();

            catalog.add(new CourseCatalogItem(
                    course.getId(),
                    course.getCourseCode(),
                    course.getName(),
                    dept,
                    course.getSemester(),
                    course.getCredits(),
                    capacity,
                    enrolled,
                    available,
                    already
            ));
        }
        return catalog;
    }

    private void checkCourseCapacity(Connection conn, Course course, String academicYear) {
        if (course.getMaxCapacity() != null && course.getMaxCapacity() > 0) {
            int currentEnrolled = enrollmentDAO.countActiveByCourseAndYear(conn, course.getId(), academicYear);
            if (currentEnrolled >= course.getMaxCapacity()) {
                throw new ValidationException(String.format(
                        "Course %s (%s) has reached maximum capacity (%d/%d) for academic year %s.",
                        course.getCourseCode(), course.getName(), currentEnrolled, course.getMaxCapacity(), academicYear));
            }
        }
    }
}
