package com.college.service;

import com.college.config.DBConnection;
import com.college.dto.AcademicOverviewDTO;
import com.college.exception.DataAccessException;
import com.college.exception.ValidationException;
import com.college.model.Role;
import com.college.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service aggregating live academic hierarchy statistics, programme enrollments,
 * cohort distributions, and CSV reporting.
 */
public class AcademicReportService {

    public AcademicOverviewDTO getAcademicOverview(User currentUser, String filterSchool, Integer filterDeptId, Integer filterProgId) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY);

        AcademicOverviewDTO dto = new AcademicOverviewDTO();

        try (Connection conn = DBConnection.getConnection()) {
            // 1. Total Schools
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(DISTINCT school) FROM departments WHERE school IS NOT NULL AND TRIM(school) != ''")) {
                if (rs.next()) dto.setTotalSchools(rs.getInt(1));
            } catch (SQLException ignored) {
                dto.setTotalSchools(1);
            }

            // 2. Department counts
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*), SUM(CASE WHEN active = 1 THEN 1 ELSE 0 END) FROM departments")) {
                if (rs.next()) {
                    dto.setTotalDepartments(rs.getInt(1));
                    dto.setActiveDepartments(rs.getInt(2));
                }
            }

            // 3. Programme counts
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*), SUM(CASE WHEN active = 1 THEN 1 ELSE 0 END) FROM academic_programs")) {
                if (rs.next()) {
                    dto.setTotalPrograms(rs.getInt(1));
                    dto.setActivePrograms(rs.getInt(2));
                }
            }

            // 4. Total Students
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM students WHERE active = 1")) {
                if (rs.next()) {
                    dto.setTotalStudents(rs.getInt(1));
                }
            }

            // 5. Programme-wise Student Distribution
            String progSql = "SELECT COALESCE(ap.code, 'UNASSIGNED') AS prog_code, " +
                             "COALESCE(ap.name, 'No Programme Assigned') AS prog_name, " +
                             "COUNT(s.id) AS student_count " +
                             "FROM students s " +
                             "LEFT JOIN academic_programs ap ON s.program_id = ap.id " +
                             "LEFT JOIN departments d ON s.department_id = d.id " +
                             "WHERE s.active = 1 " +
                             (filterSchool != null && !filterSchool.isEmpty() && !"ALL".equalsIgnoreCase(filterSchool) ? "AND d.school = ? " : "") +
                             (filterDeptId != null && filterDeptId > 0 ? "AND s.department_id = ? " : "") +
                             (filterProgId != null && filterProgId > 0 ? "AND s.program_id = ? " : "") +
                             "GROUP BY ap.id, ap.code, ap.name " +
                             "ORDER BY student_count DESC";

            try (PreparedStatement ps = conn.prepareStatement(progSql)) {
                int idx = 1;
                if (filterSchool != null && !filterSchool.isEmpty() && !"ALL".equalsIgnoreCase(filterSchool)) {
                    ps.setString(idx++, filterSchool);
                }
                if (filterDeptId != null && filterDeptId > 0) {
                    ps.setInt(idx++, filterDeptId);
                }
                if (filterProgId != null && filterProgId > 0) {
                    ps.setInt(idx++, filterProgId);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    Map<String, Integer> progMap = new LinkedHashMap<>();
                    while (rs.next()) {
                        String label = rs.getString("prog_code") + " (" + rs.getString("prog_name") + ")";
                        progMap.put(label, rs.getInt("student_count"));
                    }
                    dto.setProgramStudentDistribution(progMap);
                }
            }

            // 6. Semester-wise Student Distribution
            String semSql = "SELECT s.semester, COUNT(s.id) AS student_count " +
                            "FROM students s " +
                            "LEFT JOIN departments d ON s.department_id = d.id " +
                            "WHERE s.active = 1 " +
                            (filterSchool != null && !filterSchool.isEmpty() && !"ALL".equalsIgnoreCase(filterSchool) ? "AND d.school = ? " : "") +
                            (filterDeptId != null && filterDeptId > 0 ? "AND s.department_id = ? " : "") +
                            (filterProgId != null && filterProgId > 0 ? "AND s.program_id = ? " : "") +
                            "GROUP BY s.semester " +
                            "ORDER BY CAST(s.semester AS INTEGER)";

            try (PreparedStatement ps = conn.prepareStatement(semSql)) {
                int idx = 1;
                if (filterSchool != null && !filterSchool.isEmpty() && !"ALL".equalsIgnoreCase(filterSchool)) {
                    ps.setString(idx++, filterSchool);
                }
                if (filterDeptId != null && filterDeptId > 0) {
                    ps.setInt(idx++, filterDeptId);
                }
                if (filterProgId != null && filterProgId > 0) {
                    ps.setInt(idx++, filterProgId);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    Map<String, Integer> semMap = new LinkedHashMap<>();
                    while (rs.next()) {
                        semMap.put("Semester " + rs.getString("semester"), rs.getInt("student_count"));
                    }
                    dto.setSemesterStudentDistribution(semMap);
                }
            }

            // 7. Department-wise Faculty Distribution
            String facSql = "SELECT d.code, d.name, COUNT(f.id) AS faculty_count " +
                            "FROM departments d " +
                            "LEFT JOIN faculty f ON f.department_id = d.id AND f.active = 1 " +
                            "WHERE d.active = 1 " +
                            (filterSchool != null && !filterSchool.isEmpty() && !"ALL".equalsIgnoreCase(filterSchool) ? "AND d.school = ? " : "") +
                            (filterDeptId != null && filterDeptId > 0 ? "AND d.id = ? " : "") +
                            "GROUP BY d.id, d.code, d.name " +
                            "ORDER BY faculty_count DESC";

            try (PreparedStatement ps = conn.prepareStatement(facSql)) {
                int idx = 1;
                if (filterSchool != null && !filterSchool.isEmpty() && !"ALL".equalsIgnoreCase(filterSchool)) {
                    ps.setString(idx++, filterSchool);
                }
                if (filterDeptId != null && filterDeptId > 0) {
                    ps.setInt(idx++, filterDeptId);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    Map<String, Integer> deptMap = new LinkedHashMap<>();
                    while (rs.next()) {
                        deptMap.put(rs.getString("code") + " - " + rs.getString("name"), rs.getInt("faculty_count"));
                    }
                    dto.setDepartmentFacultyDistribution(deptMap);
                }
            }

            // 8. Cohorts Overview
            String cohortSql = "SELECT sc.cohort_code, COUNT(s.id) AS student_count " +
                               "FROM student_cohorts sc " +
                               "LEFT JOIN students s ON s.cohort_id = sc.id AND s.active = 1 " +
                               "WHERE sc.status = 'ACTIVE' " +
                               "GROUP BY sc.id, sc.cohort_code " +
                               "ORDER BY sc.cohort_code";

            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery(cohortSql)) {
                Map<String, Integer> cohortMap = new LinkedHashMap<>();
                while (rs.next()) {
                    cohortMap.put(rs.getString("cohort_code"), rs.getInt("student_count"));
                }
                dto.setCohortStudentDistribution(cohortMap);
            }

        } catch (SQLException e) {
            throw new DataAccessException("Failed to query academic overview statistics: " + e.getMessage(), e);
        }

        return dto;
    }

    /**
     * Exports the Academic Overview summary into an RFC 4180 compliant CSV string.
     */
    public String exportAcademicOverviewCsv(User currentUser, String filterSchool, Integer filterDeptId, Integer filterProgId) {
        AcademicOverviewDTO dto = getAcademicOverview(currentUser, filterSchool, filterDeptId, filterProgId);

        StringBuilder sb = new StringBuilder();
        sb.append("ACADEMIC STRUCTURE OVERVIEW REPORT\n");
        sb.append("Total Schools,").append(dto.getTotalSchools()).append("\n");
        sb.append("Total Departments,").append(dto.getTotalDepartments()).append("\n");
        sb.append("Active Departments,").append(dto.getActiveDepartments()).append("\n");
        sb.append("Total Programmes,").append(dto.getTotalPrograms()).append("\n");
        sb.append("Active Programmes,").append(dto.getActivePrograms()).append("\n");
        sb.append("Total Enrolled Students,").append(dto.getTotalStudents()).append("\n\n");

        sb.append("PROGRAMME-WISE STUDENT DISTRIBUTION\n");
        sb.append("Programme,Enrolled Students\n");
        for (Map.Entry<String, Integer> entry : dto.getProgramStudentDistribution().entrySet()) {
            sb.append(escapeCsv(entry.getKey())).append(",").append(entry.getValue()).append("\n");
        }
        sb.append("\n");

        sb.append("SEMESTER-WISE STUDENT DISTRIBUTION\n");
        sb.append("Semester,Enrolled Students\n");
        for (Map.Entry<String, Integer> entry : dto.getSemesterStudentDistribution().entrySet()) {
            sb.append(escapeCsv(entry.getKey())).append(",").append(entry.getValue()).append("\n");
        }
        sb.append("\n");

        sb.append("DEPARTMENT-WISE FACULTY DISTRIBUTION\n");
        sb.append("Department,Faculty Count\n");
        for (Map.Entry<String, Integer> entry : dto.getDepartmentFacultyDistribution().entrySet()) {
            sb.append(escapeCsv(entry.getKey())).append(",").append(entry.getValue()).append("\n");
        }
        sb.append("\n");

        sb.append("COHORT-WISE STUDENT DISTRIBUTION\n");
        sb.append("Cohort,Enrolled Students\n");
        for (Map.Entry<String, Integer> entry : dto.getCohortStudentDistribution().entrySet()) {
            sb.append(escapeCsv(entry.getKey())).append(",").append(entry.getValue()).append("\n");
        }

        return sb.toString();
    }

    private String escapeCsv(String val) {
        if (val == null) return "\"\"";
        return "\"" + val.replace("\"", "\"\"") + "\"";
    }
}

