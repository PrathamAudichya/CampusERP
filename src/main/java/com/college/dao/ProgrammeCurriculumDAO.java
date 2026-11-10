package com.college.dao;

import com.college.config.DBConnection;
import com.college.exception.DataAccessException;
import com.college.model.ProgrammeCurriculum;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Data Access Object for the {@code programme_curriculum} table.
 */
public class ProgrammeCurriculumDAO {

    public ProgrammeCurriculum insert(Connection conn, ProgrammeCurriculum curr) {
        String sql = "INSERT INTO programme_curriculum (program_id, semester_number, course_id, " +
                     "course_category, is_mandatory, academic_regulation, prerequisite_course_id, active) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, curr.getProgramId());
            ps.setInt(2, curr.getSemesterNumber());
            ps.setInt(3, curr.getCourseId());
            ps.setString(4, curr.getCourseCategory() != null ? curr.getCourseCategory() : "CORE");
            ps.setInt(5, curr.isMandatory() ? 1 : 0);
            ps.setString(6, curr.getAcademicRegulation() != null ? curr.getAcademicRegulation() : "R2026");
            if (curr.getPrerequisiteCourseId() != null && curr.getPrerequisiteCourseId() > 0) {
                ps.setInt(7, curr.getPrerequisiteCourseId());
            } else {
                ps.setNull(7, java.sql.Types.INTEGER);
            }
            ps.setInt(8, curr.isActive() ? 1 : 0);

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    curr.setId(rs.getInt(1));
                }
            }
            return curr;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert curriculum mapping: " + e.getMessage(), e);
        }
    }

    public ProgrammeCurriculum insert(ProgrammeCurriculum curr) {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, curr);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public boolean update(Connection conn, ProgrammeCurriculum curr) {
        String sql = "UPDATE programme_curriculum SET semester_number = ?, course_category = ?, " +
                     "is_mandatory = ?, academic_regulation = ?, prerequisite_course_id = ?, active = ? " +
                     "WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, curr.getSemesterNumber());
            ps.setString(2, curr.getCourseCategory() != null ? curr.getCourseCategory() : "CORE");
            ps.setInt(3, curr.isMandatory() ? 1 : 0);
            ps.setString(4, curr.getAcademicRegulation() != null ? curr.getAcademicRegulation() : "R2026");
            if (curr.getPrerequisiteCourseId() != null && curr.getPrerequisiteCourseId() > 0) {
                ps.setInt(5, curr.getPrerequisiteCourseId());
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            ps.setInt(6, curr.isActive() ? 1 : 0);
            ps.setInt(7, curr.getId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update curriculum mapping ID " + curr.getId() + ": " + e.getMessage(), e);
        }
    }

    public boolean update(ProgrammeCurriculum curr) {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, curr);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public boolean delete(Connection conn, int id) {
        String sql = "DELETE FROM programme_curriculum WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete curriculum mapping ID " + id + ": " + e.getMessage(), e);
        }
    }

    public boolean delete(int id) {
        try (Connection conn = DBConnection.getConnection()) {
            return delete(conn, id);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public ProgrammeCurriculum findById(Connection conn, int id) {
        String sql = "SELECT pc.*, c.course_code, c.name AS course_name, c.credits, " +
                     "p.course_code AS prereq_code, p.name AS prereq_name " +
                     "FROM programme_curriculum pc " +
                     "JOIN courses c ON pc.course_id = c.id " +
                     "LEFT JOIN courses p ON pc.prerequisite_course_id = p.id " +
                     "WHERE pc.id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find curriculum mapping by ID " + id + ": " + e.getMessage(), e);
        }
    }

    public ProgrammeCurriculum findById(int id) {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, id);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public List<ProgrammeCurriculum> findByProgramAndSemester(Connection conn, int programId, int semesterNumber) {
        String sql = "SELECT pc.*, c.course_code, c.name AS course_name, c.credits, " +
                     "p.course_code AS prereq_code, p.name AS prereq_name " +
                     "FROM programme_curriculum pc " +
                     "JOIN courses c ON pc.course_id = c.id " +
                     "LEFT JOIN courses p ON pc.prerequisite_course_id = p.id " +
                     "WHERE pc.program_id = ? AND pc.semester_number = ? AND pc.active = 1 " +
                     "ORDER BY pc.course_category, c.course_code";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, programId);
            ps.setInt(2, semesterNumber);
            try (ResultSet rs = ps.executeQuery()) {
                List<ProgrammeCurriculum> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve curriculum for program " + programId + " sem " + semesterNumber + ": " + e.getMessage(), e);
        }
    }

    public List<ProgrammeCurriculum> findByProgramAndSemester(int programId, int semesterNumber) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByProgramAndSemester(conn, programId, semesterNumber);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public List<ProgrammeCurriculum> findByProgramId(Connection conn, int programId) {
        String sql = "SELECT pc.*, c.course_code, c.name AS course_name, c.credits, " +
                     "p.course_code AS prereq_code, p.name AS prereq_name " +
                     "FROM programme_curriculum pc " +
                     "JOIN courses c ON pc.course_id = c.id " +
                     "LEFT JOIN courses p ON pc.prerequisite_course_id = p.id " +
                     "WHERE pc.program_id = ? " +
                     "ORDER BY pc.semester_number, pc.course_category, c.course_code";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, programId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ProgrammeCurriculum> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve curriculum for program " + programId + ": " + e.getMessage(), e);
        }
    }

    public List<ProgrammeCurriculum> findByProgramId(int programId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByProgramId(conn, programId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public boolean existsMapping(int programId, int semesterNumber, int courseId, String regulation) {
        String sql = "SELECT 1 FROM programme_curriculum WHERE program_id = ? AND semester_number = ? " +
                     "AND course_id = ? AND academic_regulation = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, programId);
            ps.setInt(2, semesterNumber);
            ps.setInt(3, courseId);
            ps.setString(4, regulation != null ? regulation : "R2026");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check curriculum mapping existence: " + e.getMessage(), e);
        }
    }

    /**
     * Traverses the prerequisite graph to detect if setting prerequisiteCourseId for courseId
     * creates a circular dependency (e.g. A requires B, and B requires A).
     */
    public boolean hasCircularPrerequisite(int courseId, int candidatePrereqId) {
        if (courseId == candidatePrereqId) {
            return true;
        }

        Set<Integer> visited = new HashSet<>();
        visited.add(courseId);

        int currentPrereq = candidatePrereqId;
        while (currentPrereq > 0) {
            if (visited.contains(currentPrereq)) {
                return true;
            }
            visited.add(currentPrereq);

            String sql = "SELECT prerequisite_course_id FROM programme_curriculum " +
                         "WHERE course_id = ? AND prerequisite_course_id IS NOT NULL";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, currentPrereq);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        currentPrereq = rs.getInt("prerequisite_course_id");
                    } else {
                        break;
                    }
                }
            } catch (SQLException e) {
                break;
            }
        }
        return false;
    }

    private ProgrammeCurriculum mapRow(ResultSet rs) throws SQLException {
        ProgrammeCurriculum pc = new ProgrammeCurriculum();
        pc.setId(rs.getInt("id"));
        pc.setProgramId(rs.getInt("program_id"));
        pc.setSemesterNumber(rs.getInt("semester_number"));
        pc.setCourseId(rs.getInt("course_id"));
        pc.setCourseCategory(rs.getString("course_category"));
        pc.setMandatory(rs.getInt("is_mandatory") == 1);
        pc.setAcademicRegulation(rs.getString("academic_regulation"));

        int prereqId = rs.getInt("prerequisite_course_id");
        if (!rs.wasNull() && prereqId > 0) {
            pc.setPrerequisiteCourseId(prereqId);
        }

        pc.setActive(rs.getInt("active") == 1);
        pc.setCreatedAt(rs.getString("created_at"));

        try {
            pc.setCourseCode(rs.getString("course_code"));
            pc.setCourseName(rs.getString("course_name"));
            pc.setCredits(rs.getInt("credits"));
            pc.setPrerequisiteCode(rs.getString("prereq_code"));
            pc.setPrerequisiteName(rs.getString("prereq_name"));
        } catch (SQLException ignored) {}

        return pc;
    }
}

