package com.college.dao;

import com.college.config.DBConnection;
import com.college.exception.DataAccessException;
import com.college.model.ProgrammeSemester;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the {@code programme_semesters} table.
 */
public class ProgrammeSemesterDAO {

    public ProgrammeSemester insert(Connection conn, ProgrammeSemester sem) {
        String sql = "INSERT INTO programme_semesters (program_id, semester_number, semester_label, term_type, credits_target) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, sem.getProgramId());
            ps.setInt(2, sem.getSemesterNumber());
            ps.setString(3, sem.getSemesterLabel());
            ps.setString(4, sem.getTermType() != null ? sem.getTermType() : "SEMESTER");
            ps.setInt(5, sem.getCreditsTarget());

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    sem.setId(rs.getInt(1));
                }
            }
            return sem;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert programme semester: " + e.getMessage(), e);
        }
    }

    public void batchInsert(Connection conn, int programId, List<ProgrammeSemester> sems) {
        String deleteSql = "DELETE FROM programme_semesters WHERE program_id = ?";
        try (PreparedStatement delPs = conn.prepareStatement(deleteSql)) {
            delPs.setInt(1, programId);
            delPs.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to clear previous semesters for program " + programId + ": " + e.getMessage(), e);
        }

        String insertSql = "INSERT INTO programme_semesters (program_id, semester_number, semester_label, term_type, credits_target) " +
                           "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            for (ProgrammeSemester s : sems) {
                ps.setInt(1, programId);
                ps.setInt(2, s.getSemesterNumber());
                ps.setString(3, s.getSemesterLabel());
                ps.setString(4, s.getTermType() != null ? s.getTermType() : "SEMESTER");
                ps.setInt(5, s.getCreditsTarget());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to batch insert semesters for program " + programId + ": " + e.getMessage(), e);
        }
    }

    public void batchInsert(int programId, List<ProgrammeSemester> sems) {
        try (Connection conn = DBConnection.getConnection()) {
            batchInsert(conn, programId, sems);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public List<ProgrammeSemester> findByProgramId(Connection conn, int programId) {
        String sql = "SELECT * FROM programme_semesters WHERE program_id = ? ORDER BY semester_number";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, programId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ProgrammeSemester> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find semesters for program " + programId + ": " + e.getMessage(), e);
        }
    }

    public List<ProgrammeSemester> findByProgramId(int programId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByProgramId(conn, programId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public boolean deleteByProgramId(Connection conn, int programId) {
        String sql = "DELETE FROM programme_semesters WHERE program_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, programId);
            return ps.executeUpdate() >= 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete semesters for program " + programId + ": " + e.getMessage(), e);
        }
    }

    public boolean deleteByProgramId(int programId) {
        try (Connection conn = DBConnection.getConnection()) {
            return deleteByProgramId(conn, programId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    private ProgrammeSemester mapRow(ResultSet rs) throws SQLException {
        ProgrammeSemester s = new ProgrammeSemester();
        s.setId(rs.getInt("id"));
        s.setProgramId(rs.getInt("program_id"));
        s.setSemesterNumber(rs.getInt("semester_number"));
        s.setSemesterLabel(rs.getString("semester_label"));
        s.setTermType(rs.getString("term_type"));
        s.setCreditsTarget(rs.getInt("credits_target"));
        s.setCreatedAt(rs.getString("created_at"));
        return s;
    }
}

