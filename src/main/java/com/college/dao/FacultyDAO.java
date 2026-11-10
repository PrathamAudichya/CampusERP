package com.college.dao;

import com.college.config.DBConnection;
import com.college.exception.DataAccessException;
import com.college.model.Faculty;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the {@code faculty} table.
 *
 * <p>Handles faculty profile persistence, queries by ID, faculty ID, or user ID,
 * department joins for transient display properties, and active state management.</p>
 */
public class FacultyDAO {

    private static final String BASE_SELECT =
        "SELECT f.id, f.user_id, f.department_id, f.faculty_id, f.first_name, f.last_name, " +
        "       f.email, f.phone, f.specialization, f.join_date, f.active, " +
        "       f.created_at, f.updated_at, d.name AS department_name, d.school AS school_name " +
        "FROM faculty f " +
        "LEFT JOIN departments d ON f.department_id = d.id ";

    /**
     * Inserts a new faculty member.
     *
     * @param conn database connection
     * @param faculty faculty entity to insert
     * @return the persisted faculty with generated ID
     * @throws DataAccessException if a database error occurs
     */
    public Faculty insert(Connection conn, Faculty faculty) {
        String sql = "INSERT INTO faculty (user_id, department_id, faculty_id, first_name, last_name, " +
                     "                    email, phone, specialization, join_date, active) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, faculty.getUserId());
            pstmt.setInt(2, faculty.getDepartmentId());
            pstmt.setString(3, faculty.getFacultyId());
            pstmt.setString(4, faculty.getFirstName());
            pstmt.setString(5, faculty.getLastName());
            pstmt.setString(6, faculty.getEmail());
            pstmt.setString(7, faculty.getPhone());
            pstmt.setString(8, faculty.getSpecialization());
            pstmt.setString(9, faculty.getJoinDate());
            pstmt.setInt(10, faculty.isActive() ? 1 : 0);

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    faculty.setId(rs.getInt(1));
                }
            }
            return faculty;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert faculty '" + faculty.getFacultyId() + "': " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to insert a faculty member using a managed connection.
     *
     * @param faculty faculty entity to insert
     * @return the persisted faculty
     */
    public Faculty insert(Faculty faculty) {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, faculty);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a faculty member by primary key ID, populating departmentName.
     *
     * @param conn database connection
     * @param id faculty record ID
     * @return faculty entity or {@code null} if not found
     */
    public Faculty findById(Connection conn, int id) {
        String sql = BASE_SELECT + "WHERE f.id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find faculty by ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find a faculty member by ID using a managed connection.
     *
     * @param id faculty record ID
     * @return faculty entity or {@code null} if not found
     */
    public Faculty findById(int id) {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, id);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a faculty member by unique faculty ID (e.g., "FAC2026001").
     *
     * @param conn database connection
     * @param facultyId unique faculty ID string
     * @return faculty entity or {@code null} if not found
     */
    public Faculty findByFacultyId(Connection conn, String facultyId) {
        String sql = BASE_SELECT + "WHERE f.faculty_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, facultyId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find faculty by facultyId '" + facultyId + "': " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find a faculty member by facultyId using a managed connection.
     *
     * @param facultyId unique faculty ID string
     * @return faculty entity or {@code null} if not found
     */
    public Faculty findByFacultyId(String facultyId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByFacultyId(conn, facultyId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a faculty member by associated user ID.
     *
     * @param conn database connection
     * @param userId user account ID
     * @return faculty entity or {@code null} if not found
     */
    public Faculty findByUserId(Connection conn, int userId) {
        String sql = BASE_SELECT + "WHERE f.user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find faculty by userId " + userId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find a faculty member by user ID using a managed connection.
     *
     * @param userId user account ID
     * @return faculty entity or {@code null} if not found
     */
    public Faculty findByUserId(int userId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByUserId(conn, userId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a faculty member by email address.
     *
     * @param conn database connection
     * @param email faculty email
     * @return faculty entity or {@code null} if not found
     */
    public Faculty findByEmail(Connection conn, String email) {
        String sql = BASE_SELECT + "WHERE f.email = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find faculty by email '" + email + "': " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find a faculty member by email using a managed connection.
     *
     * @param email faculty email
     * @return faculty entity or {@code null} if not found
     */
    public Faculty findByEmail(String email) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByEmail(conn, email);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all faculty members.
     *
     * @param conn database connection
     * @return list of all faculty members
     */
    public List<Faculty> findAll(Connection conn) {
        String sql = BASE_SELECT + "ORDER BY f.id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<Faculty> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve all faculty members: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to retrieve all faculty members using a managed connection.
     *
     * @return list of all faculty members
     */
    public List<Faculty> findAll() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAll(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all active faculty members.
     *
     * @param conn database connection
     * @return list of active faculty members
     */
    public List<Faculty> findAllActive(Connection conn) {
        String sql = BASE_SELECT + "WHERE f.active = 1 ORDER BY f.id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<Faculty> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve active faculty members: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to retrieve active faculty members using a managed connection.
     *
     * @return list of active faculty members
     */
    public List<Faculty> findAllActive() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAllActive(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves faculty members by department ID.
     *
     * @param conn database connection
     * @param departmentId department ID
     * @return list of faculty members in the department
     */
    public List<Faculty> findByDepartment(Connection conn, int departmentId) {
        String sql = BASE_SELECT + "WHERE f.department_id = ? ORDER BY f.faculty_id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, departmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Faculty> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find faculty by department " + departmentId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find faculty by department using a managed connection.
     *
     * @param departmentId department ID
     * @return list of faculty members in the department
     */
    public List<Faculty> findByDepartment(int departmentId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByDepartment(conn, departmentId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Updates an existing faculty member's profile.
     * Automatically sets {@code updated_at = CURRENT_TIMESTAMP}.
     *
     * @param conn database connection
     * @param faculty faculty entity with updated fields
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean update(Connection conn, Faculty faculty) {
        String sql = "UPDATE faculty SET department_id = ?, first_name = ?, last_name = ?, " +
                     "                  email = ?, phone = ?, specialization = ?, join_date = ?, " +
                     "                  active = ?, updated_at = CURRENT_TIMESTAMP " +
                     "WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, faculty.getDepartmentId());
            pstmt.setString(2, faculty.getFirstName());
            pstmt.setString(3, faculty.getLastName());
            pstmt.setString(4, faculty.getEmail());
            pstmt.setString(5, faculty.getPhone());
            pstmt.setString(6, faculty.getSpecialization());
            pstmt.setString(7, faculty.getJoinDate());
            pstmt.setInt(8, faculty.isActive() ? 1 : 0);
            pstmt.setInt(9, faculty.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update faculty ID " + faculty.getId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to update a faculty member using a managed connection.
     *
     * @param faculty faculty entity with updated fields
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean update(Faculty faculty) {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, faculty);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Sets the active status of a faculty member (soft delete / reactivate).
     *
     * @param conn database connection
     * @param id faculty record ID
     * @param active new active state
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean setActive(Connection conn, int id, boolean active) {
        String sql = "UPDATE faculty SET active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, active ? 1 : 0);
            pstmt.setInt(2, id);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to set active status for faculty ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to set faculty active status using a managed connection.
     *
     * @param id faculty record ID
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
     * Maps the current row of a ResultSet to a Faculty domain model.
     *
     * @param rs result set positioned at a valid row
     * @return populated Faculty model
     * @throws SQLException if column reading fails
     */
    private Faculty mapRow(ResultSet rs) throws SQLException {
        Faculty f = new Faculty();
        f.setId(rs.getInt("id"));
        f.setUserId(rs.getInt("user_id"));
        f.setDepartmentId(rs.getInt("department_id"));
        f.setFacultyId(rs.getString("faculty_id"));
        f.setFirstName(rs.getString("first_name"));
        f.setLastName(rs.getString("last_name"));
        f.setEmail(rs.getString("email"));
        f.setPhone(rs.getString("phone"));
        f.setSpecialization(rs.getString("specialization"));
        f.setJoinDate(rs.getString("join_date"));
        f.setActive(rs.getInt("active") == 1);
        f.setCreatedAt(rs.getString("created_at"));
        f.setUpdatedAt(rs.getString("updated_at"));
        f.setDepartmentName(rs.getString("department_name"));
        try {
            f.setSchoolName(rs.getString("school_name"));
        } catch (SQLException ignored) {}
        return f;
    }
}

