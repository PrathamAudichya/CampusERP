package com.college.service;

import com.college.dao.CourseDAO;
import com.college.dao.DepartmentDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.ValidationException;
import com.college.model.Course;
import com.college.model.Department;
import com.college.model.Role;
import com.college.model.User;
import com.college.util.ValidationUtil;

import java.util.List;

/**
 * Service managing course catalog records, capacity configurations, and reactivation rules.
 */
public class CourseService {

    private final CourseDAO courseDAO;
    private final DepartmentDAO departmentDAO;
    private final AuditService auditService;

    public CourseService() {
        this.courseDAO = new CourseDAO();
        this.departmentDAO = new DepartmentDAO();
        this.auditService = new AuditService();
    }

    public CourseService(CourseDAO courseDAO, DepartmentDAO departmentDAO, AuditService auditService) {
        this.courseDAO = courseDAO;
        this.departmentDAO = departmentDAO;
        this.auditService = auditService;
    }

    /**
     * Creates a new Course or reactivates an existing inactive course with the same course code.
     *
     * @param currentUser authenticated user (ADMIN only)
     * @param course      course entity containing course catalog details
     * @return persisted or reactivated Course
     */
    public Course createCourse(User currentUser, Course course) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        validateCourseInput(course);

        // Validate department
        Department dept = departmentDAO.findById(course.getDepartmentId());
        if (dept == null || !dept.isActive()) {
            throw new ValidationException("Department ID " + course.getDepartmentId() + " does not exist or is inactive.");
        }

        String courseCode = course.getCourseCode().trim().toUpperCase();
        Course existing = courseDAO.findByCourseCode(courseCode);

        if (existing != null) {
            if (existing.isActive()) {
                throw new ValidationException("Course code already exists: " + courseCode);
            }
            // Section 4.7: Inactive Match Reactivation Behavior
            existing.setName(course.getName().trim());
            existing.setDepartmentId(course.getDepartmentId());
            existing.setCredits(course.getCredits());
            existing.setSemester(course.getSemester().trim());
            existing.setMaxCapacity(course.getMaxCapacity());
            existing.setActive(true);

            courseDAO.update(existing);
            courseDAO.setActive(existing.getId(), true);

            auditService.log(currentUser.getId(), "REACTIVATE_COURSE", "COURSE", existing.getId(),
                    "active=0", "active=1 (Course: " + courseCode + " - " + existing.getName() + ")");
            return existing;
        }

        course.setCourseCode(courseCode);
        course.setName(course.getName().trim());
        course.setSemester(course.getSemester().trim());
        course.setActive(true);

        Course saved = courseDAO.insert(course);

        auditService.log(currentUser.getId(), "CREATE_COURSE", "COURSE", saved.getId(),
                null, "Created course: " + courseCode + " (" + saved.getName() + ", Credits: " + saved.getCredits() + ")");
        return saved;
    }

    /**
     * Updates an existing course definition (ADMIN only).
     */
    public Course updateCourse(User currentUser, Course course) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        if (course == null || course.getId() <= 0) {
            throw new ValidationException("Valid course record required.");
        }
        validateCourseInput(course);

        Course current = courseDAO.findById(course.getId());
        if (current == null) {
            throw new ValidationException("Course " + course.getId() + " not found.");
        }

        Department dept = departmentDAO.findById(course.getDepartmentId());
        if (dept == null || !dept.isActive()) {
            throw new ValidationException("Department ID " + course.getDepartmentId() + " does not exist or is inactive.");
        }

        String courseCode = course.getCourseCode().trim().toUpperCase();
        Course other = courseDAO.findByCourseCode(courseCode);
        if (other != null && other.getId() != course.getId()) {
            throw new ValidationException("Course code " + courseCode + " is already in use by course ID " + other.getId());
        }

        course.setCourseCode(courseCode);
        course.setName(course.getName().trim());
        course.setSemester(course.getSemester().trim());
        courseDAO.update(course);

        auditService.log(currentUser.getId(), "UPDATE_COURSE", "COURSE", course.getId(),
                current.getName(), course.getName());
        return course;
    }

    /**
     * Soft-deactivates a course (ADMIN only).
     */
    public void deleteCourse(User currentUser, int courseId) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        Course course = courseDAO.findById(courseId);
        if (course == null) {
            throw new ValidationException("Course " + courseId + " not found.");
        }
        if (!course.isActive()) {
            throw new ValidationException("Course " + course.getCourseCode() + " is already inactive.");
        }

        courseDAO.setActive(courseId, false);
        auditService.log(currentUser.getId(), "DEACTIVATE_COURSE", "COURSE", courseId,
                "active=1", "active=0 (Course: " + course.getCourseCode() + ")");
    }

    /**
     * Explicitly reactivates a soft-deleted course (ADMIN only).
     */
    public Course reactivateCourse(User currentUser, int courseId) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        Course course = courseDAO.findById(courseId);
        if (course == null) {
            throw new ValidationException("Course " + courseId + " not found.");
        }
        if (course.isActive()) {
            throw new ValidationException("Course " + course.getCourseCode() + " is already active.");
        }

        courseDAO.setActive(courseId, true);
        course.setActive(true);
        auditService.log(currentUser.getId(), "REACTIVATE_COURSE", "COURSE", courseId,
                "active=0", "active=1 (Course: " + course.getCourseCode() + ")");
        return course;
    }

    public Course getCourseById(User currentUser, int id) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        return courseDAO.findById(id);
    }

    public Course getCourseByCode(User currentUser, String code) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        ValidationUtil.requireNonEmpty(code, "Course code");
        return courseDAO.findByCourseCode(code.trim().toUpperCase());
    }

    public List<Course> getAllActiveCourses(User currentUser) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        return courseDAO.findAllActive();
    }

    public List<Course> getCoursesByDepartment(User currentUser, int departmentId) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        return courseDAO.findByDepartment(departmentId);
    }

    public List<Course> getCoursesBySemester(User currentUser, String semester) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        ValidationUtil.requireNonEmpty(semester, "Semester");
        return courseDAO.findBySemester(semester.trim());
    }

    private void validateCourseInput(Course course) {
        if (course == null) {
            throw new ValidationException("Course entity cannot be null.");
        }
        ValidationUtil.requireNonEmpty(course.getCourseCode(), "Course code");
        ValidationUtil.requireNonEmpty(course.getName(), "Course name");
        ValidationUtil.requireNonEmpty(course.getSemester(), "Semester");
        if (course.getDepartmentId() <= 0) {
            throw new ValidationException("Department ID must be a positive integer.");
        }
        if (course.getCredits() <= 0) {
            throw new ValidationException("Course credits must be strictly positive.");
        }
        if (course.getMaxCapacity() == null || course.getMaxCapacity() <= 0) {
            throw new ValidationException("Course max capacity must be a positive integer.");
        }
    }
}

