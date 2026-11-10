package com.college.service;

import com.college.dao.CourseAssignmentDAO;
import com.college.dao.CourseDAO;
import com.college.dao.FacultyDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.ValidationException;
import com.college.model.Course;
import com.college.model.CourseAssignment;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.User;
import com.college.util.ValidationUtil;

import java.util.List;

/**
 * Service managing faculty teaching assignments, multi-faculty course coverage, and assignment reactivations.
 */
public class CourseAssignmentService {

    private final CourseAssignmentDAO courseAssignmentDAO;
    private final CourseDAO courseDAO;
    private final FacultyDAO facultyDAO;
    private final AuditService auditService;

    public CourseAssignmentService() {
        this.courseAssignmentDAO = new CourseAssignmentDAO();
        this.courseDAO = new CourseDAO();
        this.facultyDAO = new FacultyDAO();
        this.auditService = new AuditService();
    }

    public CourseAssignmentService(CourseAssignmentDAO courseAssignmentDAO, CourseDAO courseDAO,
                                   FacultyDAO facultyDAO, AuditService auditService) {
        this.courseAssignmentDAO = courseAssignmentDAO;
        this.courseDAO = courseDAO;
        this.facultyDAO = facultyDAO;
        this.auditService = auditService;
    }

    /**
     * Assigns a faculty member to teach a course for an academic year (ADMIN only).
     *
     * @param currentUser  authenticated user (ADMIN only)
     * @param courseId     course ID
     * @param facultyId    faculty ID
     * @param academicYear academic year (e.g. '2026-2027')
     * @return persisted or reactivated CourseAssignment
     */
    public CourseAssignment assignFaculty(User currentUser, int courseId, int facultyId, String academicYear) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        ValidationUtil.requireNonEmpty(academicYear, "Academic year");
        String trimmedYear = academicYear.trim();

        Course course = courseDAO.findById(courseId);
        if (course == null || !course.isActive()) {
            throw new ValidationException("Course ID " + courseId + " does not exist or is inactive.");
        }

        Faculty faculty = facultyDAO.findById(facultyId);
        if (faculty == null || !faculty.isActive()) {
            throw new ValidationException("Faculty ID " + facultyId + " does not exist or is inactive.");
        }

        CourseAssignment existing = courseAssignmentDAO.findByCourseFacultyYear(courseId, facultyId, trimmedYear);
        if (existing != null) {
            if (existing.isActive()) {
                throw new ValidationException(String.format(
                        "Faculty %s is already actively assigned to course %s for academic year %s.",
                        faculty.getFacultyId(), course.getCourseCode(), trimmedYear));
            }
            // Section 4.7: Inactive Match Reactivation Behavior
            existing.setActive(true);
            courseAssignmentDAO.setActive(existing.getId(), true);

            auditService.log(currentUser.getId(), "REACTIVATE_ASSIGNMENT", "COURSE_ASSIGNMENT", existing.getId(),
                    "active=0", String.format("Reactivated assignment: Faculty %s to Course %s (%s)",
                            faculty.getFacultyId(), course.getCourseCode(), trimmedYear));
            return existing;
        }

        CourseAssignment ca = new CourseAssignment(courseId, facultyId, trimmedYear);
        ca.setActive(true);
        CourseAssignment saved = courseAssignmentDAO.insert(ca);

        auditService.log(currentUser.getId(), "ASSIGN_FACULTY", "COURSE_ASSIGNMENT", saved.getId(),
                null, String.format("Assigned faculty %s to course %s (%s)",
                        faculty.getFacultyId(), course.getCourseCode(), trimmedYear));
        return saved;
    }

    /**
     * Deactivates an active faculty course assignment (ADMIN only).
     */
    public void deactivateAssignment(User currentUser, int assignmentId) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        CourseAssignment ca = courseAssignmentDAO.findById(assignmentId);
        if (ca == null) {
            throw new ValidationException("Course assignment " + assignmentId + " not found.");
        }
        if (!ca.isActive()) {
            throw new ValidationException("Course assignment " + assignmentId + " is already inactive.");
        }

        courseAssignmentDAO.setActive(assignmentId, false);
        auditService.log(currentUser.getId(), "DEACTIVATE_ASSIGNMENT", "COURSE_ASSIGNMENT", assignmentId,
                "active=1", "active=0 (Course ID: " + ca.getCourseId() + ", Faculty ID: " + ca.getFacultyId() + ")");
    }

    public List<CourseAssignment> getAssignmentsByCourse(User currentUser, int courseId) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        return courseAssignmentDAO.findByCourse(courseId);
    }

    public List<CourseAssignment> getAssignmentsByFaculty(User currentUser, int facultyId) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        Faculty faculty = facultyDAO.findById(facultyId);
        if (faculty == null) {
            throw new ValidationException("Faculty ID " + facultyId + " not found.");
        }
        // Authorization: Admin or own assignments
        if (currentUser.getRole() == Role.FACULTY && currentUser.getId() != faculty.getUserId()) {
            throw new AuthenticationException("Faculty members can only view their own course assignments.");
        }
        if (currentUser.getRole() == Role.STUDENT) {
            throw new AuthenticationException("Students cannot view faculty assignments directly.");
        }
        return courseAssignmentDAO.findByFaculty(facultyId);
    }

    public List<CourseAssignment> getActiveAssignmentsByFacultyAndYear(User currentUser, int facultyId, String academicYear) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        ValidationUtil.requireNonEmpty(academicYear, "Academic year");
        Faculty faculty = facultyDAO.findById(facultyId);
        if (faculty == null) {
            throw new ValidationException("Faculty ID " + facultyId + " not found.");
        }
        if (currentUser.getRole() == Role.FACULTY && currentUser.getId() != faculty.getUserId()) {
            throw new AuthenticationException("Faculty members can only view their own course assignments.");
        }
        if (currentUser.getRole() == Role.STUDENT) {
            throw new AuthenticationException("Students cannot view faculty assignments directly.");
        }
        return courseAssignmentDAO.findActiveByFacultyAndYear(facultyId, academicYear.trim());
    }
}

