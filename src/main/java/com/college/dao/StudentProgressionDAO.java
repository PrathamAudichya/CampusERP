package com.college.dao;

import com.college.config.DBConnection;
import com.college.exception.DataAccessException;
import com.college.model.StudentProgressionHistory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the {@code student_progression_history} table.
 */
public class StudentProgressionDAO {

    public StudentProgressionHistory insertHistory(Connection conn, StudentProgressionHistory history) {
        String sql = "INSERT INTO student_progression_history (student_id, previous_semester, new_semester, " +
                     "academic_year, action, performed_by, reason) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, history.getStudentId());
            ps.setInt(2, history.getPreviousSemester());
            ps.setInt(3, history.getNewSemester());
            ps.setString(4, history.getAcademicYear());
            ps.setString(5, history.getAction());
            ps.setInt(6, history.getPerformedBy());
            ps.setString(7, history.getReason());

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    history.setId(rs.getInt(1));
                }
            }
            return history;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to record progression history: " + e.getMessage(), e);
        }
    }

    public StudentProgressionHistory insertHistory(StudentProgressionHistory history) {
        try (Connection conn = DBConnection.getConnection()) {
            return insertHistory(conn, history);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public List<StudentProgressionHistory> findByStudentId(Connection conn, int studentId) {
        String sql = "SELECT sph.*, s.student_id AS reg_id, (s.first_name || ' ' || s.last_name) AS student_name, " +
                     "u.username AS performed_by_name " +
                     "FROM student_progression_history sph " +
                     "JOIN students s ON sph.student_id = s.id " +
                     "JOIN users u ON sph.performed_by = u.id " +
                     "WHERE sph.student_id = ? " +
                     "ORDER BY sph.created_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                List<StudentProgressionHistory> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find progression history for student " + studentId + ": " + e.getMessage(), e);
        }
    }

    public List<StudentProgressionHistory> findByStudentId(int studentId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByStudentId(conn, studentId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public List<StudentProgressionHistory> findAllHistory(Connection conn) {
        String sql = "SELECT sph.*, s.student_id AS reg_id, (s.first_name || ' ' || s.last_name) AS student_name, " +
                     "u.username AS performed_by_name " +
                     "FROM student_progression_history sph " +
                     "JOIN students s ON sph.student_id = s.id " +
                     "JOIN users u ON sph.performed_by = u.id " +
                     "ORDER BY sph.created_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<StudentProgressionHistory> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve progression history: " + e.getMessage(), e);
        }
    }

    public List<StudentProgressionHistory> findAllHistory() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAllHistory(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public boolean existsAction(Connection conn, int studentId, int previousSemester, int newSemester, String academicYear, String action) {
        String sql = "SELECT COUNT(*) FROM student_progression_history " +
                     "WHERE student_id = ? AND previous_semester = ? AND new_semester = ? AND academic_year = ? AND action = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, previousSemester);
            ps.setInt(3, newSemester);
            ps.setString(4, academicYear);
            ps.setString(5, action);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check existing progression action: " + e.getMessage(), e);
        }
    }

    public boolean existsAction(int studentId, int previousSemester, int newSemester, String academicYear, String action) {
        try (Connection conn = DBConnection.getConnection()) {
            return existsAction(conn, studentId, previousSemester, newSemester, academicYear, action);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    private StudentProgressionHistory mapRow(ResultSet rs) throws SQLException {
        StudentProgressionHistory h = new StudentProgressionHistory();
        h.setId(rs.getInt("id"));
        h.setStudentId(rs.getInt("student_id"));
        h.setPreviousSemester(rs.getInt("previous_semester"));
        h.setNewSemester(rs.getInt("new_semester"));
        h.setAcademicYear(rs.getString("academic_year"));
        h.setAction(rs.getString("action"));
        h.setPerformedBy(rs.getInt("performed_by"));
        h.setReason(rs.getString("reason"));
        h.setCreatedAt(rs.getString("created_at"));

        try {
            h.setStudentRegId(rs.getString("reg_id"));
            h.setStudentName(rs.getString("student_name"));
            h.setPerformedByName(rs.getString("performed_by_name"));
        } catch (SQLException ignored) {}

        return h;
    }
}

