package com.college.service;

import com.college.dao.AuditLogDAO;
import com.college.exception.ValidationException;
import com.college.model.AuditLog;
import com.college.model.Role;
import com.college.model.User;
import com.college.util.ValidationUtil;

import java.sql.Connection;
import java.util.List;

/**
 * Service managing transactional and standalone system audit logging.
 *
 * <p>Security & Immutability Rules:
 * <ul>
 *   <li>Never includes passwords or unmasked password hashes in audit values.</li>
 *   <li>Uses the caller's Connection for atomic transactional logging where required.</li>
 *   <li>Only ADMIN users can read or query historical audit logs.</li>
 * </ul>
 * </p>
 */
public class AuditService {

    private final AuditLogDAO auditLogDAO;

    public AuditService() {
        this.auditLogDAO = new AuditLogDAO();
    }

    public AuditService(AuditLogDAO auditLogDAO) {
        this.auditLogDAO = auditLogDAO;
    }

    /**
     * Records an audit log event within an existing database transaction.
     *
     * @param conn       caller-managed database connection (not closed by this method)
     * @param userId     ID of the acting user
     * @param action     action name (e.g., "CREATE_STUDENT", "PROCESS_PAYMENT")
     * @param entityType entity type (e.g., "STUDENT", "FEE_PAYMENT")
     * @param entityId   optional entity ID
     * @param oldValue   optional previous state (passwords masked)
     * @param newValue   optional new state (passwords masked)
     * @return persisted AuditLog
     */
    public AuditLog log(Connection conn, int userId, String action, String entityType,
                        Integer entityId, String oldValue, String newValue) {
        ValidationUtil.requireNonEmpty(action, "Audit action");
        ValidationUtil.requireNonEmpty(entityType, "Audit entity type");

        String safeOldValue = sanitizeSensitiveData(oldValue);
        String safeNewValue = sanitizeSensitiveData(newValue);

        AuditLog auditLog = new AuditLog(userId, action, entityType, entityId, safeOldValue, safeNewValue);
        return auditLogDAO.insert(conn, auditLog);
    }

    /**
     * Records an audit log event using a standalone managed connection.
     *
     * @param userId     ID of the acting user
     * @param action     action name
     * @param entityType entity type
     * @param entityId   optional entity ID
     * @param oldValue   optional previous state
     * @param newValue   optional new state
     * @return persisted AuditLog
     */
    public AuditLog log(int userId, String action, String entityType,
                        Integer entityId, String oldValue, String newValue) {
        ValidationUtil.requireNonEmpty(action, "Audit action");
        ValidationUtil.requireNonEmpty(entityType, "Audit entity type");

        String safeOldValue = sanitizeSensitiveData(oldValue);
        String safeNewValue = sanitizeSensitiveData(newValue);

        AuditLog auditLog = new AuditLog(userId, action, entityType, entityId, safeOldValue, safeNewValue);
        return auditLogDAO.insert(auditLog);
    }

    /**
     * Retrieves all historical audit logs (ADMIN only).
     */
    public List<AuditLog> getAllAuditLogs(User currentUser) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        return auditLogDAO.findAll();
    }

    /**
     * Retrieves audit logs for a specific user ID (ADMIN only).
     */
    public List<AuditLog> getAuditLogsByUserId(User currentUser, int targetUserId) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        return auditLogDAO.findByUserId(targetUserId);
    }

    /**
     * Retrieves audit logs for a specific entity type (ADMIN only).
     */
    public List<AuditLog> getAuditLogsByEntityType(User currentUser, String entityType) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        ValidationUtil.requireNonEmpty(entityType, "Entity type");
        return auditLogDAO.findByEntityType(entityType);
    }

    /**
     * Retrieves audit logs within an ISO date/timestamp range (ADMIN only).
     */
    public List<AuditLog> getAuditLogsByDateRange(User currentUser, String startTimestamp, String endTimestamp) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        ValidationUtil.requireNonEmpty(startTimestamp, "Start timestamp");
        ValidationUtil.requireNonEmpty(endTimestamp, "End timestamp");
        return auditLogDAO.findByDateRange(startTimestamp, endTimestamp);
    }

    /**
     * Ensures sensitive fields like password hashes or tokens are never persisted in audit logs.
     */
    private String sanitizeSensitiveData(String value) {
        if (value == null) {
            return null;
        }
        // Mask bcrypt hashes ($2a$, $2b$, $2y$) if accidentally passed
        return value.replaceAll("\\$2[aby]\\$\\d{2}\\$[A-Za-z0-9./]{53}", "[PROTECTED]");
    }
}

