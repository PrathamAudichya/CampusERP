package com.college.dao;

import com.college.config.DBConnection;
import com.college.exception.DataAccessException;
import com.college.model.AttendanceSession;
import com.college.model.AttendanceSubmission;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for interactive attendance check-in sessions and student submissions.
 */
public class AttendanceSessionDAO {

    public AttendanceSession createSession(Connection conn, AttendanceSession session) throws SQLException {
        String sessionNum = (session.getSessionNumber() != null && !session.getSessionNumber().trim().isEmpty())
                ? session.getSessionNumber().trim() : "1";
        String sql = "INSERT INTO attendance_sessions (course_id, faculty_id, session_date, session_code, academic_year, expires_at, status, session_number) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, session.getCourseId());
            ps.setInt(2, session.getFacultyId());
            ps.setString(3, session.getSessionDate());
            ps.setString(4, session.getSessionCode());
            ps.setString(5, session.getAcademicYear());
            ps.setString(6, session.getExpiresAt());
            ps.setString(7, session.getStatus());
            ps.setString(8, sessionNum);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    session.setId(rs.getInt(1));
                }
            }
        }
        return session;
    }

    public AttendanceSession createSession(AttendanceSession session) {
        try (Connection conn = DBConnection.getConnection()) {
            return createSession(conn, session);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to create attendance session: " + e.getMessage(), e);
        }
    }

    public AttendanceSession findById(int id) {
        String sql = "SELECT * FROM attendance_sessions WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToSession(rs);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find attendance session by ID: " + e.getMessage(), e);
        }
        return null;
    }

    public AttendanceSession findOpenSessionByCode(String sessionCode) {
        String sql = "SELECT * FROM attendance_sessions WHERE session_code = ? AND status = 'OPEN'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToSession(rs);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find open attendance session: " + e.getMessage(), e);
        }
        return null;
    }

    public List<AttendanceSession> findSessionsByFaculty(int facultyId) {
        List<AttendanceSession> list = new ArrayList<>();
        String sql = "SELECT * FROM attendance_sessions WHERE faculty_id = ? ORDER BY id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, facultyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToSession(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve faculty attendance sessions: " + e.getMessage(), e);
        }
        return list;
    }

    public void updateStatus(Connection conn, int sessionId, String status) throws SQLException {
        String sql = "UPDATE attendance_sessions SET status = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, sessionId);
            ps.executeUpdate();
        }
    }

    public void updateStatus(int sessionId, String status) {
        try (Connection conn = DBConnection.getConnection()) {
            updateStatus(conn, sessionId, status);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update session status: " + e.getMessage(), e);
        }
    }

    public void updateFinalized(Connection conn, int sessionId, String finalizedAt, int finalizedBy) throws SQLException {
        String sql = "UPDATE attendance_sessions SET status = 'FINALIZED', finalized_at = ?, finalized_by = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, finalizedAt);
            ps.setInt(2, finalizedBy);
            ps.setInt(3, sessionId);
            ps.executeUpdate();
        }
    }

    public void updateFinalized(int sessionId, String finalizedAt, int finalizedBy) {
        try (Connection conn = DBConnection.getConnection()) {
            updateFinalized(conn, sessionId, finalizedAt, finalizedBy);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to finalize session: " + e.getMessage(), e);
        }
    }

    public void regenerateCode(Connection conn, int sessionId, String newCode) throws SQLException {
        String sql = "UPDATE attendance_sessions SET session_code = ? WHERE id = ? AND status != 'FINALIZED'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newCode);
            ps.setInt(2, sessionId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Cannot regenerate code: session not found or already FINALIZED.");
            }
        }
    }

    public void regenerateCode(int sessionId, String newCode) {
        try (Connection conn = DBConnection.getConnection()) {
            regenerateCode(conn, sessionId, newCode);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to regenerate session code: " + e.getMessage(), e);
        }
    }

    public AttendanceSession findOpenSessionByCourseAndDate(int courseId, String sessionDate) {
        String sql = "SELECT * FROM attendance_sessions WHERE course_id = ? AND session_date = ? AND status = 'OPEN' ORDER BY id DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            ps.setString(2, sessionDate);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToSession(rs);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find open session by course and date: " + e.getMessage(), e);
        }
        return null;
    }

    public List<AttendanceSession> findSessionsByCourseAndYear(int courseId, String academicYear) {
        List<AttendanceSession> list = new ArrayList<>();
        String sql = "SELECT * FROM attendance_sessions WHERE course_id = ? AND academic_year = ? ORDER BY id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            ps.setString(2, academicYear);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToSession(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve course sessions: " + e.getMessage(), e);
        }
        return list;
    }

    public List<AttendanceSession> findActiveSessionsForStudent(int studentId) {
        List<AttendanceSession> list = new ArrayList<>();
        String sql = "SELECT s.* FROM attendance_sessions s " +
                     "JOIN enrollments e ON s.course_id = e.course_id AND s.academic_year = e.academic_year " +
                     "WHERE e.student_id = ? AND e.status = 'ACTIVE' AND s.status = 'OPEN' " +
                     "ORDER BY s.id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToSession(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve active sessions for student: " + e.getMessage(), e);
        }
        return list;
    }

    public List<AttendanceSession> findAllSessions() {
        List<AttendanceSession> list = new ArrayList<>();
        String sql = "SELECT * FROM attendance_sessions ORDER BY id DESC";
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRowToSession(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve all sessions: " + e.getMessage(), e);
        }
        return list;
    }

    public void recordFailedAttempt(int sessionId, int studentId) {
        String sql = "INSERT INTO attendance_failed_attempts (session_id, student_id, attempt_count, last_attempt_at) " +
                     "VALUES (?, ?, 1, CURRENT_TIMESTAMP) " +
                     "ON CONFLICT(session_id, student_id) DO UPDATE SET attempt_count = attempt_count + 1, last_attempt_at = CURRENT_TIMESTAMP";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ps.setInt(2, studentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to record failed attempt: " + e.getMessage(), e);
        }
    }

    public int getFailedAttempts(int sessionId, int studentId) {
        String sql = "SELECT attempt_count FROM attendance_failed_attempts WHERE session_id = ? AND student_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ps.setInt(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("attempt_count");
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to query failed attempts: " + e.getMessage(), e);
        }
        return 0;
    }

    public void resetFailedAttempts(int sessionId, int studentId) {
        String sql = "DELETE FROM attendance_failed_attempts WHERE session_id = ? AND student_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ps.setInt(2, studentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to reset failed attempts: " + e.getMessage(), e);
        }
    }

    public AttendanceSubmission recordSubmission(Connection conn, AttendanceSubmission sub) throws SQLException {
        String sql = "INSERT INTO attendance_submissions (session_id, student_id, enrollment_id, submitted_code, status) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, sub.getSessionId());
            ps.setInt(2, sub.getStudentId());
            ps.setInt(3, sub.getEnrollmentId());
            ps.setString(4, sub.getSubmittedCode());
            ps.setString(5, sub.getStatus());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    sub.setId(rs.getInt(1));
                }
            }
        }
        return sub;
    }

    public AttendanceSubmission recordSubmission(AttendanceSubmission sub) {
        try (Connection conn = DBConnection.getConnection()) {
            return recordSubmission(conn, sub);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to record attendance submission: " + e.getMessage(), e);
        }
    }

    public boolean hasStudentSubmitted(int sessionId, int studentId) {
        String sql = "SELECT 1 FROM attendance_submissions WHERE session_id = ? AND student_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ps.setInt(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check student submission status: " + e.getMessage(), e);
        }
    }

    public List<AttendanceSubmission> findSubmissionsBySession(int sessionId) {
        List<AttendanceSubmission> list = new ArrayList<>();
        String sql = "SELECT * FROM attendance_submissions WHERE session_id = ? ORDER BY id ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToSubmission(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve submissions: " + e.getMessage(), e);
        }
        return list;
    }

    private AttendanceSession mapRowToSession(ResultSet rs) throws SQLException {
        AttendanceSession s = new AttendanceSession();
        s.setId(rs.getInt("id"));
        s.setCourseId(rs.getInt("course_id"));
        s.setFacultyId(rs.getInt("faculty_id"));
        s.setSessionDate(rs.getString("session_date"));
        s.setSessionCode(rs.getString("session_code"));
        s.setAcademicYear(rs.getString("academic_year"));
        s.setExpiresAt(rs.getString("expires_at"));
        s.setStatus(rs.getString("status"));
        s.setCreatedAt(rs.getString("created_at"));
        try {
            String sn = rs.getString("session_number");
            if (sn != null) s.setSessionNumber(sn);
        } catch (SQLException ignored) {}
        try {
            s.setFinalizedAt(rs.getString("finalized_at"));
        } catch (SQLException ignored) {}
        try {
            int fb = rs.getInt("finalized_by");
            if (!rs.wasNull()) s.setFinalizedBy(fb);
        } catch (SQLException ignored) {}
        return s;
    }

    private AttendanceSubmission mapRowToSubmission(ResultSet rs) throws SQLException {
        AttendanceSubmission sub = new AttendanceSubmission();
        sub.setId(rs.getInt("id"));
        sub.setSessionId(rs.getInt("session_id"));
        sub.setStudentId(rs.getInt("student_id"));
        sub.setEnrollmentId(rs.getInt("enrollment_id"));
        sub.setSubmittedCode(rs.getString("submitted_code"));
        sub.setStatus(rs.getString("status"));
        sub.setSubmissionTime(rs.getString("submission_time"));
        return sub;
    }
}

