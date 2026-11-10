package com.college.dao;

import com.college.config.DBConnection;
import com.college.exception.DataAccessException;
import com.college.model.Attendance;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the {@code attendance} table.
 *
 * <p>Handles attendance recording, atomic UPSERTs on (enrollment_id, attendance_date),
 * attendance history queries by course, student, or date, and transient presentation joins.</p>
 */
public class AttendanceDAO {

    private static final String BASE_SELECT =
        "SELECT a.id, a.enrollment_id, a.attendance_date, a.status, a.remarks, a.session_code, " +
        "       (s.first_name || ' ' || s.last_name) AS student_name, s.student_id AS student_id_number, " +
        "       c.course_code " +
        "FROM attendance a " +
        "JOIN enrollments e ON a.enrollment_id = e.id " +
        "JOIN students s ON e.student_id = s.id " +
        "JOIN courses c ON e.course_id = c.id ";

    /**
     * Inserts a new attendance record.
     *
     * @param conn database connection
     * @param attendance attendance entity to insert
     * @return persisted entity with generated ID
     * @throws DataAccessException if a database error occurs
     */
    public Attendance insert(Connection conn, Attendance attendance) {
        String sql = "INSERT INTO attendance (enrollment_id, attendance_date, status, remarks, session_code) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, attendance.getEnrollmentId());
            pstmt.setString(2, attendance.getAttendanceDate());
            pstmt.setString(3, attendance.getStatus());
            pstmt.setString(4, attendance.getRemarks());
            pstmt.setString(5, attendance.getSessionCode());

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    attendance.setId(rs.getInt(1));
                }
            }
            return attendance;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert attendance: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to insert attendance using a managed connection.
     *
     * @param attendance attendance entity to insert
     * @return persisted entity
     */
    public Attendance insert(Attendance attendance) {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, attendance);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Atomically inserts or updates attendance on conflict of (enrollment_id, attendance_date).
     *
     * @param conn database connection
     * @param enrollmentId enrollment ID
     * @param attendanceDate ISO date string (YYYY-MM-DD)
     * @param status attendance status (PRESENT, ABSENT, LATE)
     * @param remarks optional remarks
     * @return populated Attendance model
     * @throws DataAccessException if a database error occurs
     */
    public Attendance upsert(Connection conn, int enrollmentId, String attendanceDate, String status, String remarks) {
        return upsert(conn, enrollmentId, attendanceDate, status, remarks, null);
    }

    /**
     * Atomically inserts or updates attendance on conflict of (enrollment_id, attendance_date),
     * recording session verification code when provided.
     *
     * @param conn database connection
     * @param enrollmentId enrollment ID
     * @param attendanceDate ISO date string (YYYY-MM-DD)
     * @param status attendance status (PRESENT, ABSENT, LATE)
     * @param remarks optional remarks
     * @param sessionCode optional 6-digit session verification code
     * @return populated Attendance model
     * @throws DataAccessException if a database error occurs
     */
    public Attendance upsert(Connection conn, int enrollmentId, String attendanceDate, String status, String remarks, String sessionCode) {
        String sql = "INSERT INTO attendance (enrollment_id, attendance_date, status, remarks, session_code) " +
                     "VALUES (?, ?, ?, ?, ?) " +
                     "ON CONFLICT(enrollment_id, attendance_date) " +
                     "DO UPDATE SET status = excluded.status, remarks = excluded.remarks, " +
                     "              session_code = COALESCE(excluded.session_code, attendance.session_code)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, enrollmentId);
            pstmt.setString(2, attendanceDate);
            pstmt.setString(3, status);
            pstmt.setString(4, remarks);
            pstmt.setString(5, sessionCode);

            pstmt.executeUpdate();
            return findByEnrollmentAndDate(conn, enrollmentId, attendanceDate);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to upsert attendance: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to upsert attendance using a managed connection.
     *
     * @param enrollmentId enrollment ID
     * @param attendanceDate ISO date string
     * @param status attendance status
     * @param remarks optional remarks
     * @return populated Attendance model
     */
    public Attendance upsert(int enrollmentId, String attendanceDate, String status, String remarks) {
        try (Connection conn = DBConnection.getConnection()) {
            return upsert(conn, enrollmentId, attendanceDate, status, remarks);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds attendance by primary key ID.
     *
     * @param conn database connection
     * @param id attendance ID
     * @return attendance entity or {@code null} if not found
     */
    public Attendance findById(Connection conn, int id) {
        String sql = BASE_SELECT + "WHERE a.id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find attendance by ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find attendance by ID using a managed connection.
     *
     * @param id attendance ID
     * @return attendance entity or {@code null} if not found
     */
    public Attendance findById(int id) {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, id);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds attendance by unique key (enrollment_id, attendance_date).
     *
     * @param conn database connection
     * @param enrollmentId enrollment ID
     * @param attendanceDate attendance date
     * @return attendance entity or {@code null} if not found
     */
    public Attendance findByEnrollmentAndDate(Connection conn, int enrollmentId, String attendanceDate) {
        String sql = BASE_SELECT + "WHERE a.enrollment_id = ? AND a.attendance_date = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, enrollmentId);
            pstmt.setString(2, attendanceDate);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find attendance by enrollment and date: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find attendance by enrollment and date using a managed connection.
     *
     * @param enrollmentId enrollment ID
     * @param attendanceDate attendance date
     * @return attendance entity or {@code null} if not found
     */
    public Attendance findByEnrollmentAndDate(int enrollmentId, String attendanceDate) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByEnrollmentAndDate(conn, enrollmentId, attendanceDate);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all attendance records for an enrollment.
     *
     * @param conn database connection
     * @param enrollmentId enrollment ID
     * @return list of attendance records
     */
    public List<Attendance> findByEnrollment(Connection conn, int enrollmentId) {
        String sql = BASE_SELECT + "WHERE a.enrollment_id = ? ORDER BY a.attendance_date DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, enrollmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Attendance> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find attendance for enrollment " + enrollmentId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find attendance by enrollment using a managed connection.
     *
     * @param enrollmentId enrollment ID
     * @return list of attendance records
     */
    public List<Attendance> findByEnrollment(int enrollmentId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByEnrollment(conn, enrollmentId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves attendance records for a course on a specific date.
     *
     * @param conn database connection
     * @param courseId course ID
     * @param attendanceDate date (YYYY-MM-DD)
     * @return list of attendance records
     */
    public List<Attendance> findByCourseAndDate(Connection conn, int courseId, String attendanceDate) {
        String sql = BASE_SELECT + "WHERE e.course_id = ? AND a.attendance_date = ? ORDER BY s.student_id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, courseId);
            pstmt.setString(2, attendanceDate);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Attendance> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find attendance by course and date: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find attendance by course and date using a managed connection.
     *
     * @param courseId course ID
     * @param attendanceDate date
     * @return list of attendance records
     */
    public List<Attendance> findByCourseAndDate(int courseId, String attendanceDate) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByCourseAndDate(conn, courseId, attendanceDate);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all attendance records for a course in an academic year.
     *
     * @param conn database connection
     * @param courseId course ID
     * @param academicYear academic year
     * @return list of attendance records
     */
    public List<Attendance> findByCourseAndYear(Connection conn, int courseId, String academicYear) {
        String sql = BASE_SELECT + "WHERE e.course_id = ? AND e.academic_year = ? ORDER BY a.attendance_date DESC, s.student_id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, courseId);
            pstmt.setString(2, academicYear);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Attendance> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find attendance by course and year: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find attendance by course and year using a managed connection.
     *
     * @param courseId course ID
     * @param academicYear academic year
     * @return list of attendance records
     */
    public List<Attendance> findByCourseAndYear(int courseId, String academicYear) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByCourseAndYear(conn, courseId, academicYear);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Updates an attendance record.
     *
     * @param conn database connection
     * @param attendance attendance entity
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean update(Connection conn, Attendance attendance) {
        String sql = "UPDATE attendance SET status = ?, remarks = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, attendance.getStatus());
            pstmt.setString(2, attendance.getRemarks());
            pstmt.setInt(3, attendance.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update attendance ID " + attendance.getId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to update attendance using a managed connection.
     *
     * @param attendance attendance entity
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean update(Attendance attendance) {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, attendance);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Maps the current row of a ResultSet to an Attendance domain model.
     *
     * @param rs result set positioned at a valid row
     * @return populated Attendance model
     * @throws SQLException if column reading fails
     */
    private Attendance mapRow(ResultSet rs) throws SQLException {
        Attendance a = new Attendance();
        a.setId(rs.getInt("id"));
        a.setEnrollmentId(rs.getInt("enrollment_id"));
        a.setAttendanceDate(rs.getString("attendance_date"));
        a.setStatus(rs.getString("status"));
        a.setRemarks(rs.getString("remarks"));
        try {
            a.setSessionCode(rs.getString("session_code"));
        } catch (SQLException ignored) {}
        a.setStudentName(rs.getString("student_name"));
        a.setStudentIdNumber(rs.getString("student_id_number"));
        a.setCourseCode(rs.getString("course_code"));
        return a;
    }
}

