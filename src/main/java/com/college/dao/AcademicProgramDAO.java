package com.college.dao;

import com.college.config.DBConnection;
import com.college.exception.DataAccessException;
import com.college.model.AcademicProgram;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the {@code academic_programs} table.
 * Supports CRUD operations, department/level filtering, active toggling,
 * and joined metadata lookups (department name, code, school).
 */
public class AcademicProgramDAO {

    public AcademicProgram insert(Connection conn, AcademicProgram program) {
        String sql = "INSERT INTO academic_programs (department_id, name, code, degree, level, " +
                     "duration_years, total_semesters, description, credits_required, academic_regulation, " +
                     "is_research, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, program.getDepartmentId());
            ps.setString(2, program.getName());
            ps.setString(3, program.getCode());
            ps.setString(4, program.getDegree());
            ps.setString(5, program.getLevel());
            ps.setInt(6, program.getDurationYears());
            ps.setInt(7, program.getTotalSemesters());
            ps.setString(8, program.getDescription());
            ps.setInt(9, program.getCreditsRequired());
            ps.setString(10, program.getAcademicRegulation() != null ? program.getAcademicRegulation() : "R2026");
            ps.setInt(11, program.isResearch() ? 1 : 0);
            ps.setInt(12, program.isActive() ? 1 : 0);

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    program.setId(rs.getInt(1));
                }
            }
            return program;
        } catch (SQLException e) {
            // Fallback for legacy schema table definition if extended columns are absent
            String sqlLegacy = "INSERT INTO academic_programs (department_id, name, code, degree, level, " +
                               "duration_years, total_semesters, is_research, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sqlLegacy, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, program.getDepartmentId());
                ps.setString(2, program.getName());
                ps.setString(3, program.getCode());
                ps.setString(4, program.getDegree());
                ps.setString(5, program.getLevel());
                ps.setInt(6, program.getDurationYears());
                ps.setInt(7, program.getTotalSemesters());
                ps.setInt(8, program.isResearch() ? 1 : 0);
                ps.setInt(9, program.isActive() ? 1 : 0);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        program.setId(rs.getInt(1));
                    }
                }
                return program;
            } catch (SQLException ex) {
                throw new DataAccessException("Failed to insert academic program '" + program.getCode() + "': " + ex.getMessage(), ex);
            }
        }
    }

    public AcademicProgram insert(AcademicProgram program) {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, program);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public boolean update(Connection conn, AcademicProgram program) {
        String sql = "UPDATE academic_programs SET department_id = ?, name = ?, code = ?, degree = ?, level = ?, " +
                     "duration_years = ?, total_semesters = ?, description = ?, credits_required = ?, " +
                     "academic_regulation = ?, is_research = ?, active = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, program.getDepartmentId());
            ps.setString(2, program.getName());
            ps.setString(3, program.getCode());
            ps.setString(4, program.getDegree());
            ps.setString(5, program.getLevel());
            ps.setInt(6, program.getDurationYears());
            ps.setInt(7, program.getTotalSemesters());
            ps.setString(8, program.getDescription());
            ps.setInt(9, program.getCreditsRequired());
            ps.setString(10, program.getAcademicRegulation() != null ? program.getAcademicRegulation() : "R2026");
            ps.setInt(11, program.isResearch() ? 1 : 0);
            ps.setInt(12, program.isActive() ? 1 : 0);
            ps.setInt(13, program.getId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            String sqlLegacy = "UPDATE academic_programs SET department_id = ?, name = ?, code = ?, degree = ?, level = ?, " +
                               "duration_years = ?, total_semesters = ?, is_research = ?, active = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlLegacy)) {
                ps.setInt(1, program.getDepartmentId());
                ps.setString(2, program.getName());
                ps.setString(3, program.getCode());
                ps.setString(4, program.getDegree());
                ps.setString(5, program.getLevel());
                ps.setInt(6, program.getDurationYears());
                ps.setInt(7, program.getTotalSemesters());
                ps.setInt(8, program.isResearch() ? 1 : 0);
                ps.setInt(9, program.isActive() ? 1 : 0);
                ps.setInt(10, program.getId());
                return ps.executeUpdate() > 0;
            } catch (SQLException ex) {
                throw new DataAccessException("Failed to update academic program ID " + program.getId() + ": " + ex.getMessage(), ex);
            }
        }
    }

    public boolean update(AcademicProgram program) {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, program);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public boolean setActive(Connection conn, int id, boolean active) {
        String sql = "UPDATE academic_programs SET active = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to set active status for program ID " + id + ": " + e.getMessage(), e);
        }
    }

    public boolean setActive(int id, boolean active) {
        try (Connection conn = DBConnection.getConnection()) {
            return setActive(conn, id, active);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public AcademicProgram findById(Connection conn, int id) {
        String sql = "SELECT p.*, d.name AS dept_name, d.code AS dept_code, d.school AS dept_school " +
                     "FROM academic_programs p " +
                     "JOIN departments d ON p.department_id = d.id " +
                     "WHERE p.id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find academic program by ID " + id + ": " + e.getMessage(), e);
        }
    }

    public AcademicProgram findById(int id) {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, id);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public AcademicProgram findByCode(Connection conn, String code) {
        String sql = "SELECT p.*, d.name AS dept_name, d.code AS dept_code, d.school AS dept_school " +
                     "FROM academic_programs p " +
                     "JOIN departments d ON p.department_id = d.id " +
                     "WHERE p.code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find academic program by code '" + code + "': " + e.getMessage(), e);
        }
    }

    public AcademicProgram findByCode(String code) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByCode(conn, code);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public List<AcademicProgram> findAll(Connection conn) {
        String sql = "SELECT p.*, d.name AS dept_name, d.code AS dept_code, d.school AS dept_school " +
                     "FROM academic_programs p " +
                     "JOIN departments d ON p.department_id = d.id " +
                     "ORDER BY d.name, p.level, p.code";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<AcademicProgram> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve academic programs: " + e.getMessage(), e);
        }
    }

    public List<AcademicProgram> findAll() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAll(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public List<AcademicProgram> findAllActive(Connection conn) {
        String sql = "SELECT p.*, d.name AS dept_name, d.code AS dept_code, d.school AS dept_school " +
                     "FROM academic_programs p " +
                     "JOIN departments d ON p.department_id = d.id " +
                     "WHERE p.active = 1 " +
                     "ORDER BY d.name, p.level, p.code";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<AcademicProgram> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve active academic programs: " + e.getMessage(), e);
        }
    }

    public List<AcademicProgram> findAllActive() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAllActive(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public List<AcademicProgram> findByDepartment(Connection conn, int departmentId) {
        String sql = "SELECT p.*, d.name AS dept_name, d.code AS dept_code, d.school AS dept_school " +
                     "FROM academic_programs p " +
                     "JOIN departments d ON p.department_id = d.id " +
                     "WHERE p.department_id = ? " +
                     "ORDER BY p.code";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, departmentId);
            try (ResultSet rs = ps.executeQuery()) {
                List<AcademicProgram> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve programs for department " + departmentId + ": " + e.getMessage(), e);
        }
    }

    public List<AcademicProgram> findByDepartment(int departmentId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByDepartment(conn, departmentId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public List<AcademicProgram> findByLevel(Connection conn, String level) {
        String sql = "SELECT p.*, d.name AS dept_name, d.code AS dept_code, d.school AS dept_school " +
                     "FROM academic_programs p " +
                     "JOIN departments d ON p.department_id = d.id " +
                     "WHERE p.level = ? " +
                     "ORDER BY d.name, p.code";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, level);
            try (ResultSet rs = ps.executeQuery()) {
                List<AcademicProgram> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve programs for level " + level + ": " + e.getMessage(), e);
        }
    }

    public List<AcademicProgram> findByLevel(String level) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByLevel(conn, level);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public int countEnrolledStudents(Connection conn, int programId) {
        String sql = "SELECT COUNT(*) FROM students WHERE program_id = ? AND active = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, programId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    public int countEnrolledStudents(int programId) {
        try (Connection conn = DBConnection.getConnection()) {
            return countEnrolledStudents(conn, programId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    private AcademicProgram mapRow(ResultSet rs) throws SQLException {
        AcademicProgram p = new AcademicProgram();
        p.setId(rs.getInt("id"));
        p.setDepartmentId(rs.getInt("department_id"));
        p.setName(rs.getString("name"));
        p.setCode(rs.getString("code"));
        p.setDegree(rs.getString("degree"));
        p.setLevel(rs.getString("level"));
        p.setDurationYears(rs.getInt("duration_years"));
        p.setTotalSemesters(rs.getInt("total_semesters"));
        p.setResearch(rs.getInt("is_research") == 1);
        p.setActive(rs.getInt("active") == 1);
        p.setCreatedAt(rs.getString("created_at"));

        try {
            p.setDescription(rs.getString("description"));
            p.setCreditsRequired(rs.getInt("credits_required"));
            p.setAcademicRegulation(rs.getString("academic_regulation"));
        } catch (SQLException ignored) {}

        try {
            p.setDepartmentName(rs.getString("dept_name"));
            p.setDepartmentCode(rs.getString("dept_code"));
            p.setSchoolName(rs.getString("dept_school"));
        } catch (SQLException ignored) {}

        return p;
    }
}

