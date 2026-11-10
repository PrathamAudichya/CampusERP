package com.college.dao;

import com.college.config.DBConnection;
import com.college.exception.DataAccessException;
import com.college.model.Mark;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the {@code marks} table.
 *
 * <p>Handles evaluation scores, letter grades, atomic UPSERTs on (enrollment_id, examination_id),
 * and presentation JOIN queries connecting enrollments, students, and examinations.</p>
 */
public class MarkDAO {

    private static final String BASE_SELECT =
        "SELECT m.id, m.enrollment_id, m.examination_id, m.marks_obtained, m.grade, m.remarks, " +
        "       m.created_at, m.updated_at, " +
        "       (s.first_name || ' ' || s.last_name) AS student_name, s.student_id AS student_id_number, " +
        "       ex.exam_name, ex.total_marks " +
        "FROM marks m " +
        "JOIN enrollments e ON m.enrollment_id = e.id " +
        "JOIN students s ON e.student_id = s.id " +
        "JOIN examinations ex ON m.examination_id = ex.id ";

    /**
     * Inserts a new mark record.
     *
     * @param conn database connection
     * @param mark mark entity to insert
     * @return persisted entity with generated ID
     * @throws DataAccessException if a database error occurs
     */
    public Mark insert(Connection conn, Mark mark) {
        String sql = "INSERT INTO marks (enrollment_id, examination_id, marks_obtained, grade, remarks) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, mark.getEnrollmentId());
            pstmt.setInt(2, mark.getExaminationId());
            pstmt.setDouble(3, mark.getMarksObtained());
            pstmt.setString(4, mark.getGrade());
            pstmt.setString(5, mark.getRemarks());

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    mark.setId(rs.getInt(1));
                }
            }
            return mark;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert mark: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to insert a mark using a managed connection.
     *
     * @param mark mark entity to insert
     * @return persisted entity
     */
    public Mark insert(Mark mark) {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, mark);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Atomically inserts or updates a mark record on conflict of (enrollment_id, examination_id).
     *
     * @param conn database connection
     * @param mark mark entity
     * @return persisted/updated Mark entity
     * @throws DataAccessException if a database error occurs
     */
    public Mark upsert(Connection conn, Mark mark) {
        String sql = "INSERT INTO marks (enrollment_id, examination_id, marks_obtained, grade, remarks) " +
                     "VALUES (?, ?, ?, ?, ?) " +
                     "ON CONFLICT(enrollment_id, examination_id) " +
                     "DO UPDATE SET marks_obtained = excluded.marks_obtained, " +
                     "              grade = excluded.grade, " +
                     "              remarks = excluded.remarks, " +
                     "              updated_at = CURRENT_TIMESTAMP";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, mark.getEnrollmentId());
            pstmt.setInt(2, mark.getExaminationId());
            pstmt.setDouble(3, mark.getMarksObtained());
            pstmt.setString(4, mark.getGrade());
            pstmt.setString(5, mark.getRemarks());

            pstmt.executeUpdate();

            return findByEnrollmentAndExamination(conn, mark.getEnrollmentId(), mark.getExaminationId());
        } catch (SQLException e) {
            throw new DataAccessException("Failed to upsert mark: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to upsert a mark using a managed connection.
     *
     * @param mark mark entity
     * @return persisted/updated entity
     */
    public Mark upsert(Mark mark) {
        try (Connection conn = DBConnection.getConnection()) {
            return upsert(conn, mark);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a mark by primary key ID.
     *
     * @param conn database connection
     * @param id mark ID
     * @return mark entity or {@code null} if not found
     */
    public Mark findById(Connection conn, int id) {
        String sql = BASE_SELECT + "WHERE m.id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find mark by ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find a mark by ID using a managed connection.
     *
     * @param id mark ID
     * @return mark entity or {@code null} if not found
     */
    public Mark findById(int id) {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, id);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a mark by unique key (enrollment_id, examination_id).
     *
     * @param conn database connection
     * @param enrollmentId enrollment ID
     * @param examinationId examination ID
     * @return mark entity or {@code null} if not found
     */
    public Mark findByEnrollmentAndExamination(Connection conn, int enrollmentId, int examinationId) {
        String sql = BASE_SELECT + "WHERE m.enrollment_id = ? AND m.examination_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, enrollmentId);
            pstmt.setInt(2, examinationId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find mark: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find a mark by unique key using a managed connection.
     *
     * @param enrollmentId enrollment ID
     * @param examinationId examination ID
     * @return mark entity or {@code null} if not found
     */
    public Mark findByEnrollmentAndExamination(int enrollmentId, int examinationId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByEnrollmentAndExamination(conn, enrollmentId, examinationId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all marks recorded for an enrollment.
     *
     * @param conn database connection
     * @param enrollmentId enrollment ID
     * @return list of marks
     */
    public List<Mark> findByEnrollment(Connection conn, int enrollmentId) {
        String sql = BASE_SELECT + "WHERE m.enrollment_id = ? ORDER BY ex.exam_date DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, enrollmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Mark> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find marks for enrollment " + enrollmentId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find marks by enrollment using a managed connection.
     *
     * @param enrollmentId enrollment ID
     * @return list of marks
     */
    public List<Mark> findByEnrollment(int enrollmentId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByEnrollment(conn, enrollmentId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all marks recorded for an examination.
     *
     * @param conn database connection
     * @param examinationId examination ID
     * @return list of marks
     */
    public List<Mark> findByExamination(Connection conn, int examinationId) {
        String sql = BASE_SELECT + "WHERE m.examination_id = ? ORDER BY s.student_id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, examinationId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Mark> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find marks for examination " + examinationId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find marks by examination using a managed connection.
     *
     * @param examinationId examination ID
     * @return list of marks
     */
    public List<Mark> findByExamination(int examinationId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByExamination(conn, examinationId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all marks for a student in a specific academic year.
     *
     * @param conn database connection
     * @param studentId student ID
     * @param academicYear academic year
     * @return list of marks
     */
    public List<Mark> findByStudentAndYear(Connection conn, int studentId, String academicYear) {
        String sql = BASE_SELECT + "WHERE e.student_id = ? AND e.academic_year = ? ORDER BY ex.exam_date DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, studentId);
            pstmt.setString(2, academicYear);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Mark> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find marks for student in year " + academicYear + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find marks by student and year using a managed connection.
     *
     * @param studentId student ID
     * @param academicYear academic year
     * @return list of marks
     */
    public List<Mark> findByStudentAndYear(int studentId, String academicYear) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByStudentAndYear(conn, studentId, academicYear);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Updates an existing mark.
     * Automatically sets {@code updated_at = CURRENT_TIMESTAMP}.
     *
     * @param conn database connection
     * @param mark mark entity
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean update(Connection conn, Mark mark) {
        String sql = "UPDATE marks SET marks_obtained = ?, grade = ?, remarks = ?, " +
                     "                 updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, mark.getMarksObtained());
            pstmt.setString(2, mark.getGrade());
            pstmt.setString(3, mark.getRemarks());
            pstmt.setInt(4, mark.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update mark ID " + mark.getId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to update a mark using a managed connection.
     *
     * @param mark mark entity
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean update(Mark mark) {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, mark);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Maps the current row of a ResultSet to a Mark domain model.
     *
     * @param rs result set positioned at a valid row
     * @return populated Mark model
     * @throws SQLException if column reading fails
     */
    private Mark mapRow(ResultSet rs) throws SQLException {
        Mark m = new Mark();
        m.setId(rs.getInt("id"));
        m.setEnrollmentId(rs.getInt("enrollment_id"));
        m.setExaminationId(rs.getInt("examination_id"));
        m.setMarksObtained(rs.getDouble("marks_obtained"));
        m.setGrade(rs.getString("grade"));
        m.setRemarks(rs.getString("remarks"));
        m.setCreatedAt(rs.getString("created_at"));
        m.setUpdatedAt(rs.getString("updated_at"));
        m.setStudentName(rs.getString("student_name"));
        m.setStudentIdNumber(rs.getString("student_id_number"));
        m.setExamName(rs.getString("exam_name"));
        Object totalMarks = rs.getObject("total_marks");
        m.setTotalMarks(totalMarks != null ? rs.getInt("total_marks") : null);
        return m;
    }
}

