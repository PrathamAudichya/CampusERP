package com.college.model;

/**
 * Domain entity representing a system user account.
 * Maps to the {@code users} table in the database schema.
 */
public class User {

    private int id;
    private String username;
    private String passwordHash;
    private Role role;
    private boolean mustChangePassword;
    private boolean active = true;
    private String createdAt;
    private String updatedAt;

    /**
     * Default no-argument constructor.
     */
    public User() {
    }

    /**
     * Convenience constructor for creating a new user account.
     *
     * @param username     the unique login username
     * @param passwordHash the BCrypt password hash
     * @param role         the user role (ADMIN, FACULTY, STUDENT)
     */
    public User(String username, String passwordHash, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    /**
     * Full constructor mapping all database columns.
     *
     * @param id                 internal user ID
     * @param username           unique login username
     * @param passwordHash       BCrypt password hash
     * @param role               user role
     * @param mustChangePassword whether user must change password upon next login
     * @param active             soft deletion flag
     * @param createdAt          record creation timestamp
     * @param updatedAt          record update timestamp
     */
    public User(int id, String username, String passwordHash, Role role,
                boolean mustChangePassword, boolean active, String createdAt, String updatedAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.mustChangePassword = mustChangePassword;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    /**
     * Convenience setter for string-based role assignment.
     *
     * @param roleStr role name (ADMIN, FACULTY, STUDENT)
     */
    public void setRole(String roleStr) {
        if (roleStr != null && !roleStr.isBlank()) {
            this.role = Role.valueOf(roleStr.trim().toUpperCase());
        } else {
            this.role = null;
        }
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public boolean getMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public boolean isActive() {
        return active;
    }

    public boolean getActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Serializes user state for audit logging.
     * Explicitly excludes the {@code passwordHash} to prevent credential exposure.
     *
     * @return audit-safe string representation
     */
    public String toAuditString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", role=" + role +
                ", mustChangePassword=" + mustChangePassword +
                ", active=" + active +
                ", createdAt='" + createdAt + '\'' +
                ", updatedAt='" + updatedAt + '\'' +
                '}';
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", passwordHash='[PROTECTED]'" +
                ", role=" + role +
                ", mustChangePassword=" + mustChangePassword +
                ", active=" + active +
                ", createdAt='" + createdAt + '\'' +
                ", updatedAt='" + updatedAt + '\'' +
                '}';
    }
}

