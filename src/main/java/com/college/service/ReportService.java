package com.college.service;

import com.college.dao.CourseAssignmentDAO;
import com.college.dao.FacultyDAO;
import com.college.dao.ReportDAO;
import com.college.dao.StudentDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.ValidationException;
import com.college.model.CourseAssignment;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.User;
import com.college.util.ValidationUtil;

import java.util.List;
import java.util.Map;

/**
 * Service managing aggregate institutional reporting with strict role-aware filtering.
 */
public class ReportService {

    private final ReportDAO reportDAO;
    private final FacultyDAO facultyDAO;
    private final StudentDAO studentDAO;
    private final CourseAssignmentDAO courseAssignmentDAO;

    public ReportService() {
        this.reportDAO = new ReportDAO();
        this.facultyDAO = new FacultyDAO();
        this.studentDAO = new StudentDAO();
        this.courseAssignmentDAO = new CourseAssignmentDAO();
    }

    public ReportService(ReportDAO reportDAO, FacultyDAO facultyDAO,
                         StudentDAO studentDAO, CourseAssignmentDAO courseAssignmentDAO) {
        this.reportDAO = reportDAO;
        this.facultyDAO = facultyDAO;
        this.studentDAO = studentDAO;
        this.courseAssignmentDAO = courseAssignmentDAO;
    }

    /**
     * Generates student academic performance reports with role-aware scoping.
     *
     * @param currentUser  authenticated user
     * @param academicYear academic year filter (required)
     * @param departmentId optional department filter
     * @param courseId     optional course filter
     * @return list of report rows as column-to-value maps
     */
    public List<Map<String, Object>> getStudentPerformanceReport(User currentUser, String academicYear,
                                                                 Integer departmentId, Integer courseId) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY, Role.STUDENT);
        ValidationUtil.requireNonEmpty(academicYear, "Academic year");

        if (currentUser.getRole() == Role.ADMIN) {
            return reportDAO.getStudentPerformanceReport(academicYear.trim(), departmentId, courseId, null, null);
        } else if (currentUser.getRole() == Role.FACULTY) {
            Faculty faculty = facultyDAO.findByUserId(currentUser.getId());
            if (faculty == null || !faculty.isActive()) {
                throw new AuthenticationException("Active faculty profile not found.");
            }
            if (courseId != null) {
                CourseAssignment ca = courseAssignmentDAO.findActiveByCourseAndFaculty(
                        courseId, faculty.getId(), academicYear.trim());
                if (ca == null) {
                    throw new AuthenticationException("Faculty can only view performance for their assigned courses.");
                }
                return reportDAO.getStudentPerformanceReport(academicYear.trim(), departmentId, courseId, null, null);
            } else {
                List<CourseAssignment> assignments = courseAssignmentDAO.findActiveByFacultyAndYear(
                        faculty.getId(), academicYear.trim());
                List<Integer> courseIds = assignments.stream().map(CourseAssignment::getCourseId).toList();
                return reportDAO.getStudentPerformanceReport(academicYear.trim(), departmentId, null, null, courseIds);
            }
        } else {
            // STUDENT: can only view their own performance records
            Student student = studentDAO.findByUserId(currentUser.getId());
            if (student == null) {
                throw new AuthenticationException("Student profile not found.");
            }
            return reportDAO.getStudentPerformanceReport(academicYear.trim(), student.getDepartmentId(), courseId, student.getId(), null);
        }
    }

    /**
     * Generates attendance summary metrics with role-based scoping.
     */
    public List<Map<String, Object>> getAttendanceSummaryReport(User currentUser, String academicYear,
                                                                Integer courseId, Integer studentId) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY, Role.STUDENT);
        ValidationUtil.requireNonEmpty(academicYear, "Academic year");

        if (currentUser.getRole() == Role.ADMIN) {
            return reportDAO.getAttendanceSummaryReport(academicYear.trim(), courseId, studentId);
        } else if (currentUser.getRole() == Role.STUDENT) {
            Student student = studentDAO.findByUserId(currentUser.getId());
            if (student == null) {
                throw new AuthenticationException("Student profile not found.");
            }
            // Enforce student scoping strictly to self
            return reportDAO.getAttendanceSummaryReport(academicYear.trim(), courseId, student.getId());
        } else {
            // FACULTY
            Faculty faculty = facultyDAO.findByUserId(currentUser.getId());
            if (faculty == null || !faculty.isActive()) {
                throw new AuthenticationException("Active faculty profile not found.");
            }
            if (courseId != null) {
                CourseAssignment ca = courseAssignmentDAO.findActiveByCourseAndFaculty(
                        courseId, faculty.getId(), academicYear.trim());
                if (ca == null) {
                    throw new AuthenticationException("Faculty can only view attendance for their assigned courses.");
                }
                return reportDAO.getAttendanceSummaryReport(academicYear.trim(), courseId, studentId);
            } else {
                List<CourseAssignment> assignments = courseAssignmentDAO.findActiveByFacultyAndYear(
                        faculty.getId(), academicYear.trim());
                List<Integer> courseIds = assignments.stream().map(CourseAssignment::getCourseId).toList();
                return reportDAO.getAttendanceSummaryReport(academicYear.trim(), null, studentId, courseIds);
            }
        }
    }

    /**
     * Generates attendance defaulters report for students falling below the shortage threshold (default 75.0%).
     *
     * @param currentUser  authenticated user (ADMIN or assigned FACULTY)
     * @param academicYear target academic year
     * @param courseId     optional course filter
     * @param threshold    attendance percentage threshold (defaults to 75.0% if null)
     * @return list of student attendance summary records falling below threshold
     */
    public List<Map<String, Object>> getAttendanceDefaultersReport(User currentUser, String academicYear,
                                                                  Integer courseId, Double threshold) {
        double limit = (threshold != null && threshold >= 0.0) ? threshold : AttendanceService.DEFAULT_ATTENDANCE_THRESHOLD;
        List<Map<String, Object>> summary = getAttendanceSummaryReport(currentUser, academicYear, courseId, null);
        List<Map<String, Object>> defaulters = new java.util.ArrayList<>();
        for (Map<String, Object> row : summary) {
            Number pct = (Number) row.get("attendance_percentage");
            Number total = (Number) row.get("total_sessions");
            if (total != null && total.intValue() > 0 && pct != null && pct.doubleValue() < limit) {
                defaulters.add(row);
            }
        }
        return defaulters;
    }

    /**
     * Generates attendance eligible report for students meeting or exceeding the shortage threshold (>= 75.0%).
     */
    public List<Map<String, Object>> getAttendanceEligibleReport(User currentUser, String academicYear,
                                                                 Integer courseId, Double threshold) {
        double limit = (threshold != null && threshold >= 0.0) ? threshold : AttendanceService.DEFAULT_ATTENDANCE_THRESHOLD;
        List<Map<String, Object>> summary = getAttendanceSummaryReport(currentUser, academicYear, courseId, null);
        List<Map<String, Object>> eligible = new java.util.ArrayList<>();
        for (Map<String, Object> row : summary) {
            Number pct = (Number) row.get("attendance_percentage");
            Number total = (Number) row.get("total_sessions");
            if (total != null && total.intValue() > 0 && pct != null && pct.doubleValue() >= limit) {
                eligible.add(row);
            }
        }
        return eligible;
    }

    /**
     * Generates report of students with zero recorded attendance sessions (neutral N/A status).
     */
    public List<Map<String, Object>> getAttendanceZeroSessionsReport(User currentUser, String academicYear,
                                                                     Integer courseId) {
        List<Map<String, Object>> summary = getAttendanceSummaryReport(currentUser, academicYear, courseId, null);
        List<Map<String, Object>> zeroSessions = new java.util.ArrayList<>();
        for (Map<String, Object> row : summary) {
            Number total = (Number) row.get("total_sessions");
            if (total == null || total.intValue() == 0) {
                zeroSessions.add(row);
            }
        }
        return zeroSessions;
    }

    /**
     * Generates institutional fee collection and refund reconciliation report (ADMIN only).
     */
    public List<Map<String, Object>> getFeeCollectionReport(User currentUser, String academicYear, Integer departmentId) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        ValidationUtil.requireNonEmpty(academicYear, "Academic year");
        return reportDAO.getFeeCollectionReport(academicYear.trim(), departmentId);
    }

    /**
     * Generates outstanding student fee arrears report (ADMIN only).
     */
    public List<Map<String, Object>> getStudentFeeDuesReport(User currentUser, String academicYear, Integer departmentId) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        ValidationUtil.requireNonEmpty(academicYear, "Academic year");
        return reportDAO.getStudentFeeDuesReport(academicYear.trim(), departmentId);
    }

    /**
     * Generates course seat utilization and capacity analysis (ADMIN or FACULTY).
     */
    public List<Map<String, Object>> getCourseEnrollmentCapacityReport(User currentUser, String academicYear, Integer departmentId) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY);
        ValidationUtil.requireNonEmpty(academicYear, "Academic year");

        if (currentUser.getRole() == Role.ADMIN) {
            return reportDAO.getCourseEnrollmentCapacityReport(academicYear.trim(), departmentId);
        } else {
            // FACULTY: scoped to assigned courses
            Faculty faculty = facultyDAO.findByUserId(currentUser.getId());
            if (faculty == null || !faculty.isActive()) {
                throw new AuthenticationException("Active faculty profile not found.");
            }
            List<CourseAssignment> assignments = courseAssignmentDAO.findActiveByFacultyAndYear(
                    faculty.getId(), academicYear.trim());
            List<Integer> courseIds = assignments.stream().map(CourseAssignment::getCourseId).toList();
            return reportDAO.getCourseEnrollmentCapacityReport(academicYear.trim(), departmentId, courseIds);
        }
    }

    /**
     * Generates university-wide department metrics summary (ADMIN only).
     */
    public List<Map<String, Object>> getDepartmentSummaryReport(User currentUser) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        return reportDAO.getDepartmentSummaryReport();
    }
}

