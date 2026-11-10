package com.college.dao;

import com.college.config.DBConnection;
import com.college.exception.DataAccessException;
import com.college.model.Course;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the {@code courses} table.
 *
 * <p>Handles course catalog persistence, lookups by code or ID, department joins
 * for transient display properties, capacity tracking, and course lifecycle management.</p>
 */
public class CourseDAO {

    private static final String BASE_SELECT =
        "SELECT c.id, c.department_id, c.course_code, c.name, c.credits, c.semester, " +
        "       c.max_capacity, c.active, c.created_at, c.updated_at, d.name AS department_name " +
        "FROM courses c " +
        "LEFT JOIN departments d ON c.department_id = d.id ";

    /**
     * Inserts a new course.
     *
     * @param conn database connection
     * @param course course entity to insert
     * @return the persisted course with generated ID
     * @throws DataAccessException if a database error occurs
     */
    public Course insert(Connection conn, Course course) {
        String sql = "INSERT INTO courses (department_id, course_code, name, credits, semester, max_capacity, active) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, course.getDepartmentId());
            pstmt.setString(2, course.getCourseCode());
            pstmt.setString(3, course.getName());
            pstmt.setInt(4, course.getCredits());
            pstmt.setString(5, course.getSemester());
            if (course.getMaxCapacity() != null) {
                pstmt.setInt(6, course.getMaxCapacity());
            } else {
                pstmt.setNull(6, Types.INTEGER);
            }
            pstmt.setInt(7, course.isActive() ? 1 : 0);

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    course.setId(rs.getInt(1));
                }
            }
            return course;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert course '" + course.getCourseCode() + "': " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to insert a course using a managed connection.
     *
     * @param course course entity to insert
     * @return the persisted course
     */
    public Course insert(Course course) {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, course);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a course by primary key ID, populating departmentName.
     *
     * @param conn database connection
     * @param id course record ID
     * @return course entity or {@code null} if not found
     */
    public Course findById(Connection conn, int id) {
        String sql = BASE_SELECT + "WHERE c.id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find course by ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find a course by ID using a managed connection.
     *
     * @param id course record ID
     * @return course entity or {@code null} if not found
     */
    public Course findById(int id) {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, id);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a course by unique course code (e.g., "CS101").
     *
     * @param conn database connection
     * @param courseCode unique course code
     * @return course entity or {@code null} if not found
     */
    public Course findByCourseCode(Connection conn, String courseCode) {
        String sql = BASE_SELECT + "WHERE c.course_code = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, courseCode);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find course by code '" + courseCode + "': " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find a course by courseCode using a managed connection.
     *
     * @param courseCode unique course code
     * @return course entity or {@code null} if not found
     */
    public Course findByCourseCode(String courseCode) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByCourseCode(conn, courseCode);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all courses.
     *
     * @param conn database connection
     * @return list of all courses
     */
    public List<Course> findAll(Connection conn) {
        String sql = BASE_SELECT + "ORDER BY c.course_code";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<Course> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve all courses: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to retrieve all courses using a managed connection.
     *
     * @return list of all courses
     */
    public List<Course> findAll() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAll(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all active courses.
     *
     * @param conn database connection
     * @return list of active courses
     */
    public List<Course> findAllActive(Connection conn) {
        String sql = BASE_SELECT + "WHERE c.active = 1 ORDER BY c.course_code";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<Course> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve active courses: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to retrieve active courses using a managed connection.
     *
     * @return list of active courses
     */
    public List<Course> findAllActive() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAllActive(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves courses belonging to a department.
     *
     * @param conn database connection
     * @param departmentId department ID
     * @return list of courses in department
     */
    public List<Course> findByDepartment(Connection conn, int departmentId) {
        String sql = BASE_SELECT + "WHERE c.department_id = ? ORDER BY c.course_code";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, departmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Course> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find courses by department " + departmentId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find courses by department using a managed connection.
     *
     * @param departmentId department ID
     * @return list of courses in department
     */
    public List<Course> findByDepartment(int departmentId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByDepartment(conn, departmentId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves courses for a specific semester.
     *
     * @param conn database connection
     * @param semester semester name/number
     * @return list of courses in semester
     */
    public List<Course> findBySemester(Connection conn, String semester) {
        String sql = BASE_SELECT + "WHERE c.semester = ? ORDER BY c.course_code";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, semester);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Course> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find courses by semester " + semester + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find courses by semester using a managed connection.
     *
     * @param semester semester name/number
     * @return list of courses in semester
     */
    public List<Course> findBySemester(String semester) {
        try (Connection conn = DBConnection.getConnection()) {
            return findBySemester(conn, semester);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Updates an existing course's details.
     * Automatically sets {@code updated_at = CURRENT_TIMESTAMP}.
     *
     * @param conn database connection
     * @param course course entity with updated values
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean update(Connection conn, Course course) {
        String sql = "UPDATE courses SET department_id = ?, course_code = ?, name = ?, " +
                     "                  credits = ?, semester = ?, max_capacity = ?, active = ?, " +
                     "                  updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, course.getDepartmentId());
            pstmt.setString(2, course.getCourseCode());
            pstmt.setString(3, course.getName());
            pstmt.setInt(4, course.getCredits());
            pstmt.setString(5, course.getSemester());
            if (course.getMaxCapacity() != null) {
                pstmt.setInt(6, course.getMaxCapacity());
            } else {
                pstmt.setNull(6, Types.INTEGER);
            }
            pstmt.setInt(7, course.isActive() ? 1 : 0);
            pstmt.setInt(8, course.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update course ID " + course.getId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to update a course using a managed connection.
     *
     * @param course course entity with updated values
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean update(Course course) {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, course);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Sets the active status of a course (soft delete / reactivate).
     *
     * @param conn database connection
     * @param id course record ID
     * @param active new active state
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean setActive(Connection conn, int id, boolean active) {
        String sql = "UPDATE courses SET active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, active ? 1 : 0);
            pstmt.setInt(2, id);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to set active status for course ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to set course active status using a managed connection.
     *
     * @param id course record ID
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
     * Maps the current row of a ResultSet to a Course domain model.
     *
     * @param rs result set positioned at a valid row
     * @return populated Course model
     * @throws SQLException if column reading fails
     */
    private Course mapRow(ResultSet rs) throws SQLException {
        Course c = new Course();
        c.setId(rs.getInt("id"));
        c.setDepartmentId(rs.getInt("department_id"));
        c.setCourseCode(rs.getString("course_code"));
        c.setName(rs.getString("name"));
        c.setCredits(rs.getInt("credits"));
        c.setSemester(rs.getString("semester"));
        Object maxCap = rs.getObject("max_capacity");
        c.setMaxCapacity(maxCap != null ? rs.getInt("max_capacity") : null);
        c.setActive(rs.getInt("active") == 1);
        c.setCreatedAt(rs.getString("created_at"));
        c.setUpdatedAt(rs.getString("updated_at"));
        c.setDepartmentName(rs.getString("department_name"));
        return c;
    }
}

