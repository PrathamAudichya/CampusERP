package com.college.service;

import com.college.dao.AuditLogDAO;
import com.college.dao.CourseDAO;
import com.college.dao.EnrollmentDAO;
import com.college.dao.FacultyDAO;
import com.college.dao.FeePaymentDAO;
import com.college.dao.StudentDAO;
import com.college.model.AuditLog;
import com.college.model.Enrollment;
import com.college.model.FeePayment;
import com.college.model.Role;
import com.college.model.User;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service aggregating institutional KPIs, academic enrollment statistics,
 * financial totals, and recent audit activity for the Administrator Dashboard.
 */
public class AdminDashboardService {

    private final StudentDAO studentDAO;
    private final FacultyDAO facultyDAO;
    private final CourseDAO courseDAO;
    private final EnrollmentDAO enrollmentDAO;
    private final FeePaymentDAO feePaymentDAO;
    private final AuditLogDAO auditLogDAO;

    public AdminDashboardService() {
        this.studentDAO = new StudentDAO();
        this.facultyDAO = new FacultyDAO();
        this.courseDAO = new CourseDAO();
        this.enrollmentDAO = new EnrollmentDAO();
        this.feePaymentDAO = new FeePaymentDAO();
        this.auditLogDAO = new AuditLogDAO();
    }

    public AdminDashboardService(StudentDAO studentDAO, FacultyDAO facultyDAO,
                                 CourseDAO courseDAO, EnrollmentDAO enrollmentDAO,
                                 FeePaymentDAO feePaymentDAO, AuditLogDAO auditLogDAO) {
        this.studentDAO = studentDAO;
        this.facultyDAO = facultyDAO;
        this.courseDAO = courseDAO;
        this.enrollmentDAO = enrollmentDAO;
        this.feePaymentDAO = feePaymentDAO;
        this.auditLogDAO = auditLogDAO;
    }

    /**
     * Aggregates real institutional statistics from the database for the active administrator.
     *
     * @param currentUser authenticated user (must possess Role.ADMIN)
     * @return populated AdminDashboardData
     */
    public AdminDashboardData getDashboardData(User currentUser) {
        AuthService.requireRole(currentUser, Role.ADMIN);

        int totalStudents = studentDAO.findAll().size();
        int totalFaculty = facultyDAO.findAll().size();
        int totalCourses = courseDAO.findAll().size();

        List<Enrollment> allEnrollments = enrollmentDAO.findAll();
        int activeEnrollments = (int) allEnrollments.stream()
                .filter(e -> "ACTIVE".equalsIgnoreCase(e.getStatus()))
                .count();

        List<FeePayment> allPayments = feePaymentDAO.findAll();
        long totalFeeCollectionPaise = allPayments.stream()
                .filter(p -> "SUCCESS".equalsIgnoreCase(p.getStatus()))
                .mapToLong(FeePayment::getAmountPaidPaise)
                .sum();

        List<AuditLog> allLogs = auditLogDAO.findAll();
        List<AuditLog> recentAuditLogs = allLogs.stream()
                .sorted((a, b) -> Integer.compare(b.getId(), a.getId())) // Descending by ID
                .limit(10)
                .collect(Collectors.toList());

        List<Enrollment> recentEnrollments = allEnrollments.stream()
                .sorted((a, b) -> Integer.compare(b.getId(), a.getId()))
                .limit(8)
                .collect(Collectors.toList());

        return new AdminDashboardData(
                totalStudents,
                totalFaculty,
                totalCourses,
                activeEnrollments,
                totalFeeCollectionPaise,
                recentAuditLogs,
                recentEnrollments
        );
    }

    /**
     * Immutable data transfer object containing real administrative KPIs.
     */
    public static class AdminDashboardData {
        private final int totalStudents;
        private final int totalFaculty;
        private final int totalCourses;
        private final int activeEnrollments;
        private final long totalFeeCollectionPaise;
        private final List<AuditLog> recentAuditLogs;
        private final List<Enrollment> recentEnrollments;

        public AdminDashboardData(int totalStudents, int totalFaculty, int totalCourses,
                                  int activeEnrollments, long totalFeeCollectionPaise,
                                  List<AuditLog> recentAuditLogs, List<Enrollment> recentEnrollments) {
            this.totalStudents = totalStudents;
            this.totalFaculty = totalFaculty;
            this.totalCourses = totalCourses;
            this.activeEnrollments = activeEnrollments;
            this.totalFeeCollectionPaise = totalFeeCollectionPaise;
            this.recentAuditLogs = recentAuditLogs;
            this.recentEnrollments = recentEnrollments;
        }

        public int getTotalStudents() { return totalStudents; }
        public int getTotalFaculty() { return totalFaculty; }
        public int getTotalCourses() { return totalCourses; }
        public int getActiveEnrollments() { return activeEnrollments; }
        public long getTotalFeeCollectionPaise() { return totalFeeCollectionPaise; }
        public List<AuditLog> getRecentAuditLogs() { return recentAuditLogs; }
        public List<Enrollment> getRecentEnrollments() { return recentEnrollments; }
    }
}

