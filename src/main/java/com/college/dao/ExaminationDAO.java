package com.college.dao;

import com.college.config.DBConnection;
import com.college.exception.DataAccessException;
import com.college.model.Examination;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the {@code examinations} table.
 *
 * <p>Handles examination schedule persistence, course joins for presentation,
 * lookups by course and academic year, and active status toggling.</p>
 */
public class ExaminationDAO {

    private static final String BASE_SELECT =
        "SELECT ex.id, ex.course_id, ex.exam_name, ex.exam_type, ex.exam_date, " +
        "       ex.total_marks, ex.academic_year, ex.active, ex.created_at, " +
        "       c.course_code, c.name AS course_name " +
        "FROM examinations ex " +
        "JOIN courses c ON ex.course_id = c.id ";

    /**
     * Inserts a new examination.
     *
     * @param conn database connection
     * @param exam examination entity to insert
     * @return persisted entity with generated ID
     * @throws DataAccessException if a database error occurs
     */
    public Examination insert(Connection conn, Examination exam) {
        String sql = "INSERT INTO examinations (course_id, exam_name, exam_type, exam_date, total_marks, academic_year, active) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, exam.getCourseId());
            pstmt.setString(2, exam.getExamName());
            pstmt.setString(3, exam.getExamType());
            pstmt.setString(4, exam.getExamDate());
            pstmt.setInt(5, exam.getTotalMarks());
            pstmt.setString(6, exam.getAcademicYear());
            pstmt.setInt(7, exam.isActive() ? 1 : 0);

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    exam.setId(rs.getInt(1));
                }
            }
            return exam;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert examination: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to insert an examination using a managed connection.
     *
     * @param exam examination entity to insert
     * @return persisted entity
     */
    public Examination insert(Examination exam) {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, exam);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds an examination by primary key ID, populating course transient details.
     *
     * @param conn database connection
     * @param id examination ID
     * @return examination entity or {@code null} if not found
     */
    public Examination findById(Connection conn, int id) {
        String sql = BASE_SELECT + "WHERE ex.id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find examination by ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find an examination by ID using a managed connection.
     *
     * @param id examination ID
     * @return examination entity or {@code null} if not found
     */
    public Examination findById(int id) {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, id);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all examinations for a course.
     *
     * @param conn database connection
     * @param courseId course ID
     * @return list of examinations
     */
    public List<Examination> findByCourse(Connection conn, int courseId) {
        String sql = BASE_SELECT + "WHERE ex.course_id = ? ORDER BY ex.exam_date DESC, ex.id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, courseId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Examination> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find examinations for course " + courseId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find examinations by course using a managed connection.
     *
     * @param courseId course ID
     * @return list of examinations
     */
    public List<Examination> findByCourse(int courseId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByCourse(conn, courseId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves examinations for a course in a specific academic year.
     *
     * @param conn database connection
     * @param courseId course ID
     * @param academicYear academic year
     * @return list of examinations
     */
    public List<Examination> findByCourseAndYear(Connection conn, int courseId, String academicYear) {
        String sql = BASE_SELECT + "WHERE ex.course_id = ? AND ex.academic_year = ? ORDER BY ex.exam_date DESC, ex.id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, courseId);
            pstmt.setString(2, academicYear);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Examination> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find examinations by course and year: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find examinations by course and year using a managed connection.
     *
     * @param courseId course ID
     * @param academicYear academic year
     * @return list of examinations
     */
    public List<Examination> findByCourseAndYear(int courseId, String academicYear) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByCourseAndYear(conn, courseId, academicYear);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all examinations.
     *
     * @param conn database connection
     * @return list of all examinations
     */
    public List<Examination> findAll(Connection conn) {
        String sql = BASE_SELECT + "ORDER BY ex.academic_year DESC, ex.exam_date DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<Examination> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve all examinations: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to retrieve all examinations using a managed connection.
     *
     * @return list of all examinations
     */
    public List<Examination> findAll() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAll(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all active examinations.
     *
     * @param conn database connection
     * @return list of active examinations
     */
    public List<Examination> findAllActive(Connection conn) {
        String sql = BASE_SELECT + "WHERE ex.active = 1 ORDER BY ex.academic_year DESC, ex.exam_date DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<Examination> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve active examinations: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to retrieve active examinations using a managed connection.
     *
     * @return list of active examinations
     */
    public List<Examination> findAllActive() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAllActive(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Updates an examination's details.
     *
     * @param conn database connection
     * @param exam examination entity
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean update(Connection conn, Examination exam) {
        String sql = "UPDATE examinations SET course_id = ?, exam_name = ?, exam_type = ?, " +
                     "                         exam_date = ?, total_marks = ?, academic_year = ?, active = ? " +
                     "WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, exam.getCourseId());
            pstmt.setString(2, exam.getExamName());
            pstmt.setString(3, exam.getExamType());
            pstmt.setString(4, exam.getExamDate());
            pstmt.setInt(5, exam.getTotalMarks());
            pstmt.setString(6, exam.getAcademicYear());
            pstmt.setInt(7, exam.isActive() ? 1 : 0);
            pstmt.setInt(8, exam.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update examination ID " + exam.getId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to update an examination using a managed connection.
     *
     * @param exam examination entity
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean update(Examination exam) {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, exam);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Sets the active status of an examination.
     *
     * @param conn database connection
     * @param id examination ID
     * @param active new active state
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean setActive(Connection conn, int id, boolean active) {
        String sql = "UPDATE examinations SET active = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, active ? 1 : 0);
            pstmt.setInt(2, id);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to set active status for examination ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to set examination active status using a managed connection.
     *
     * @param id examination ID
     * @param active new active state
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean setActive(int id, boolean active) {
        try (Connection conn = DBConnection.getConnection()) {
            return setActive(conn, id, active);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Maps the current row of a ResultSet to an Examination domain model.
     *
     * @param rs result set positioned at a valid row
     * @return populated Examination model
     * @throws SQLException if column reading fails
     */
    private Examination mapRow(ResultSet rs) throws SQLException {
        Examination ex = new Examination();
        ex.setId(rs.getInt("id"));
        ex.setCourseId(rs.getInt("course_id"));
        ex.setExamName(rs.getString("exam_name"));
        ex.setExamType(rs.getString("exam_type"));
        ex.setExamDate(rs.getString("exam_date"));
        ex.setTotalMarks(rs.getInt("total_marks"));
        ex.setAcademicYear(rs.getString("academic_year"));
        ex.setActive(rs.getInt("active") == 1);
        ex.setCreatedAt(rs.getString("created_at"));
        ex.setCourseCode(rs.getString("course_code"));
        ex.setCourseName(rs.getString("course_name"));
        return ex;
    }
}

