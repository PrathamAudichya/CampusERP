package com.college.dao;

import com.college.config.DBConnection;
import com.college.exception.DataAccessException;
import com.college.model.Role;
import com.college.model.User;
import com.college.model.UserDirectoryItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the {@code users} table.
 *
 * <p>Handles persistence, credential queries, role filtering, and password updates
 * for system authentication and user accounts.</p>
 */
public class UserDAO {

    /**
     * Inserts a new user record.
     *
     * @param conn database connection
     * @param user user entity to insert
     * @return the persisted user with generated ID and timestamps
     * @throws DataAccessException if a database error occurs
     */
    public User insert(Connection conn, User user) {
        String sql = "INSERT INTO users (username, password_hash, role, must_change_password, active) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPasswordHash());
            pstmt.setString(3, user.getRole().name());
            pstmt.setInt(4, user.isMustChangePassword() ? 1 : 0);
            pstmt.setInt(5, user.isActive() ? 1 : 0);

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    user.setId(rs.getInt(1));
                }
            }
            return user;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert user '" + user.getUsername() + "': " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to insert a new user using a managed connection.
     *
     * @param user user entity to insert
     * @return the persisted user
     */
    public User insert(User user) {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, user);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a user by primary key ID.
     *
     * @param conn database connection
     * @param id user ID
     * @return user entity or {@code null} if not found
     */
    public User findById(Connection conn, int id) {
        String sql = "SELECT id, username, password_hash, role, must_change_password, active, created_at, updated_at " +
                     "FROM users WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find user by ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find a user by ID using a managed connection.
     *
     * @param id user ID
     * @return user entity or {@code null} if not found
     */
    public User findById(int id) {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, id);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a user by unique username.
     *
     * @param conn database connection
     * @param username username to look up
     * @return user entity or {@code null} if not found
     */
    public User findByUsername(Connection conn, String username) {
        String sql = "SELECT id, username, password_hash, role, must_change_password, active, created_at, updated_at " +
                     "FROM users WHERE username = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find user by username '" + username + "': " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find a user by username using a managed connection.
     *
     * @param username username to look up
     * @return user entity or {@code null} if not found
     */
    public User findByUsername(String username) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByUsername(conn, username);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all users ordered by ID.
     *
     * @param conn database connection
     * @return list of all users
     */
    public List<User> findAll(Connection conn) {
        String sql = "SELECT id, username, password_hash, role, must_change_password, active, created_at, updated_at " +
                     "FROM users ORDER BY id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<User> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve all users: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to retrieve all users using a managed connection.
     *
     * @return list of all users
     */
    public List<User> findAll() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAll(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all users having the specified role.
     *
     * @param conn database connection
     * @param role role filter
     * @return list of users matching the role
     */
    public List<User> findByRole(Connection conn, Role role) {
        String sql = "SELECT id, username, password_hash, role, must_change_password, active, created_at, updated_at " +
                     "FROM users WHERE role = ? ORDER BY id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, role.name());
            try (ResultSet rs = pstmt.executeQuery()) {
                List<User> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find users by role " + role + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to retrieve users by role using a managed connection.
     *
     * @param role role filter
     * @return list of users matching the role
     */
    public List<User> findByRole(Role role) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByRole(conn, role);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all user accounts joined with their associated student or faculty profile
     * and department details for the Institutional User Directory.
     * Guaranteed 0 duplicate rows due to UNIQUE 1:1 user_id constraints.
     *
     * @param conn database connection
     * @return list of UserDirectoryItem DTOs ordered by user ID
     */
    public List<UserDirectoryItem> findAllUserDirectoryItems(Connection conn) {
        String sql = "SELECT " +
                     "    u.id AS user_id, " +
                     "    u.username, " +
                     "    u.role, " +
                     "    u.must_change_password, " +
                     "    u.active AS user_active, " +
                     "    u.created_at AS user_created_at, " +
                     "    u.updated_at AS user_updated_at, " +
                     "    COALESCE(s.student_id, f.faculty_id) AS natural_id, " +
                     "    CASE " +
                     "        WHEN u.role = 'STUDENT' THEN (s.first_name || ' ' || s.last_name) " +
                     "        WHEN u.role = 'FACULTY' THEN (f.first_name || ' ' || f.last_name) " +
                     "        ELSE 'System Administrator' " +
                     "    END AS full_name, " +
                     "    COALESCE(s.email, f.email) AS email, " +
                     "    COALESCE(s.phone, f.phone) AS phone, " +
                     "    COALESCE(ds.code, df.code) AS department_code, " +
                     "    COALESCE(ds.name, df.name) AS department_name, " +
                     "    s.semester, " +
                     "    f.specialization, " +
                     "    1 AS account_created " +
                     "FROM users u " +
                     "LEFT JOIN students s ON s.user_id = u.id " +
                     "LEFT JOIN departments ds ON ds.id = s.department_id " +
                     "LEFT JOIN faculty f ON f.user_id = u.id " +
                     "LEFT JOIN departments df ON df.id = f.department_id " +
                     "UNION ALL " +
                     "SELECT " +
                     "    0 AS user_id, " +
                     "    '—' AS username, " +
                     "    'STUDENT' AS role, " +
                     "    0 AS must_change_password, " +
                     "    s.active AS user_active, " +
                     "    s.created_at AS user_created_at, " +
                     "    s.updated_at AS user_updated_at, " +
                     "    s.student_id AS natural_id, " +
                     "    (s.first_name || ' ' || s.last_name) AS full_name, " +
                     "    s.email, " +
                     "    s.phone, " +
                     "    ds.code AS department_code, " +
                     "    ds.name AS department_name, " +
                     "    s.semester, " +
                     "    NULL AS specialization, " +
                     "    0 AS account_created " +
                     "FROM students s " +
                     "LEFT JOIN departments ds ON ds.id = s.department_id " +
                     "WHERE s.user_id IS NULL " +
                     "UNION ALL " +
                     "SELECT " +
                     "    0 AS user_id, " +
                     "    '—' AS username, " +
                     "    'FACULTY' AS role, " +
                     "    0 AS must_change_password, " +
                     "    f.active AS user_active, " +
                     "    f.created_at AS user_created_at, " +
                     "    f.updated_at AS user_updated_at, " +
                     "    f.faculty_id AS natural_id, " +
                     "    (f.first_name || ' ' || f.last_name) AS full_name, " +
                     "    f.email, " +
                     "    f.phone, " +
                     "    df.code AS department_code, " +
                     "    df.name AS department_name, " +
                     "    NULL AS semester, " +
                     "    f.specialization, " +
                     "    0 AS account_created " +
                     "FROM faculty f " +
                     "LEFT JOIN departments df ON df.id = f.department_id " +
                     "WHERE f.user_id IS NULL " +
                     "ORDER BY user_id ASC, natural_id ASC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<UserDirectoryItem> list = new ArrayList<>();
            while (rs.next()) {
                UserDirectoryItem item = new UserDirectoryItem();
                item.setUserId(rs.getInt("user_id"));
                item.setUsername(rs.getString("username"));
                item.setRole(Role.valueOf(rs.getString("role")));
                item.setMustChangePassword(rs.getInt("must_change_password") == 1);
                item.setActive(rs.getInt("user_active") == 1);
                item.setCreatedAt(rs.getString("user_created_at"));
                item.setUpdatedAt(rs.getString("user_updated_at"));
                item.setNaturalId(rs.getString("natural_id"));
                item.setFullName(rs.getString("full_name"));
                item.setEmail(rs.getString("email"));
                item.setPhone(rs.getString("phone"));
                item.setDepartmentCode(rs.getString("department_code"));
                item.setDepartmentName(rs.getString("department_name"));
                item.setSemester(rs.getString("semester"));
                item.setSpecialization(rs.getString("specialization"));
                item.setAccountCreated(rs.getInt("account_created") == 1);
                list.add(item);
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve user directory items: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to retrieve all user directory items using a managed connection.
     *
     * @return list of UserDirectoryItem DTOs
     */
    public List<UserDirectoryItem> findAllUserDirectoryItems() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAllUserDirectoryItems(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Updates user details (password_hash, role, must_change_password, active).
     * Automatically touches {@code updated_at = CURRENT_TIMESTAMP}.
     *
     * @param conn database connection
     * @param user user entity containing updated values
     * @return {@code true} if a record was updated; {@code false} otherwise
     */
    public boolean update(Connection conn, User user) {
        String sql = "UPDATE users SET password_hash = ?, role = ?, must_change_password = ?, " +
                     "active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user.getPasswordHash());
            pstmt.setString(2, user.getRole().name());
            pstmt.setInt(3, user.isMustChangePassword() ? 1 : 0);
            pstmt.setInt(4, user.isActive() ? 1 : 0);
            pstmt.setInt(5, user.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update user ID " + user.getId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to update a user using a managed connection.
     *
     * @param user user entity containing updated values
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean update(User user) {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, user);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Updates the password hash and must_change_password flag for a user.
     *
     * @param conn database connection
     * @param id user ID
     * @param newPasswordHash new BCrypt password hash
     * @param mustChangePassword whether the user must change password on next login
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean updatePassword(Connection conn, int id, String newPasswordHash, boolean mustChangePassword) {
        String sql = "UPDATE users SET password_hash = ?, must_change_password = ?, updated_at = CURRENT_TIMESTAMP " +
                     "WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newPasswordHash);
            pstmt.setInt(2, mustChangePassword ? 1 : 0);
            pstmt.setInt(3, id);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update password for user ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to update password using a managed connection.
     *
     * @param id user ID
     * @param newPasswordHash new BCrypt password hash
     * @param mustChangePassword whether user must change password
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean updatePassword(int id, String newPasswordHash, boolean mustChangePassword) {
        try (Connection conn = DBConnection.getConnection()) {
            return updatePassword(conn, id, newPasswordHash, mustChangePassword);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Updates the username for a user by primary key ID.
     *
     * @param conn database connection
     * @param id user ID
     * @param newUsername new unique username
     * @return true if updated; false otherwise
     */
    public boolean updateUsername(Connection conn, int id, String newUsername) {
        String sql = "UPDATE users SET username = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newUsername);
            pstmt.setInt(2, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update username for user ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to update username using a managed connection.
     *
     * @param id user ID
     * @param newUsername new unique username
     * @return true if updated; false otherwise
     */
    public boolean updateUsername(int id, String newUsername) {
        try (Connection conn = DBConnection.getConnection()) {
            return updateUsername(conn, id, newUsername);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Sets the must_change_password flag for a user.
     *
     * @param conn database connection
     * @param id user ID
     * @param mustChange whether user must change password on next login
     * @return true if updated; false otherwise
     */
    public boolean setMustChangePassword(Connection conn, int id, boolean mustChange) {
        String sql = "UPDATE users SET must_change_password = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, mustChange ? 1 : 0);
            pstmt.setInt(2, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to set must_change_password for user ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to set must_change_password using a managed connection.
     *
     * @param id user ID
     * @param mustChange whether user must change password on next login
     * @return true if updated; false otherwise
     */
    public boolean setMustChangePassword(int id, boolean mustChange) {
        try (Connection conn = DBConnection.getConnection()) {
            return setMustChangePassword(conn, id, mustChange);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Sets the active status of a user (soft delete / reactivate).
     *
     * @param conn database connection
     * @param id user ID
     * @param active new active state
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean setActive(Connection conn, int id, boolean active) {
        String sql = "UPDATE users SET active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, active ? 1 : 0);
            pstmt.setInt(2, id);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to set active status for user ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to set user active status using a managed connection.
     *
     * @param id user ID
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
     * Counts the total number of active users with the ADMIN role.
     *
     * @param conn database connection
     * @return count of active administrators
     * @throws DataAccessException if a database error occurs
     */
    public int countActiveAdmins(Connection conn) {
        String sql = "SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND active = 1";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count active administrators: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to count active administrators using a managed connection.
     *
     * @return count of active administrators
     * @throws DataAccessException if a database error occurs
     */
    public int countActiveAdmins() {
        try (Connection conn = DBConnection.getConnection()) {
            return countActiveAdmins(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Maps the current row of a ResultSet to a User domain model.
     *
     * @param rs result set positioned at a valid row
     * @return populated User model
     * @throws SQLException if column reading fails
     */
    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setRole(Role.valueOf(rs.getString("role")));
        user.setMustChangePassword(rs.getInt("must_change_password") == 1);
        user.setActive(rs.getInt("active") == 1);
        user.setCreatedAt(rs.getString("created_at"));
        user.setUpdatedAt(rs.getString("updated_at"));
        return user;
    }
}

