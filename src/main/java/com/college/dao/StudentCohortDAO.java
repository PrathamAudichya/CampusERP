package com.college.dao;

import com.college.config.DBConnection;
import com.college.exception.DataAccessException;
import com.college.model.StudentCohort;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the {@code student_cohorts} table.
 */
public class StudentCohortDAO {

    public StudentCohort insert(Connection conn, StudentCohort cohort) {
        String sql = "INSERT INTO student_cohorts (cohort_code, program_id, admission_year, " +
                     "expected_graduation_year, current_semester, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, cohort.getCohortCode());
            ps.setInt(2, cohort.getProgramId());
            ps.setString(3, cohort.getAdmissionYear());
            ps.setString(4, cohort.getExpectedGraduationYear());
            ps.setInt(5, cohort.getCurrentSemester());
            ps.setString(6, cohort.getStatus() != null ? cohort.getStatus() : "ACTIVE");

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    cohort.setId(rs.getInt(1));
                }
            }
            return cohort;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert student cohort '" + cohort.getCohortCode() + "': " + e.getMessage(), e);
        }
    }

    public StudentCohort insert(StudentCohort cohort) {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, cohort);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public boolean update(Connection conn, StudentCohort cohort) {
        String sql = "UPDATE student_cohorts SET cohort_code = ?, program_id = ?, admission_year = ?, " +
                     "expected_graduation_year = ?, current_semester = ?, status = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cohort.getCohortCode());
            ps.setInt(2, cohort.getProgramId());
            ps.setString(3, cohort.getAdmissionYear());
            ps.setString(4, cohort.getExpectedGraduationYear());
            ps.setInt(5, cohort.getCurrentSemester());
            ps.setString(6, cohort.getStatus() != null ? cohort.getStatus() : "ACTIVE");
            ps.setInt(7, cohort.getId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update student cohort ID " + cohort.getId() + ": " + e.getMessage(), e);
        }
    }

    public boolean update(StudentCohort cohort) {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, cohort);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public StudentCohort findById(Connection conn, int id) {
        String sql = "SELECT sc.*, ap.name AS program_name, ap.code AS program_code, ap.degree " +
                     "FROM student_cohorts sc " +
                     "JOIN academic_programs ap ON sc.program_id = ap.id " +
                     "WHERE sc.id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    StudentCohort c = mapRow(rs);
                    c.setStudentCount(countStudentsInCohort(conn, id));
                    return c;
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find cohort by ID " + id + ": " + e.getMessage(), e);
        }
    }

    public StudentCohort findById(int id) {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, id);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public StudentCohort findByCode(Connection conn, String code) {
        String sql = "SELECT sc.*, ap.name AS program_name, ap.code AS program_code, ap.degree " +
                     "FROM student_cohorts sc " +
                     "JOIN academic_programs ap ON sc.program_id = ap.id " +
                     "WHERE sc.cohort_code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    StudentCohort c = mapRow(rs);
                    c.setStudentCount(countStudentsInCohort(conn, c.getId()));
                    return c;
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find cohort by code '" + code + "': " + e.getMessage(), e);
        }
    }

    public StudentCohort findByCode(String code) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByCode(conn, code);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public List<StudentCohort> findByProgramId(Connection conn, int programId) {
        String sql = "SELECT sc.*, ap.name AS program_name, ap.code AS program_code, ap.degree " +
                     "FROM student_cohorts sc " +
                     "JOIN academic_programs ap ON sc.program_id = ap.id " +
                     "WHERE sc.program_id = ? " +
                     "ORDER BY sc.admission_year DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, programId);
            try (ResultSet rs = ps.executeQuery()) {
                List<StudentCohort> list = new ArrayList<>();
                while (rs.next()) {
                    StudentCohort c = mapRow(rs);
                    c.setStudentCount(countStudentsInCohort(conn, c.getId()));
                    list.add(c);
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find cohorts for program " + programId + ": " + e.getMessage(), e);
        }
    }

    public List<StudentCohort> findByProgramId(int programId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByProgramId(conn, programId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public List<StudentCohort> findAllActive(Connection conn) {
        String sql = "SELECT sc.*, ap.name AS program_name, ap.code AS program_code, ap.degree " +
                     "FROM student_cohorts sc " +
                     "JOIN academic_programs ap ON sc.program_id = ap.id " +
                     "WHERE sc.status = 'ACTIVE' " +
                     "ORDER BY ap.code, sc.admission_year DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<StudentCohort> list = new ArrayList<>();
            while (rs.next()) {
                StudentCohort c = mapRow(rs);
                c.setStudentCount(countStudentsInCohort(conn, c.getId()));
                list.add(c);
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve active cohorts: " + e.getMessage(), e);
        }
    }

    public List<StudentCohort> findAllActive() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAllActive(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public List<StudentCohort> findAll(Connection conn) {
        String sql = "SELECT sc.*, ap.name AS program_name, ap.code AS program_code, ap.degree " +
                     "FROM student_cohorts sc " +
                     "JOIN academic_programs ap ON sc.program_id = ap.id " +
                     "ORDER BY ap.code, sc.admission_year DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<StudentCohort> list = new ArrayList<>();
            while (rs.next()) {
                StudentCohort c = mapRow(rs);
                c.setStudentCount(countStudentsInCohort(conn, c.getId()));
                list.add(c);
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve all cohorts: " + e.getMessage(), e);
        }
    }

    public List<StudentCohort> findAll() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAll(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public int countStudentsInCohort(Connection conn, int cohortId) {
        String sql = "SELECT COUNT(*) FROM students WHERE cohort_id = ? AND active = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cohortId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    private StudentCohort mapRow(ResultSet rs) throws SQLException {
        StudentCohort c = new StudentCohort();
        c.setId(rs.getInt("id"));
        c.setCohortCode(rs.getString("cohort_code"));
        c.setProgramId(rs.getInt("program_id"));
        c.setAdmissionYear(rs.getString("admission_year"));
        c.setExpectedGraduationYear(rs.getString("expected_graduation_year"));
        c.setCurrentSemester(rs.getInt("current_semester"));
        c.setStatus(rs.getString("status"));
        c.setCreatedAt(rs.getString("created_at"));

        try {
            c.setProgramName(rs.getString("program_name"));
            c.setProgramCode(rs.getString("program_code"));
            c.setDegree(rs.getString("degree"));
        } catch (SQLException ignored) {}

        return c;
    }
}

