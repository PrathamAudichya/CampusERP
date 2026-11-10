package com.college.dao;

import com.college.config.DBConnection;
import com.college.exception.DataAccessException;
import com.college.model.CourseAssignment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the {@code course_assignments} table.
 *
 * <p>Handles assigning faculty members to courses per academic year, active assignment
 * authorization checks, and course/faculty JOIN queries for UI presentation.</p>
 */
public class CourseAssignmentDAO {

    private static final String BASE_SELECT =
        "SELECT ca.id, ca.course_id, ca.faculty_id, ca.academic_year, ca.active, ca.assigned_date, " +
        "       c.course_code, c.name AS course_name, (f.first_name || ' ' || f.last_name) AS faculty_name " +
        "FROM course_assignments ca " +
        "JOIN courses c ON ca.course_id = c.id " +
        "JOIN faculty f ON ca.faculty_id = f.id ";

    /**
     * Inserts a new course assignment.
     *
     * @param conn database connection
     * @param ca assignment entity to insert
     * @return persisted entity with generated ID
     * @throws DataAccessException if a database error occurs
     */
    public CourseAssignment insert(Connection conn, CourseAssignment ca) {
        String sql = "INSERT INTO course_assignments (course_id, faculty_id, academic_year, active) " +
                     "VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, ca.getCourseId());
            pstmt.setInt(2, ca.getFacultyId());
            pstmt.setString(3, ca.getAcademicYear());
            pstmt.setInt(4, ca.isActive() ? 1 : 0);

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    ca.setId(rs.getInt(1));
                }
            }
            return ca;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert course assignment: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to insert a course assignment using a managed connection.
     *
     * @param ca assignment entity to insert
     * @return persisted entity
     */
    public CourseAssignment insert(CourseAssignment ca) {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, ca);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds an assignment by primary key ID, populating course and faculty transient details.
     *
     * @param conn database connection
     * @param id assignment ID
     * @return assignment entity or {@code null} if not found
     */
    public CourseAssignment findById(Connection conn, int id) {
        String sql = BASE_SELECT + "WHERE ca.id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find course assignment by ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find an assignment by ID using a managed connection.
     *
     * @param id assignment ID
     * @return assignment entity or {@code null} if not found
     */
    public CourseAssignment findById(int id) {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, id);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds an assignment by composite unique key (course_id, faculty_id, academic_year).
     *
     * @param conn database connection
     * @param courseId course ID
     * @param facultyId faculty ID
     * @param academicYear academic year (e.g., "2026-2027")
     * @return assignment entity or {@code null} if not found
     */
    public CourseAssignment findByCourseFacultyYear(Connection conn, int courseId, int facultyId, String academicYear) {
        String sql = BASE_SELECT + "WHERE ca.course_id = ? AND ca.faculty_id = ? AND ca.academic_year = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, courseId);
            pstmt.setInt(2, facultyId);
            pstmt.setString(3, academicYear);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find course assignment: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find an assignment by unique key using a managed connection.
     *
     * @param courseId course ID
     * @param facultyId faculty ID
     * @param academicYear academic year
     * @return assignment entity or {@code null} if not found
     */
    public CourseAssignment findByCourseFacultyYear(int courseId, int facultyId, String academicYear) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByCourseFacultyYear(conn, courseId, facultyId, academicYear);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds an active assignment for authorization verification.
     *
     * @param conn database connection
     * @param courseId course ID
     * @param facultyId faculty ID
     * @param academicYear academic year
     * @return active assignment entity or {@code null} if not assigned or inactive
     */
    public CourseAssignment findActiveByCourseAndFaculty(Connection conn, int courseId, int facultyId, String academicYear) {
        String sql = BASE_SELECT + "WHERE ca.course_id = ? AND ca.faculty_id = ? AND ca.academic_year = ? AND ca.active = 1";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, courseId);
            pstmt.setInt(2, facultyId);
            pstmt.setString(3, academicYear);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check active course assignment: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to check active assignment using a managed connection.
     *
     * @param courseId course ID
     * @param facultyId faculty ID
     * @param academicYear academic year
     * @return active assignment entity or {@code null}
     */
    public CourseAssignment findActiveByCourseAndFaculty(int courseId, int facultyId, String academicYear) {
        try (Connection conn = DBConnection.getConnection()) {
            return findActiveByCourseAndFaculty(conn, courseId, facultyId, academicYear);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all assignments for a specific course.
     *
     * @param conn database connection
     * @param courseId course ID
     * @return list of assignments
     */
    public List<CourseAssignment> findByCourse(Connection conn, int courseId) {
        String sql = BASE_SELECT + "WHERE ca.course_id = ? ORDER BY ca.academic_year DESC, f.last_name";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, courseId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<CourseAssignment> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find assignments by course " + courseId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find assignments by course using a managed connection.
     *
     * @param courseId course ID
     * @return list of assignments
     */
    public List<CourseAssignment> findByCourse(int courseId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByCourse(conn, courseId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all assignments for a faculty member.
     *
     * @param conn database connection
     * @param facultyId faculty ID
     * @return list of assignments
     */
    public List<CourseAssignment> findByFaculty(Connection conn, int facultyId) {
        String sql = BASE_SELECT + "WHERE ca.faculty_id = ? ORDER BY ca.academic_year DESC, c.course_code";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, facultyId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<CourseAssignment> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find assignments by faculty " + facultyId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find assignments by faculty using a managed connection.
     *
     * @param facultyId faculty ID
     * @return list of assignments
     */
    public List<CourseAssignment> findByFaculty(int facultyId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByFaculty(conn, facultyId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves active assignments for a faculty member in a specific academic year.
     *
     * @param conn database connection
     * @param facultyId faculty ID
     * @param academicYear academic year
     * @return list of active assignments
     */
    public List<CourseAssignment> findActiveByFacultyAndYear(Connection conn, int facultyId, String academicYear) {
        String sql = BASE_SELECT + "WHERE ca.faculty_id = ? AND ca.academic_year = ? AND ca.active = 1 " +
                     "ORDER BY c.course_code";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, facultyId);
            pstmt.setString(2, academicYear);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<CourseAssignment> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find active assignments: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find active assignments by faculty and year using a managed connection.
     *
     * @param facultyId faculty ID
     * @param academicYear academic year
     * @return list of active assignments
     */
    public List<CourseAssignment> findActiveByFacultyAndYear(int facultyId, String academicYear) {
        try (Connection conn = DBConnection.getConnection()) {
            return findActiveByFacultyAndYear(conn, facultyId, academicYear);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all course assignments.
     *
     * @param conn database connection
     * @return list of all assignments
     */
    public List<CourseAssignment> findAll(Connection conn) {
        String sql = BASE_SELECT + "ORDER BY ca.academic_year DESC, c.course_code";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<CourseAssignment> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve all course assignments: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to retrieve all assignments using a managed connection.
     *
     * @return list of all assignments
     */
    public List<CourseAssignment> findAll() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAll(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Updates an assignment (e.g. active flag or assigned date).
     *
     * @param conn database connection
     * @param ca assignment entity
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean update(Connection conn, CourseAssignment ca) {
        String sql = "UPDATE course_assignments SET course_id = ?, faculty_id = ?, academic_year = ?, " +
                     "                              active = ?, assigned_date = CURRENT_TIMESTAMP " +
                     "WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, ca.getCourseId());
            pstmt.setInt(2, ca.getFacultyId());
            pstmt.setString(3, ca.getAcademicYear());
            pstmt.setInt(4, ca.isActive() ? 1 : 0);
            pstmt.setInt(5, ca.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update course assignment ID " + ca.getId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to update an assignment using a managed connection.
     *
     * @param ca assignment entity
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean update(CourseAssignment ca) {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, ca);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Sets the active status of an assignment (soft delete / reactivate).
     *
     * @param conn database connection
     * @param id assignment ID
     * @param active new active state
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean setActive(Connection conn, int id, boolean active) {
        String sql = "UPDATE course_assignments SET active = ?, assigned_date = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, active ? 1 : 0);
            pstmt.setInt(2, id);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to set active status for assignment ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to set assignment active status using a managed connection.
     *
     * @param id assignment ID
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
     * Maps the current row of a ResultSet to a CourseAssignment domain model.
     *
     * @param rs result set positioned at a valid row
     * @return populated CourseAssignment model
     * @throws SQLException if column reading fails
     */
    private CourseAssignment mapRow(ResultSet rs) throws SQLException {
        CourseAssignment ca = new CourseAssignment();
        ca.setId(rs.getInt("id"));
        ca.setCourseId(rs.getInt("course_id"));
        ca.setFacultyId(rs.getInt("faculty_id"));
        ca.setAcademicYear(rs.getString("academic_year"));
        ca.setActive(rs.getInt("active") == 1);
        ca.setAssignedDate(rs.getString("assigned_date"));
        ca.setCourseCode(rs.getString("course_code"));
        ca.setCourseName(rs.getString("course_name"));
        ca.setFacultyName(rs.getString("faculty_name"));
        return ca;
    }
}

