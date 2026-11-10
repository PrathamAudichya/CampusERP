package com.college.model;

/**
 * Domain entity representing an immutable system audit trail entry.
 * Maps to the {@code audit_log} table in the database schema.
 *
 * <p>Security rule: Serialized {@code oldValue} and {@code newValue} states
 * must never include raw password hashes or unhashed credentials.</p>
 */
public class AuditLog {

    private int id;
    private int userId;
    private String action;
    private String entityType;
    private Integer entityId;
    private String oldValue;
    private String newValue;
    private String timestamp;

    // Transient display property populated from users join
    private String username;

    /**
     * Default no-argument constructor.
     */
    public AuditLog() {
    }

    /**
     * Convenience constructor for recording an audit log event.
     *
     * @param userId     actor user ID
     * @param action     operation (e.g., "CREATE", "UPDATE", "DELETE", "REFUND")
     * @param entityType target entity type (e.g., "STUDENT", "FEE_PAYMENT")
     * @param entityId   primary key of target entity (can be null)
     * @param oldValue   serialized prior state (passwords excluded)
     * @param newValue   serialized new state (passwords excluded)
     */
    public AuditLog(int userId, String action, String entityType, Integer entityId,
                    String oldValue, String newValue) {
        this.userId = userId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    /**
     * Full constructor mapping all database columns.
     */
    public AuditLog(int id, int userId, String action, String entityType, Integer entityId,
                    String oldValue, String newValue, String timestamp) {
        this.id = id;
        this.userId = userId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.timestamp = timestamp;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Integer getEntityId() {
        return entityId;
    }

    public void setEntityId(Integer entityId) {
        this.entityId = entityId;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String toString() {
        return "AuditLog{" +
                "id=" + id +
                ", userId=" + userId +
                ", action='" + action + '\'' +
                ", entityType='" + entityType + '\'' +
                ", entityId=" + entityId +
                ", oldValue='" + oldValue + '\'' +
                ", newValue='" + newValue + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", username='" + username + '\'' +
                '}';
    }
}

