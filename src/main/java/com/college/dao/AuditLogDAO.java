package com.college.dao;

import com.college.config.DBConnection;
import com.college.exception.DataAccessException;
import com.college.model.AuditLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the {@code audit_log} table.
 *
 * <p>Implements an append-only audit trail logger. Database triggers
 * {@code trg_audit_log_no_update} and {@code trg_audit_log_no_delete} strictly prohibit
 * any modifications or deletions of audit records. Provides query overloads for
 * compliance auditing by user, entity type, entity ID, or timestamp ranges.</p>
 */
public class AuditLogDAO {

    private static final String BASE_SELECT =
        "SELECT al.id, al.user_id, al.action, al.entity_type, al.entity_id, " +
        "       al.old_value, al.new_value, al.timestamp, " +
        "       u.username " +
        "FROM audit_log al " +
        "LEFT JOIN users u ON al.user_id = u.id ";

    /**
     * Appends a new audit record to the log.
     *
     * @param conn database connection (must participate in caller's transaction)
     * @param log audit log entity to persist
     * @return persisted entity with generated ID and timestamp
     * @throws DataAccessException if insertion fails
     */
    public AuditLog insert(Connection conn, AuditLog log) {
        String sql = "INSERT INTO audit_log (user_id, action, entity_type, entity_id, old_value, new_value) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, log.getUserId());
            pstmt.setString(2, log.getAction());
            pstmt.setString(3, log.getEntityType());
            if (log.getEntityId() != null) {
                pstmt.setInt(4, log.getEntityId());
            } else {
                pstmt.setNull(4, Types.INTEGER);
            }
            pstmt.setString(5, log.getOldValue());
            pstmt.setString(6, log.getNewValue());

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    log.setId(rs.getInt(1));
                }
            }
            return log;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert audit log: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to append an audit log using a managed connection.
     *
     * @param log audit log entity to persist
     * @return persisted entity
     */
    public AuditLog insert(AuditLog log) {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, log);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds an audit log entry by primary key ID.
     *
     * @param conn database connection
     * @param id audit log ID
     * @return audit log entity or {@code null} if not found
     */
    public AuditLog findById(Connection conn, int id) {
        String sql = BASE_SELECT + "WHERE al.id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find audit log by ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find an audit log by ID using a managed connection.
     *
     * @param id audit log ID
     * @return audit log entity or {@code null} if not found
     */
    public AuditLog findById(int id) {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, id);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all audit log records ordered chronologically descending.
     *
     * @param conn database connection
     * @return list of audit log records
     */
    public List<AuditLog> findAll(Connection conn) {
        String sql = BASE_SELECT + "ORDER BY al.id DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<AuditLog> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve audit logs: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to retrieve all audit logs using a managed connection.
     *
     * @return list of all audit log records
     */
    public List<AuditLog> findAll() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAll(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves audit logs for a specific user ID.
     *
     * @param conn database connection
     * @param userId user account ID
     * @return list of audit log records
     */
    public List<AuditLog> findByUserId(Connection conn, int userId) {
        String sql = BASE_SELECT + "WHERE al.user_id = ? ORDER BY al.id DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<AuditLog> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find audit logs for user ID " + userId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find audit logs by user ID using a managed connection.
     *
     * @param userId user account ID
     * @return list of audit log records
     */
    public List<AuditLog> findByUserId(int userId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByUserId(conn, userId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves audit logs for a specific entity type (e.g., "STUDENT", "FEE_PAYMENT").
     *
     * @param conn database connection
     * @param entityType entity type string
     * @return list of audit log records
     */
    public List<AuditLog> findByEntityType(Connection conn, String entityType) {
        String sql = BASE_SELECT + "WHERE al.entity_type = ? ORDER BY al.id DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, entityType);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<AuditLog> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find audit logs for entity type '" + entityType + "': " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find audit logs by entity type using a managed connection.
     *
     * @param entityType entity type string
     * @return list of audit log records
     */
    public List<AuditLog> findByEntityType(String entityType) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByEntityType(conn, entityType);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves audit logs for a specific entity record by entity type and ID.
     *
     * @param conn database connection
     * @param entityType entity type string
     * @param entityId entity primary key ID
     * @return list of audit log records
     */
    public List<AuditLog> findByEntityTypeAndId(Connection conn, String entityType, int entityId) {
        String sql = BASE_SELECT + "WHERE al.entity_type = ? AND al.entity_id = ? ORDER BY al.id DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, entityType);
            pstmt.setInt(2, entityId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<AuditLog> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find audit logs for entity: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find audit logs by entity type and ID using a managed connection.
     *
     * @param entityType entity type
     * @param entityId entity ID
     * @return list of audit log records
     */
    public List<AuditLog> findByEntityTypeAndId(String entityType, int entityId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByEntityTypeAndId(conn, entityType, entityId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves audit logs recorded within an ISO timestamp range.
     *
     * @param conn database connection
     * @param startTimestamp inclusive start timestamp (e.g., "2026-01-01 00:00:00")
     * @param endTimestamp inclusive end timestamp (e.g., "2026-12-31 23:59:59")
     * @return list of audit log records
     */
    public List<AuditLog> findByDateRange(Connection conn, String startTimestamp, String endTimestamp) {
        String sql = BASE_SELECT + "WHERE al.timestamp >= ? AND al.timestamp <= ? ORDER BY al.id DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, startTimestamp);
            pstmt.setString(2, endTimestamp);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<AuditLog> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find audit logs by date range: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find audit logs by date range using a managed connection.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @return list of audit log records
     */
    public List<AuditLog> findByDateRange(String startTimestamp, String endTimestamp) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByDateRange(conn, startTimestamp, endTimestamp);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Maps the current row of a ResultSet to an AuditLog domain model.
     *
     * @param rs result set positioned at a valid row
     * @return populated AuditLog model
     * @throws SQLException if column reading fails
     */
    private AuditLog mapRow(ResultSet rs) throws SQLException {
        AuditLog log = new AuditLog();
        log.setId(rs.getInt("id"));
        log.setUserId(rs.getInt("user_id"));
        log.setAction(rs.getString("action"));
        log.setEntityType(rs.getString("entity_type"));
        Object entityId = rs.getObject("entity_id");
        log.setEntityId(entityId != null ? rs.getInt("entity_id") : null);
        log.setOldValue(rs.getString("old_value"));
        log.setNewValue(rs.getString("new_value"));
        log.setTimestamp(rs.getString("timestamp"));
        log.setUsername(rs.getString("username"));
        return log;
    }
}

