package com.college.dao;

import com.college.config.DBConnection;
import com.college.exception.DataAccessException;
import com.college.model.Enrollment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the {@code enrollments} table.
 *
 * <p>Handles course registration lifecycle (ACTIVE, DROPPED, COMPLETED), capacity counts,
 * student and course joins for transient display properties, and academic year filtering.</p>
 */
public class EnrollmentDAO {

    private static final String BASE_SELECT =
        "SELECT e.id, e.student_id, e.course_id, e.academic_year, e.enrolled_date, e.status, " +
        "       e.created_at, e.updated_at, " +
        "       (s.first_name || ' ' || s.last_name) AS student_name, s.student_id AS student_id_number, " +
        "       c.course_code, c.name AS course_name " +
        "FROM enrollments e " +
        "JOIN students s ON e.student_id = s.id " +
        "JOIN courses c ON e.course_id = c.id ";

    /**
     * Inserts a new enrollment record.
     *
     * @param conn database connection
     * @param enrollment enrollment entity to insert
     * @return persisted entity with generated ID
     * @throws DataAccessException if a database error occurs
     */
    public Enrollment insert(Connection conn, Enrollment enrollment) {
        String sql = "INSERT INTO enrollments (student_id, course_id, academic_year, status) " +
                     "VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, enrollment.getStudentId());
            pstmt.setInt(2, enrollment.getCourseId());
            pstmt.setString(3, enrollment.getAcademicYear());
            pstmt.setString(4, enrollment.getStatus() != null ? enrollment.getStatus() : "ACTIVE");

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    enrollment.setId(rs.getInt(1));
                }
            }
            return enrollment;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert enrollment: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to insert an enrollment using a managed connection.
     *
     * @param enrollment enrollment entity to insert
     * @return persisted entity
     */
    public Enrollment insert(Enrollment enrollment) {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, enrollment);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds an enrollment by primary key ID, populating student and course transient details.
     *
     * @param conn database connection
     * @param id enrollment ID
     * @return enrollment entity or {@code null} if not found
     */
    public Enrollment findById(Connection conn, int id) {
        String sql = BASE_SELECT + "WHERE e.id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find enrollment by ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find an enrollment by ID using a managed connection.
     *
     * @param id enrollment ID
     * @return enrollment entity or {@code null} if not found
     */
    public Enrollment findById(int id) {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, id);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds an enrollment by unique key (student_id, course_id, academic_year).
     *
     * @param conn database connection
     * @param studentId student ID
     * @param courseId course ID
     * @param academicYear academic year
     * @return enrollment entity or {@code null} if not found
     */
    public Enrollment findByStudentCourseYear(Connection conn, int studentId, int courseId, String academicYear) {
        String sql = BASE_SELECT + "WHERE e.student_id = ? AND e.course_id = ? AND e.academic_year = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, studentId);
            pstmt.setInt(2, courseId);
            pstmt.setString(3, academicYear);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find enrollment: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find enrollment by student, course, and year using a managed connection.
     *
     * @param studentId student ID
     * @param courseId course ID
     * @param academicYear academic year
     * @return enrollment entity or {@code null} if not found
     */
    public Enrollment findByStudentCourseYear(int studentId, int courseId, String academicYear) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByStudentCourseYear(conn, studentId, courseId, academicYear);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Counts the number of active enrollments for a course in a given academic year.
     * Used for course capacity validation.
     *
     * @param conn database connection
     * @param courseId course ID
     * @param academicYear academic year
     * @return number of students actively enrolled
     */
    public int countActiveByCourseAndYear(Connection conn, int courseId, String academicYear) {
        String sql = "SELECT COUNT(*) FROM enrollments WHERE course_id = ? AND academic_year = ? AND status = 'ACTIVE'";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, courseId);
            pstmt.setString(2, academicYear);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count enrollments: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to count active enrollments using a managed connection.
     *
     * @param courseId course ID
     * @param academicYear academic year
     * @return number of students actively enrolled
     */
    public int countActiveByCourseAndYear(int courseId, String academicYear) {
        try (Connection conn = DBConnection.getConnection()) {
            return countActiveByCourseAndYear(conn, courseId, academicYear);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all enrollments for a student.
     *
     * @param conn database connection
     * @param studentId student ID
     * @return list of enrollments
     */
    public List<Enrollment> findByStudent(Connection conn, int studentId) {
        String sql = BASE_SELECT + "WHERE e.student_id = ? ORDER BY e.academic_year DESC, c.course_code";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, studentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Enrollment> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find enrollments for student " + studentId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find enrollments by student using a managed connection.
     *
     * @param studentId student ID
     * @return list of enrollments
     */
    public List<Enrollment> findByStudent(int studentId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByStudent(conn, studentId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves enrollments for a student in a specific academic year.
     *
     * @param conn database connection
     * @param studentId student ID
     * @param academicYear academic year
     * @return list of enrollments
     */
    public List<Enrollment> findByStudentAndYear(Connection conn, int studentId, String academicYear) {
        String sql = BASE_SELECT + "WHERE e.student_id = ? AND e.academic_year = ? ORDER BY c.course_code";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, studentId);
            pstmt.setString(2, academicYear);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Enrollment> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find enrollments for student " + studentId + " in " + academicYear + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find enrollments by student and year using a managed connection.
     *
     * @param studentId student ID
     * @param academicYear academic year
     * @return list of enrollments
     */
    public List<Enrollment> findByStudentAndYear(int studentId, String academicYear) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByStudentAndYear(conn, studentId, academicYear);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all enrollments for a course in an academic year.
     *
     * @param conn database connection
     * @param courseId course ID
     * @param academicYear academic year
     * @return list of enrollments
     */
    public List<Enrollment> findByCourseAndYear(Connection conn, int courseId, String academicYear) {
        String sql = BASE_SELECT + "WHERE e.course_id = ? AND e.academic_year = ? ORDER BY s.student_id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, courseId);
            pstmt.setString(2, academicYear);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Enrollment> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find enrollments by course and year: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find enrollments by course and year using a managed connection.
     *
     * @param courseId course ID
     * @param academicYear academic year
     * @return list of enrollments
     */
    public List<Enrollment> findByCourseAndYear(int courseId, String academicYear) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByCourseAndYear(conn, courseId, academicYear);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves active enrollments for a course in an academic year (course roster).
     *
     * @param conn database connection
     * @param courseId course ID
     * @param academicYear academic year
     * @return list of active enrollments
     */
    public List<Enrollment> findActiveByCourseAndYear(Connection conn, int courseId, String academicYear) {
        String sql = BASE_SELECT + "WHERE e.course_id = ? AND e.academic_year = ? AND e.status = 'ACTIVE' ORDER BY s.student_id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, courseId);
            pstmt.setString(2, academicYear);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Enrollment> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find active roster for course " + courseId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find active roster using a managed connection.
     *
     * @param courseId course ID
     * @param academicYear academic year
     * @return list of active enrollments
     */
    public List<Enrollment> findActiveByCourseAndYear(int courseId, String academicYear) {
        try (Connection conn = DBConnection.getConnection()) {
            return findActiveByCourseAndYear(conn, courseId, academicYear);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all enrollments.
     *
     * @param conn database connection
     * @return list of all enrollments
     */
    public List<Enrollment> findAll(Connection conn) {
        String sql = BASE_SELECT + "ORDER BY e.academic_year DESC, c.course_code, s.student_id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<Enrollment> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve all enrollments: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to retrieve all enrollments using a managed connection.
     *
     * @return list of all enrollments
     */
    public List<Enrollment> findAll() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAll(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Updates enrollment details.
     * Automatically sets {@code updated_at = CURRENT_TIMESTAMP}.
     *
     * @param conn database connection
     * @param enrollment enrollment entity
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean update(Connection conn, Enrollment enrollment) {
        String sql = "UPDATE enrollments SET student_id = ?, course_id = ?, academic_year = ?, " +
                     "                      status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, enrollment.getStudentId());
            pstmt.setInt(2, enrollment.getCourseId());
            pstmt.setString(3, enrollment.getAcademicYear());
            pstmt.setString(4, enrollment.getStatus());
            pstmt.setInt(5, enrollment.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update enrollment ID " + enrollment.getId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to update an enrollment using a managed connection.
     *
     * @param enrollment enrollment entity
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean update(Enrollment enrollment) {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, enrollment);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Updates the status of an enrollment (ACTIVE, DROPPED, COMPLETED).
     * Automatically sets {@code updated_at = CURRENT_TIMESTAMP}.
     *
     * @param conn database connection
     * @param id enrollment ID
     * @param status new status
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean updateStatus(Connection conn, int id, String status) {
        String sql = "UPDATE enrollments SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setInt(2, id);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update status for enrollment ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to update enrollment status using a managed connection.
     *
     * @param id enrollment ID
     * @param status new status
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean updateStatus(int id, String status) {
        try (Connection conn = DBConnection.getConnection()) {
            return updateStatus(conn, id, status);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Maps the current row of a ResultSet to an Enrollment domain model.
     *
     * @param rs result set positioned at a valid row
     * @return populated Enrollment model
     * @throws SQLException if column reading fails
     */
    private Enrollment mapRow(ResultSet rs) throws SQLException {
        Enrollment e = new Enrollment();
        e.setId(rs.getInt("id"));
        e.setStudentId(rs.getInt("student_id"));
        e.setCourseId(rs.getInt("course_id"));
        e.setAcademicYear(rs.getString("academic_year"));
        e.setEnrolledDate(rs.getString("enrolled_date"));
        e.setStatus(rs.getString("status"));
        e.setCreatedAt(rs.getString("created_at"));
        e.setUpdatedAt(rs.getString("updated_at"));
        e.setStudentName(rs.getString("student_name"));
        e.setStudentIdNumber(rs.getString("student_id_number"));
        e.setCourseCode(rs.getString("course_code"));
        e.setCourseName(rs.getString("course_name"));
        return e;
    }
}

