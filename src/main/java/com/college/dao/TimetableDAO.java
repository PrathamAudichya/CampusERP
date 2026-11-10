package com.college.dao;

import com.college.config.DBConnection;
import com.college.exception.DataAccessException;
import com.college.model.TimetableEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the {@code timetables} table.
 *
 * <p>Handles persistence, conflict inspection queries, and timetable lookups
 * filtered by department, semester, faculty, classroom, and day of week.</p>
 */
public class TimetableDAO {

    private static final String BASE_SELECT =
            "SELECT t.id, t.course_id, t.faculty_id, t.department_id, t.semester, " +
            "       t.day_of_week, t.start_time, t.end_time, t.classroom, t.academic_year, " +
            "       t.created_at, t.updated_at, " +
            "       c.course_code, c.name AS course_name, " +
            "       (f.first_name || ' ' || f.last_name) AS faculty_name, " +
            "       d.code AS department_code, d.name AS department_name " +
            "FROM timetables t " +
            "JOIN courses c ON t.course_id = c.id " +
            "JOIN faculty f ON t.faculty_id = f.id " +
            "JOIN departments d ON t.department_id = d.id ";

    /**
     * Inserts a new timetable entry.
     *
     * @param conn  database connection
     * @param entry timetable entity
     * @return persisted entity with generated primary key
     */
    public TimetableEntry insert(Connection conn, TimetableEntry entry) {
        String sql = "INSERT INTO timetables (course_id, faculty_id, department_id, semester, day_of_week, " +
                     "start_time, end_time, classroom, academic_year) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, entry.getCourseId());
            pstmt.setInt(2, entry.getFacultyId());
            pstmt.setInt(3, entry.getDepartmentId());
            pstmt.setString(4, entry.getSemester());
            pstmt.setString(5, entry.getDayOfWeek());
            pstmt.setString(6, entry.getStartTime());
            pstmt.setString(7, entry.getEndTime());
            pstmt.setString(8, entry.getClassroom());
            pstmt.setString(9, entry.getAcademicYear());

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    entry.setId(rs.getInt(1));
                }
            }
            return findById(conn, entry.getId());
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert timetable entry: " + e.getMessage(), e);
        }
    }

    public TimetableEntry insert(TimetableEntry entry) {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, entry);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Updates an existing timetable entry.
     */
    public boolean update(Connection conn, TimetableEntry entry) {
        String sql = "UPDATE timetables SET course_id = ?, faculty_id = ?, department_id = ?, semester = ?, " +
                     "day_of_week = ?, start_time = ?, end_time = ?, classroom = ?, academic_year = ?, " +
                     "updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, entry.getCourseId());
            pstmt.setInt(2, entry.getFacultyId());
            pstmt.setInt(3, entry.getDepartmentId());
            pstmt.setString(4, entry.getSemester());
            pstmt.setString(5, entry.getDayOfWeek());
            pstmt.setString(6, entry.getStartTime());
            pstmt.setString(7, entry.getEndTime());
            pstmt.setString(8, entry.getClassroom());
            pstmt.setString(9, entry.getAcademicYear());
            pstmt.setInt(10, entry.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update timetable entry ID " + entry.getId() + ": " + e.getMessage(), e);
        }
    }

    public boolean update(TimetableEntry entry) {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, entry);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a timetable entry by primary key ID.
     */
    public boolean delete(Connection conn, int id) {
        String sql = "DELETE FROM timetables WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete timetable entry ID " + id + ": " + e.getMessage(), e);
        }
    }

    public boolean delete(int id) {
        try (Connection conn = DBConnection.getConnection()) {
            return delete(conn, id);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a timetable entry by primary key ID.
     */
    public TimetableEntry findById(Connection conn, int id) {
        String sql = BASE_SELECT + "WHERE t.id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find timetable entry ID " + id + ": " + e.getMessage(), e);
        }
    }

    public TimetableEntry findById(int id) {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, id);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all timetable entries for a specific academic year.
     */
    public List<TimetableEntry> findByAcademicYear(Connection conn, String academicYear) {
        String sql = BASE_SELECT + "WHERE t.academic_year = ? ORDER BY t.day_of_week, t.start_time";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, academicYear);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<TimetableEntry> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find timetable entries for academic year: " + e.getMessage(), e);
        }
    }

    public List<TimetableEntry> findByAcademicYear(String academicYear) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByAcademicYear(conn, academicYear);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves timetable entries for a department, semester, and academic year.
     */
    public List<TimetableEntry> findByDepartmentAndSemester(Connection conn, int departmentId, String semester, String academicYear) {
        String sql = BASE_SELECT + "WHERE t.department_id = ? AND t.semester = ? AND t.academic_year = ? ORDER BY t.day_of_week, t.start_time";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, departmentId);
            pstmt.setString(2, semester);
            pstmt.setString(3, academicYear);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<TimetableEntry> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find timetable entries: " + e.getMessage(), e);
        }
    }

    public List<TimetableEntry> findByDepartmentAndSemester(int departmentId, String semester, String academicYear) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByDepartmentAndSemester(conn, departmentId, semester, academicYear);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves timetable entries for a faculty member in an academic year.
     */
    public List<TimetableEntry> findByFaculty(Connection conn, int facultyId, String academicYear) {
        String sql = BASE_SELECT + "WHERE t.faculty_id = ? AND t.academic_year = ? ORDER BY t.day_of_week, t.start_time";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, facultyId);
            pstmt.setString(2, academicYear);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<TimetableEntry> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find faculty timetable entries: " + e.getMessage(), e);
        }
    }

    public List<TimetableEntry> findByFaculty(int facultyId, String academicYear) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByFaculty(conn, facultyId, academicYear);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves timetable entries by day of week and academic year.
     */
    public List<TimetableEntry> findByDayAndYear(Connection conn, String dayOfWeek, String academicYear) {
        String sql = BASE_SELECT + "WHERE t.day_of_week = ? AND t.academic_year = ? ORDER BY t.start_time";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, dayOfWeek);
            pstmt.setString(2, academicYear);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<TimetableEntry> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find timetable entries by day: " + e.getMessage(), e);
        }
    }

    public List<TimetableEntry> findByDayAndYear(String dayOfWeek, String academicYear) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByDayAndYear(conn, dayOfWeek, academicYear);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    private TimetableEntry mapRow(ResultSet rs) throws SQLException {
        TimetableEntry e = new TimetableEntry();
        e.setId(rs.getInt("id"));
        e.setCourseId(rs.getInt("course_id"));
        e.setFacultyId(rs.getInt("faculty_id"));
        e.setDepartmentId(rs.getInt("department_id"));
        e.setSemester(rs.getString("semester"));
        e.setDayOfWeek(rs.getString("day_of_week"));
        e.setStartTime(rs.getString("start_time"));
        e.setEndTime(rs.getString("end_time"));
        e.setClassroom(rs.getString("classroom"));
        e.setAcademicYear(rs.getString("academic_year"));
        e.setCreatedAt(rs.getString("created_at"));
        e.setUpdatedAt(rs.getString("updated_at"));

        e.setCourseCode(rs.getString("course_code"));
        e.setCourseName(rs.getString("course_name"));
        e.setFacultyName(rs.getString("faculty_name"));
        e.setDepartmentCode(rs.getString("department_code"));
        e.setDepartmentName(rs.getString("department_name"));
        return e;
    }
}

