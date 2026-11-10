package com.college.service;

import com.college.dao.CourseAssignmentDAO;
import com.college.dao.CourseDAO;
import com.college.dao.EnrollmentDAO;
import com.college.dao.ExaminationDAO;
import com.college.dao.FacultyDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.ValidationException;
import com.college.model.Course;
import com.college.model.CourseAssignment;
import com.college.model.Enrollment;
import com.college.model.Examination;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.User;
import com.college.util.ValidationUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service providing isolated, read-only dashboard data for authenticated faculty members.
 * Guarantees zero cross-faculty data leakage by deriving identity strictly from the authenticated User.
 */
public class FacultyDashboardService {

    private final FacultyDAO facultyDAO;
    private final CourseAssignmentDAO courseAssignmentDAO;
    private final CourseDAO courseDAO;
    private final EnrollmentDAO enrollmentDAO;
    private final ExaminationDAO examinationDAO;

    public FacultyDashboardService() {
        this.facultyDAO = new FacultyDAO();
        this.courseAssignmentDAO = new CourseAssignmentDAO();
        this.courseDAO = new CourseDAO();
        this.enrollmentDAO = new EnrollmentDAO();
        this.examinationDAO = new ExaminationDAO();
    }

    public FacultyDashboardService(FacultyDAO facultyDAO, CourseAssignmentDAO courseAssignmentDAO,
                                   CourseDAO courseDAO, EnrollmentDAO enrollmentDAO,
                                   ExaminationDAO examinationDAO) {
        this.facultyDAO = facultyDAO;
        this.courseAssignmentDAO = courseAssignmentDAO;
        this.courseDAO = courseDAO;
        this.enrollmentDAO = enrollmentDAO;
        this.examinationDAO = examinationDAO;
    }

    /**
     * Data Transfer Object encapsulating faculty dashboard metrics.
     */
    public static class FacultyDashboardData {
        private final Faculty facultyProfile;
        private final String academicYear;
        private final List<CourseAssignment> assignments;
        private final Map<Integer, Course> courseCatalog;
        private final Map<Integer, Integer> activeEnrollmentCounts;
        private final Map<Integer, List<Enrollment>> courseRosters;
        private final Map<Integer, List<Examination>> scheduledExaminations;

        public FacultyDashboardData(Faculty facultyProfile, String academicYear,
                                    List<CourseAssignment> assignments, Map<Integer, Course> courseCatalog,
                                    Map<Integer, Integer> activeEnrollmentCounts,
                                    Map<Integer, List<Enrollment>> courseRosters,
                                    Map<Integer, List<Examination>> scheduledExaminations) {
            this.facultyProfile = facultyProfile;
            this.academicYear = academicYear;
            this.assignments = assignments;
            this.courseCatalog = courseCatalog;
            this.activeEnrollmentCounts = activeEnrollmentCounts;
            this.courseRosters = courseRosters;
            this.scheduledExaminations = scheduledExaminations;
        }

        public Faculty getFacultyProfile() {
            return facultyProfile;
        }

        public String getAcademicYear() {
            return academicYear;
        }

        public List<CourseAssignment> getAssignments() {
            return assignments;
        }

        public Map<Integer, Course> getCourseCatalog() {
            return courseCatalog;
        }

        public Map<Integer, Integer> getActiveEnrollmentCounts() {
            return activeEnrollmentCounts;
        }

        public Map<Integer, List<Enrollment>> getCourseRosters() {
            return courseRosters;
        }

        public Map<Integer, List<Examination>> getScheduledExaminations() {
            return scheduledExaminations;
        }
    }

    /**
     * Assembles dashboard data for the authenticated faculty member for a specific academic year.
     *
     * @param currentUser  authenticated user (must have role FACULTY)
     * @param academicYear academic year (e.g. '2026-2027')
     * @return populated FacultyDashboardData
     */
    public FacultyDashboardData getDashboardData(User currentUser, String academicYear) {
        AuthService.requireRole(currentUser, Role.FACULTY);
        ValidationUtil.requireNonEmpty(academicYear, "Academic year");
        String trimmedYear = academicYear.trim();

        // Strict identity derivation: faculty record must match currentUser.getId()
        Faculty faculty = facultyDAO.findByUserId(currentUser.getId());
        if (faculty == null || !faculty.isActive()) {
            throw new AuthenticationException("Active faculty profile not found for authenticated user.");
        }

        // Active teaching assignments for the academic year
        List<CourseAssignment> assignments = courseAssignmentDAO.findActiveByFacultyAndYear(faculty.getId(), trimmedYear);

        Map<Integer, Course> courseCatalog = new LinkedHashMap<>();
        Map<Integer, Integer> activeEnrollmentCounts = new LinkedHashMap<>();
        Map<Integer, List<Enrollment>> courseRosters = new LinkedHashMap<>();
        Map<Integer, List<Examination>> scheduledExaminations = new LinkedHashMap<>();

        for (CourseAssignment assignment : assignments) {
            int courseId = assignment.getCourseId();

            if (!courseCatalog.containsKey(courseId)) {
                Course course = courseDAO.findById(courseId);
                if (course != null) {
                    courseCatalog.put(courseId, course);
                }
            }

            // Enrollment count & roster
            int activeCount = enrollmentDAO.countActiveByCourseAndYear(courseId, trimmedYear);
            activeEnrollmentCounts.put(courseId, activeCount);

            List<Enrollment> roster = enrollmentDAO.findActiveByCourseAndYear(courseId, trimmedYear);
            courseRosters.put(courseId, roster);

            // Examinations scheduled for course in academic year
            List<Examination> exams = examinationDAO.findByCourseAndYear(courseId, trimmedYear);
            scheduledExaminations.put(courseId, exams);
        }

        return new FacultyDashboardData(
                faculty,
                trimmedYear,
                assignments,
                courseCatalog,
                activeEnrollmentCounts,
                courseRosters,
                scheduledExaminations
        );
    }
}

