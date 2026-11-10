package com.college.dao;

import com.college.config.DBConnection;
import com.college.exception.DataAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for complex reporting and analytics queries.
 *
 * <p>Executes multi-table joins, grouped aggregations, and filtered summaries for:
 * <ul>
 *   <li>Student Academic Performance & Grade Reports</li>
 *   <li>Attendance Percentage & Session Summaries</li>
 *   <li>Fee Collections, Refunds, and Outstanding Dues</li>
 *   <li>Course Enrollment Capacities & Roster Utilization</li>
 *   <li>Department-Level Institutional Metrics</li>
 * </ul>
 * </p>
 */
public class ReportDAO {

    /**
     * Generates a student performance and marks report with optional student and course ID list filters.
     *
     * @param conn database connection
     * @param academicYear academic year filter (required)
     * @param departmentId optional department filter (null for all departments)
     * @param courseId optional course filter (null for all courses)
     * @param studentId optional student filter (null for all students)
     * @param courseIds optional list of authorized course IDs (e.g. for faculty scoping)
     * @return list of rows as attribute maps
     */
    public List<Map<String, Object>> getStudentPerformanceReport(Connection conn, String academicYear,
                                                                 Integer departmentId, Integer courseId,
                                                                 Integer studentId, List<Integer> courseIds) {
        if (courseIds != null && courseIds.isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder sql = new StringBuilder(
            "SELECT s.student_id AS student_id_number, " +
            "       (s.first_name || ' ' || s.last_name) AS student_name, " +
            "       d.code AS department_code, d.name AS department_name, " +
            "       c.course_code, c.name AS course_name, " +
            "       ex.exam_name, ex.exam_type, ex.total_marks, " +
            "       m.marks_obtained, " +
            "       ROUND((m.marks_obtained * 100.0) / ex.total_marks, 2) AS percentage, " +
            "       m.grade, m.remarks " +
            "FROM marks m " +
            "JOIN enrollments e ON m.enrollment_id = e.id " +
            "JOIN students s ON e.student_id = s.id " +
            "JOIN departments d ON s.department_id = d.id " +
            "JOIN examinations ex ON m.examination_id = ex.id " +
            "JOIN courses c ON ex.course_id = c.id " +
            "WHERE e.academic_year = ? "
        );

        if (studentId != null) {
            sql.append("AND s.id = ? ");
        }
        if (departmentId != null) {
            sql.append("AND s.department_id = ? ");
        }
        if (courseId != null) {
            sql.append("AND c.id = ? ");
        }
        if (courseIds != null) {
            String placeholders = String.join(",", Collections.nCopies(courseIds.size(), "?"));
            sql.append("AND c.id IN (").append(placeholders).append(") ");
        }
        sql.append("ORDER BY d.code, c.course_code, s.student_id, ex.exam_date");

        try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            pstmt.setString(paramIndex++, academicYear);
            if (studentId != null) {
                pstmt.setInt(paramIndex++, studentId);
            }
            if (departmentId != null) {
                pstmt.setInt(paramIndex++, departmentId);
            }
            if (courseId != null) {
                pstmt.setInt(paramIndex++, courseId);
            }
            if (courseIds != null) {
                for (Integer cid : courseIds) {
                    pstmt.setInt(paramIndex++, cid);
                }
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                return extractRows(rs);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to generate student performance report: " + e.getMessage(), e);
        }
    }

    /**
     * Overload supporting studentId filter without courseIds list.
     */
    public List<Map<String, Object>> getStudentPerformanceReport(Connection conn, String academicYear,
                                                                 Integer departmentId, Integer courseId, Integer studentId) {
        return getStudentPerformanceReport(conn, academicYear, departmentId, courseId, studentId, null);
    }

    /**
     * Backward-compatible overload for performance report using Connection.
     */
    public List<Map<String, Object>> getStudentPerformanceReport(Connection conn, String academicYear,
                                                                 Integer departmentId, Integer courseId) {
        return getStudentPerformanceReport(conn, academicYear, departmentId, courseId, null, null);
    }

    /**
     * Convenience method to generate student performance report using managed connection.
     */
    public List<Map<String, Object>> getStudentPerformanceReport(String academicYear, Integer departmentId, Integer courseId,
                                                                 Integer studentId, List<Integer> courseIds) {
        try (Connection conn = DBConnection.getConnection()) {
            return getStudentPerformanceReport(conn, academicYear, departmentId, courseId, studentId, courseIds);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> getStudentPerformanceReport(String academicYear, Integer departmentId, Integer courseId, Integer studentId) {
        return getStudentPerformanceReport(academicYear, departmentId, courseId, studentId, null);
    }

    public List<Map<String, Object>> getStudentPerformanceReport(String academicYear, Integer departmentId, Integer courseId) {
        return getStudentPerformanceReport(academicYear, departmentId, courseId, null, null);
    }

    /**
     * Generates an attendance summary report showing session counts and attendance rates.
     *
     * @param conn database connection
     * @param academicYear academic year filter (required)
     * @param courseId optional course filter (null for all courses)
     * @param studentId optional student filter (null for all students)
     * @param courseIds optional list of authorized course IDs (e.g. for faculty scoping)
     * @return list of rows as attribute maps
     */
    public List<Map<String, Object>> getAttendanceSummaryReport(Connection conn, String academicYear,
                                                                Integer courseId, Integer studentId,
                                                                List<Integer> courseIds) {
        if (courseIds != null && courseIds.isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder sql = new StringBuilder(
            "SELECT s.id AS student_id, s.student_id AS student_id_number, " +
            "       (s.first_name || ' ' || s.last_name) AS student_name, " +
            "       c.id AS course_id, c.course_code, c.name AS course_name, " +
            "       e.academic_year, " +
            "       COUNT(a.id) AS total_sessions, " +
            "       SUM(CASE WHEN a.status = 'PRESENT' THEN 1 ELSE 0 END) AS present_count, " +
            "       SUM(CASE WHEN a.status = 'ABSENT' THEN 1 ELSE 0 END) AS absent_count, " +
            "       SUM(CASE WHEN a.status = 'LATE' THEN 1 ELSE 0 END) AS late_count, " +
            "       CASE WHEN COUNT(a.id) > 0 THEN " +
            "            ROUND((SUM(CASE WHEN a.status = 'PRESENT' THEN 1.0 WHEN a.status = 'LATE' THEN 0.5 ELSE 0.0 END) * 100.0) / COUNT(a.id), 2) " +
            "            ELSE 0.0 END AS attendance_percentage " +
            "FROM enrollments e " +
            "JOIN students s ON e.student_id = s.id " +
            "JOIN courses c ON e.course_id = c.id " +
            "LEFT JOIN attendance a ON a.enrollment_id = e.id " +
            "WHERE e.academic_year = ? AND e.status = 'ACTIVE' "
        );

        if (courseId != null) {
            sql.append("AND e.course_id = ? ");
        }
        if (studentId != null) {
            sql.append("AND e.student_id = ? ");
        }
        if (courseIds != null) {
            String placeholders = String.join(",", Collections.nCopies(courseIds.size(), "?"));
            sql.append("AND e.course_id IN (").append(placeholders).append(") ");
        }
        sql.append("GROUP BY e.id, s.id, s.student_id, s.first_name, s.last_name, c.id, c.course_code, c.name, e.academic_year ");
        sql.append("ORDER BY c.course_code, s.student_id");

        try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            pstmt.setString(paramIndex++, academicYear);
            if (courseId != null) {
                pstmt.setInt(paramIndex++, courseId);
            }
            if (studentId != null) {
                pstmt.setInt(paramIndex++, studentId);
            }
            if (courseIds != null) {
                for (Integer cid : courseIds) {
                    pstmt.setInt(paramIndex++, cid);
                }
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                return extractRows(rs);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to generate attendance summary report: " + e.getMessage(), e);
        }
    }

    /**
     * Backward-compatible overload for attendance summary report using Connection.
     */
    public List<Map<String, Object>> getAttendanceSummaryReport(Connection conn, String academicYear,
                                                                Integer courseId, Integer studentId) {
        return getAttendanceSummaryReport(conn, academicYear, courseId, studentId, null);
    }

    /**
     * Convenience method to generate attendance summary report using a managed connection with courseIds filter.
     */
    public List<Map<String, Object>> getAttendanceSummaryReport(String academicYear, Integer courseId,
                                                                Integer studentId, List<Integer> courseIds) {
        try (Connection conn = DBConnection.getConnection()) {
            return getAttendanceSummaryReport(conn, academicYear, courseId, studentId, courseIds);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Backward-compatible convenience method to generate attendance summary report using a managed connection.
     */
    public List<Map<String, Object>> getAttendanceSummaryReport(String academicYear, Integer courseId, Integer studentId) {
        return getAttendanceSummaryReport(academicYear, courseId, studentId, null);
    }

    /**
     * Generates a fee collection and reconciliation report across fee structures.
     *
     * @param conn database connection
     * @param academicYear academic year filter (required)
     * @param departmentId optional department filter
     * @return list of rows as attribute maps
     */
    public List<Map<String, Object>> getFeeCollectionReport(Connection conn, String academicYear, Integer departmentId) {
        StringBuilder sql = new StringBuilder(
            "SELECT d.code AS department_code, d.name AS department_name, " +
            "       fs.id AS fee_structure_id, fs.semester, fs.fee_type, fs.amount_paise, " +
            "       ROUND(fs.amount_paise / 100.0, 2) AS amount_rupees, " +
            "       COALESCE(paid.total_completed_paise, 0) AS total_collected_paise, " +
            "       ROUND(COALESCE(paid.total_completed_paise, 0) / 100.0, 2) AS total_collected_rupees, " +
            "       COALESCE(ref.total_refunded_paise, 0) AS total_refunded_paise, " +
            "       ROUND(COALESCE(ref.total_refunded_paise, 0) / 100.0, 2) AS total_refunded_rupees, " +
            "       (COALESCE(paid.total_completed_paise, 0) - COALESCE(ref.total_refunded_paise, 0)) AS net_collected_paise, " +
            "       ROUND((COALESCE(paid.total_completed_paise, 0) - COALESCE(ref.total_refunded_paise, 0)) / 100.0, 2) AS net_collected_rupees " +
            "FROM fee_structures fs " +
            "JOIN departments d ON fs.department_id = d.id " +
            "LEFT JOIN ( " +
            "    SELECT fee_structure_id, SUM(amount_paid_paise) AS total_completed_paise " +
            "    FROM fee_payments " +
            "    WHERE status = 'COMPLETED' " +
            "    GROUP BY fee_structure_id " +
            ") paid ON fs.id = paid.fee_structure_id " +
            "LEFT JOIN ( " +
            "    SELECT fee_structure_id, SUM(amount_paid_paise) AS total_refunded_paise " +
            "    FROM fee_payments " +
            "    WHERE status = 'REFUNDED' " +
            "    GROUP BY fee_structure_id " +
            ") ref ON fs.id = ref.fee_structure_id " +
            "WHERE fs.academic_year = ? "
        );

        if (departmentId != null) {
            sql.append("AND fs.department_id = ? ");
        }
        sql.append("ORDER BY d.code, fs.semester, fs.fee_type");

        try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            pstmt.setString(paramIndex++, academicYear);
            if (departmentId != null) {
                pstmt.setInt(paramIndex++, departmentId);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                return extractRows(rs);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to generate fee collection report: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to generate fee collection report using a managed connection.
     */
    public List<Map<String, Object>> getFeeCollectionReport(String academicYear, Integer departmentId) {
        try (Connection conn = DBConnection.getConnection()) {
            return getFeeCollectionReport(conn, academicYear, departmentId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a student outstanding fee dues report.
     *
     * @param conn database connection
     * @param academicYear academic year filter
     * @param departmentId optional department filter
     * @return list of rows as attribute maps
     */
    public List<Map<String, Object>> getStudentFeeDuesReport(Connection conn, String academicYear, Integer departmentId) {
        StringBuilder sql = new StringBuilder(
            "SELECT s.id AS student_id, s.student_id AS student_id_number, " +
            "       (s.first_name || ' ' || s.last_name) AS student_name, " +
            "       d.code AS department_code, s.semester, " +
            "       fs.id AS fee_structure_id, fs.fee_type, fs.amount_paise, " +
            "       ROUND(fs.amount_paise / 100.0, 2) AS fee_amount_rupees, " +
            "       (COALESCE(paid.completed_paise, 0) - COALESCE(ref.refunded_paise, 0)) AS net_paid_paise, " +
            "       ROUND((COALESCE(paid.completed_paise, 0) - COALESCE(ref.refunded_paise, 0)) / 100.0, 2) AS net_paid_rupees, " +
            "       (fs.amount_paise - (COALESCE(paid.completed_paise, 0) - COALESCE(ref.refunded_paise, 0))) AS outstanding_paise, " +
            "       ROUND((fs.amount_paise - (COALESCE(paid.completed_paise, 0) - COALESCE(ref.refunded_paise, 0))) / 100.0, 2) AS outstanding_rupees " +
            "FROM students s " +
            "JOIN departments d ON s.department_id = d.id " +
            "JOIN fee_structures fs ON fs.department_id = s.department_id " +
            "                       AND LOWER(fs.semester) = LOWER(s.semester) " +
            "                       AND fs.academic_year = ? AND fs.active = 1 " +
            "LEFT JOIN ( " +
            "    SELECT student_id, fee_structure_id, SUM(amount_paid_paise) AS completed_paise " +
            "    FROM fee_payments WHERE status = 'COMPLETED' " +
            "    GROUP BY student_id, fee_structure_id " +
            ") paid ON paid.student_id = s.id AND paid.fee_structure_id = fs.id " +
            "LEFT JOIN ( " +
            "    SELECT student_id, fee_structure_id, SUM(amount_paid_paise) AS refunded_paise " +
            "    FROM fee_payments WHERE status = 'REFUNDED' " +
            "    GROUP BY student_id, fee_structure_id " +
            ") ref ON ref.student_id = s.id AND ref.fee_structure_id = fs.id " +
            "WHERE s.active = 1 "
        );

        if (departmentId != null) {
            sql.append("AND s.department_id = ? ");
        }
        sql.append("ORDER BY d.code, s.student_id, fs.fee_type");

        try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            pstmt.setString(paramIndex++, academicYear);
            if (departmentId != null) {
                pstmt.setInt(paramIndex++, departmentId);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                return extractRows(rs);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to generate student fee dues report: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to generate student fee dues report using a managed connection.
     */
    public List<Map<String, Object>> getStudentFeeDuesReport(String academicYear, Integer departmentId) {
        try (Connection conn = DBConnection.getConnection()) {
            return getStudentFeeDuesReport(conn, academicYear, departmentId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a course enrollment capacity and utilization report with optional course ID list filter.
     *
     * @param conn database connection
     * @param academicYear academic year filter
     * @param departmentId optional department filter
     * @param courseIds optional list of authorized course IDs (e.g. for faculty scoping)
     * @return list of rows as attribute maps
     */
    public List<Map<String, Object>> getCourseEnrollmentCapacityReport(Connection conn, String academicYear,
                                                                       Integer departmentId, List<Integer> courseIds) {
        if (courseIds != null && courseIds.isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder sql = new StringBuilder(
            "SELECT c.id AS course_id, c.course_code, c.name AS course_name, " +
            "       d.code AS department_code, c.credits, c.semester, c.max_capacity, " +
            "       COUNT(e.id) AS enrolled_count, " +
            "       CASE WHEN c.max_capacity IS NOT NULL THEN (c.max_capacity - COUNT(e.id)) ELSE NULL END AS remaining_seats, " +
            "       CASE WHEN c.max_capacity IS NOT NULL AND c.max_capacity > 0 THEN " +
            "            ROUND((COUNT(e.id) * 100.0) / c.max_capacity, 2) ELSE 0.0 END AS utilization_percentage " +
            "FROM courses c " +
            "JOIN departments d ON c.department_id = d.id " +
            "LEFT JOIN enrollments e ON e.course_id = c.id AND e.academic_year = ? AND e.status = 'ACTIVE' " +
            "WHERE c.active = 1 "
        );

        if (departmentId != null) {
            sql.append("AND c.department_id = ? ");
        }
        if (courseIds != null) {
            String placeholders = String.join(",", Collections.nCopies(courseIds.size(), "?"));
            sql.append("AND c.id IN (").append(placeholders).append(") ");
        }
        sql.append("GROUP BY c.id, c.course_code, c.name, d.code, c.credits, c.semester, c.max_capacity ");
        sql.append("ORDER BY d.code, c.course_code");

        try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            pstmt.setString(paramIndex++, academicYear);
            if (departmentId != null) {
                pstmt.setInt(paramIndex++, departmentId);
            }
            if (courseIds != null) {
                for (Integer cid : courseIds) {
                    pstmt.setInt(paramIndex++, cid);
                }
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                return extractRows(rs);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to generate course capacity report: " + e.getMessage(), e);
        }
    }

    /**
     * Backward-compatible overload for course capacity report using Connection.
     */
    public List<Map<String, Object>> getCourseEnrollmentCapacityReport(Connection conn, String academicYear, Integer departmentId) {
        return getCourseEnrollmentCapacityReport(conn, academicYear, departmentId, null);
    }

    /**
     * Convenience method to generate course capacity report using a managed connection with courseIds filter.
     */
    public List<Map<String, Object>> getCourseEnrollmentCapacityReport(String academicYear, Integer departmentId, List<Integer> courseIds) {
        try (Connection conn = DBConnection.getConnection()) {
            return getCourseEnrollmentCapacityReport(conn, academicYear, departmentId, courseIds);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Backward-compatible convenience method to generate course capacity report using a managed connection.
     */
    public List<Map<String, Object>> getCourseEnrollmentCapacityReport(String academicYear, Integer departmentId) {
        return getCourseEnrollmentCapacityReport(academicYear, departmentId, null);
    }

    /**
     * Generates a high-level department institutional metrics summary.
     *
     * @param conn database connection
     * @return list of rows as attribute maps
     */
    public List<Map<String, Object>> getDepartmentSummaryReport(Connection conn) {
        String sql =
            "SELECT d.id AS department_id, d.code AS department_code, d.name AS department_name, d.active, " +
            "       COALESCE(stu.student_count, 0) AS total_active_students, " +
            "       COALESCE(fac.faculty_count, 0) AS total_active_faculty, " +
            "       COALESCE(crs.course_count, 0) AS total_active_courses " +
            "FROM departments d " +
            "LEFT JOIN (SELECT department_id, COUNT(*) AS student_count FROM students WHERE active = 1 GROUP BY department_id) stu ON d.id = stu.department_id " +
            "LEFT JOIN (SELECT department_id, COUNT(*) AS faculty_count FROM faculty WHERE active = 1 GROUP BY department_id) fac ON d.id = fac.department_id " +
            "LEFT JOIN (SELECT department_id, COUNT(*) AS course_count FROM courses WHERE active = 1 GROUP BY department_id) crs ON d.id = crs.department_id " +
            "ORDER BY d.code";

        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            return extractRows(rs);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to generate department summary report: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to generate department summary report using a managed connection.
     */
    public List<Map<String, Object>> getDepartmentSummaryReport() {
        try (Connection conn = DBConnection.getConnection()) {
            return getDepartmentSummaryReport(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to map a ResultSet to a list of LinkedHashMaps preserving column order.
     */
    private List<Map<String, Object>> extractRows(ResultSet rs) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();

        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= colCount; i++) {
                String colName = meta.getColumnLabel(i);
                Object val = rs.getObject(i);
                row.put(colName, val);
            }
            rows.add(row);
        }
        return rows;
    }
}

