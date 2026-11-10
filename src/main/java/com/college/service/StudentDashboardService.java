package com.college.service;

import com.college.dao.AttendanceDAO;
import com.college.dao.CourseDAO;
import com.college.dao.EnrollmentDAO;
import com.college.dao.FeePaymentDAO;
import com.college.dao.FeeStructureDAO;
import com.college.dao.MarkDAO;
import com.college.dao.StudentDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.ValidationException;
import com.college.model.Attendance;
import com.college.model.Course;
import com.college.model.Enrollment;
import com.college.model.FeePayment;
import com.college.model.FeeStructure;
import com.college.model.Mark;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.User;
import com.college.util.ValidationUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service providing isolated, read-only dashboard data for authenticated students.
 * Guarantees zero cross-student data leakage by deriving identity strictly from the authenticated User.
 */
public class StudentDashboardService {

    private final StudentDAO studentDAO;
    private final EnrollmentDAO enrollmentDAO;
    private final CourseDAO courseDAO;
    private final AttendanceDAO attendanceDAO;
    private final MarkDAO markDAO;
    private final FeeStructureDAO feeStructureDAO;
    private final FeePaymentDAO feePaymentDAO;

    public StudentDashboardService() {
        this.studentDAO = new StudentDAO();
        this.enrollmentDAO = new EnrollmentDAO();
        this.courseDAO = new CourseDAO();
        this.attendanceDAO = new AttendanceDAO();
        this.markDAO = new MarkDAO();
        this.feeStructureDAO = new FeeStructureDAO();
        this.feePaymentDAO = new FeePaymentDAO();
    }

    public StudentDashboardService(StudentDAO studentDAO, EnrollmentDAO enrollmentDAO,
                                   CourseDAO courseDAO, AttendanceDAO attendanceDAO,
                                   MarkDAO markDAO, FeeStructureDAO feeStructureDAO,
                                   FeePaymentDAO feePaymentDAO) {
        this.studentDAO = studentDAO;
        this.enrollmentDAO = enrollmentDAO;
        this.courseDAO = courseDAO;
        this.attendanceDAO = attendanceDAO;
        this.markDAO = markDAO;
        this.feeStructureDAO = feeStructureDAO;
        this.feePaymentDAO = feePaymentDAO;
    }

    /**
     * Data Transfer Object encapsulating student dashboard metrics.
     */
    public static class StudentDashboardData {
        private final Student studentProfile;
        private final String academicYear;
        private final List<Enrollment> enrollments;
        private final Map<Integer, Course> courseCatalog;
        private final Map<Integer, List<Attendance>> attendanceByEnrollment;
        private final double overallAttendancePercentage;
        private final List<Mark> marks;
        private final List<FeePayment> paymentHistory;
        private final long totalFeesDuePaise;
        private final long totalFeesPaidPaise;
        private final long totalOutstandingPaise;

        public StudentDashboardData(Student studentProfile, String academicYear,
                                    List<Enrollment> enrollments, Map<Integer, Course> courseCatalog,
                                    Map<Integer, List<Attendance>> attendanceByEnrollment,
                                    double overallAttendancePercentage, List<Mark> marks,
                                    List<FeePayment> paymentHistory, long totalFeesDuePaise,
                                    long totalFeesPaidPaise, long totalOutstandingPaise) {
            this.studentProfile = studentProfile;
            this.academicYear = academicYear;
            this.enrollments = enrollments;
            this.courseCatalog = courseCatalog;
            this.attendanceByEnrollment = attendanceByEnrollment;
            this.overallAttendancePercentage = overallAttendancePercentage;
            this.marks = marks;
            this.paymentHistory = paymentHistory;
            this.totalFeesDuePaise = totalFeesDuePaise;
            this.totalFeesPaidPaise = totalFeesPaidPaise;
            this.totalOutstandingPaise = totalOutstandingPaise;
        }

        public Student getStudentProfile() {
            return studentProfile;
        }

        public String getAcademicYear() {
            return academicYear;
        }

        public List<Enrollment> getEnrollments() {
            return enrollments;
        }

        public Map<Integer, Course> getCourseCatalog() {
            return courseCatalog;
        }

        public Map<Integer, List<Attendance>> getAttendanceByEnrollment() {
            return attendanceByEnrollment;
        }

        public double getOverallAttendancePercentage() {
            return overallAttendancePercentage;
        }

        public List<Mark> getMarks() {
            return marks;
        }

        public List<FeePayment> getPaymentHistory() {
            return paymentHistory;
        }

        public long getTotalFeesDuePaise() {
            return totalFeesDuePaise;
        }

        public long getTotalFeesPaidPaise() {
            return totalFeesPaidPaise;
        }

        public long getTotalOutstandingPaise() {
            return totalOutstandingPaise;
        }
    }

    /**
     * Assembles dashboard data for the authenticated student for a specific academic year.
     *
     * @param currentUser  authenticated user (must have role STUDENT)
     * @param academicYear academic year (e.g. '2026-2027')
     * @return populated StudentDashboardData
     */
    public StudentDashboardData getDashboardData(User currentUser, String academicYear) {
        AuthService.requireRole(currentUser, Role.STUDENT);
        ValidationUtil.requireNonEmpty(academicYear, "Academic year");
        String trimmedYear = academicYear.trim();

        // Strict identity derivation: student record must match currentUser.getId()
        Student student = studentDAO.findByUserId(currentUser.getId());
        if (student == null || !student.isActive()) {
            throw new AuthenticationException("Active student profile not found for authenticated user.");
        }

        // 1. Course Enrollments for academic year
        List<Enrollment> enrollments = enrollmentDAO.findByStudentAndYear(student.getId(), trimmedYear);
        Map<Integer, Course> courseCatalog = new LinkedHashMap<>();
        Map<Integer, List<Attendance>> attendanceByEnrollment = new LinkedHashMap<>();
        List<Mark> studentMarks = new ArrayList<>();

        int totalSessionsAll = 0;
        int presentSessionsAll = 0;
        int lateSessionsAll = 0;

        for (Enrollment enrollment : enrollments) {
            Course course = courseDAO.findById(enrollment.getCourseId());
            if (course != null) {
                courseCatalog.put(course.getId(), course);
            }

            // Attendance records for enrollment
            List<Attendance> attendanceList = attendanceDAO.findByEnrollment(enrollment.getId());
            attendanceByEnrollment.put(enrollment.getId(), attendanceList);

            for (Attendance att : attendanceList) {
                totalSessionsAll++;
                if ("PRESENT".equalsIgnoreCase(att.getStatus())) {
                    presentSessionsAll++;
                } else if ("LATE".equalsIgnoreCase(att.getStatus())) {
                    lateSessionsAll++;
                }
            }

            // Assessment marks for enrollment
            List<Mark> marks = markDAO.findByEnrollment(enrollment.getId());
            studentMarks.addAll(marks);
        }

        double overallAttendancePercentage = AttendanceService.calculatePercentage(
                presentSessionsAll, lateSessionsAll, totalSessionsAll);

        // 2. Financial Metrics for student
        List<FeeStructure> feeStructures = feeStructureDAO.findByDepartmentAndYear(student.getDepartmentId(), trimmedYear);
        long totalFeesDuePaise = 0L;
        long totalFeesPaidPaise = 0L;

        for (FeeStructure fs : feeStructures) {
            if (fs.isActive() && student.getSemester().trim().equalsIgnoreCase(fs.getSemester().trim())) {
                totalFeesDuePaise += fs.getAmountPaise();
                long completed = feePaymentDAO.getTotalCompletedPaise(student.getId(), fs.getId());
                long refunded = feePaymentDAO.getTotalRefundedPaise(student.getId(), fs.getId());
                long netPaid = completed - refunded;
                totalFeesPaidPaise += netPaid;
            }
        }

        long totalOutstandingPaise = totalFeesDuePaise - totalFeesPaidPaise;
        if (totalOutstandingPaise < 0L) {
            totalOutstandingPaise = 0L;
        }

        List<FeePayment> paymentHistory = feePaymentDAO.findByStudent(student.getId());

        return new StudentDashboardData(
                student,
                trimmedYear,
                enrollments,
                courseCatalog,
                attendanceByEnrollment,
                overallAttendancePercentage,
                studentMarks,
                paymentHistory,
                totalFeesDuePaise,
                totalFeesPaidPaise,
                totalOutstandingPaise
        );
    }
}

