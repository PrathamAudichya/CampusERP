package com.college.service;

import com.college.config.AppConfig;
import com.college.config.DBConnection;
import com.college.dao.UserDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.DataAccessException;
import com.college.exception.ValidationException;
import com.college.model.Role;
import com.college.model.User;
import com.college.model.UserDirectoryItem;
import com.college.util.PasswordUtil;
import com.college.util.ValidationUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * Service managing user authentication, sessions, role authorization, and credential changes.
 */
public class AuthService {

    private final UserDAO userDAO;
    private final AuditService auditService;

    // In-memory session tracking for active desktop session
    private User currentSessionUser;

    public AuthService() {
        this.userDAO = new UserDAO();
        this.auditService = new AuditService();
    }

    public AuthService(UserDAO userDAO, AuditService auditService) {
        this.userDAO = userDAO;
        this.auditService = auditService;
    }

    /**
     * Authenticates a user against stored BCrypt password hash.
     *
     * @param username username
     * @param password plaintext password
     * @return authenticated User object
     * @throws AuthenticationException if credentials are invalid or user is inactive
     */
    public User login(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new AuthenticationException("Invalid username or password.");
        }

        User user = userDAO.findByUsername(username.trim());
        if (user == null || !user.isActive()) {
            throw new AuthenticationException("Invalid username or password.");
        }

        boolean passwordMatches = PasswordUtil.verifyPassword(password, user.getPasswordHash());
        if (!passwordMatches) {
            throw new AuthenticationException("Invalid username or password.");
        }

        // Mask password hash for session safety
        User sessionUser = copyUserWithoutSensitiveHash(user);
        this.currentSessionUser = sessionUser;

        // Standalone audit log for successful login
        auditService.log(user.getId(), "LOGIN", "USER", user.getId(), null,
                "User logged in: " + user.getUsername() + " (Role: " + user.getRole() + ")");

        return sessionUser;
    }

    /**
     * Clears current session state on logout.
     */
    public void logout() {
        if (this.currentSessionUser != null) {
            auditService.log(this.currentSessionUser.getId(), "LOGOUT", "USER",
                    this.currentSessionUser.getId(), null,
                    "User logged out: " + this.currentSessionUser.getUsername());
            this.currentSessionUser = null;
        }
    }

    /**
     * Returns the currently authenticated session user, or null if none.
     */
    public User getCurrentSessionUser() {
        return this.currentSessionUser;
    }

    /**
     * Sets the current session user (used by desktop session context).
     */
    public void setCurrentSessionUser(User user) {
        this.currentSessionUser = user != null ? copyUserWithoutSensitiveHash(user) : null;
    }

    /**
     * Enforces Role-Based Access Control (RBAC).
     *
     * @param user         acting user
     * @param allowedRoles array of allowed roles
     * @throws AuthenticationException if user is null, inactive, or not authorized
     */
    public static void requireRole(User user, Role... allowedRoles) {
        if (user == null) {
            throw new AuthenticationException("Authentication required.");
        }
        if (!user.isActive()) {
            throw new AuthenticationException("User account is inactive.");
        }
        if (user.isMustChangePassword()) {
            throw new AuthenticationException("Password change required before accessing system functions.");
        }
        if (allowedRoles == null || allowedRoles.length == 0) {
            return;
        }
        for (Role role : allowedRoles) {
            if (user.getRole() == role) {
                return;
            }
        }
        throw new AuthenticationException("Access denied. Required role: " + Arrays.toString(allowedRoles)
                + ", Current role: " + user.getRole());
    }

    /**
     * Changes user password after verifying their existing password.
     *
     * @param currentUser     authenticated user
     * @param currentPassword existing plaintext password
     * @param newPassword     new plaintext password
     */
    public void changePassword(User currentUser, String currentPassword, String newPassword) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        ValidationUtil.requireNonEmpty(currentPassword, "Current password");
        validateNewPassword(newPassword);

        // Fetch authoritative record from DB to verify current password hash
        User dbUser = userDAO.findById(currentUser.getId());
        if (dbUser == null || !dbUser.isActive()) {
            throw new AuthenticationException("User account not found or inactive.");
        }

        if (!PasswordUtil.verifyPassword(currentPassword, dbUser.getPasswordHash())) {
            throw new ValidationException("Current password does not match.");
        }

        if (currentPassword.equals(newPassword)) {
            throw new ValidationException("New password cannot be identical to the current password.");
        }

        String newHash = PasswordUtil.hashPassword(newPassword);
        userDAO.updatePassword(currentUser.getId(), newHash, false);

        // Update session state
        currentUser.setMustChangePassword(false);
        if (this.currentSessionUser != null && this.currentSessionUser.getId() == currentUser.getId()) {
            this.currentSessionUser.setMustChangePassword(false);
        }

        auditService.log(currentUser.getId(), "CHANGE_PASSWORD", "USER", currentUser.getId(),
                null, "Password changed successfully for user: " + currentUser.getUsername());
    }

    /**
     * Handles mandatory password reset on first login (without requiring current password verification).
     *
     * @param currentUser authenticated user with mustChangePassword == true
     * @param newPassword new password
     */
    public void forceChangePassword(User currentUser, String newPassword) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        validateNewPassword(newPassword);

        User dbUser = userDAO.findById(currentUser.getId());
        if (dbUser == null || !dbUser.isActive()) {
            throw new AuthenticationException("User account not found or inactive.");
        }

        String newHash = PasswordUtil.hashPassword(newPassword);
        userDAO.updatePassword(currentUser.getId(), newHash, false);

        currentUser.setMustChangePassword(false);
        if (this.currentSessionUser != null && this.currentSessionUser.getId() == currentUser.getId()) {
            this.currentSessionUser.setMustChangePassword(false);
        }

        auditService.log(currentUser.getId(), "FORCE_CHANGE_PASSWORD", "USER", currentUser.getId(),
                null, "Initial forced password reset completed for user: " + currentUser.getUsername());
    }

    /**
     * Reactivates a deactivated user account (ADMIN only).
     */
    public void reactivateUser(User currentUser, int targetUserId) {
        requireRole(currentUser, Role.ADMIN);
        User target = userDAO.findById(targetUserId);
        if (target == null) {
            throw new ValidationException("User " + targetUserId + " not found.");
        }
        if (target.isActive()) {
            throw new ValidationException("User " + target.getUsername() + " is already active.");
        }
        userDAO.setActive(targetUserId, true);
        auditService.log(currentUser.getId(), "REACTIVATE_USER", "USER", targetUserId,
                "active=0", "active=1 (Username: " + target.getUsername() + ")");
    }

    /**
     * Deactivates an active user account (ADMIN only).
     */
    public void deactivateUser(User currentUser, int targetUserId) {
        requireRole(currentUser, Role.ADMIN);
        if (currentUser.getId() == targetUserId) {
            throw new ValidationException("Cannot deactivate your own administrator account.");
        }
        User target = userDAO.findById(targetUserId);
        if (target == null) {
            throw new ValidationException("User " + targetUserId + " not found.");
        }
        if (!target.isActive()) {
            throw new ValidationException("User " + target.getUsername() + " is already inactive.");
        }
        userDAO.setActive(targetUserId, false);
        auditService.log(currentUser.getId(), "DEACTIVATE_USER", "USER", targetUserId,
                "active=1", "active=0 (Username: " + target.getUsername() + ")");
    }

    /**
     * Retrieves the institutional user directory containing joined profiles for all users (ADMIN only).
     *
     * @param currentUser authenticated administrator
     * @return list of UserDirectoryItem records
     */
    public List<UserDirectoryItem> getUserDirectory(User currentUser) {
        requireRole(currentUser, Role.ADMIN);
        return userDAO.findAllUserDirectoryItems();
    }

    /**
     * Resets a user's password to an auto-generated or custom temporary password and flags
     * must_change_password=1 for mandatory update on next login (ADMIN only).
     *
     * @param currentUser        authenticated administrator
     * @param targetUserId       ID of user account to reset
     * @param customTempPassword optional custom password (if null or blank, a secure random one is generated)
     * @return the unhashed temporary password for one-time secure display
     */
    public String resetUserPassword(User currentUser, int targetUserId, String customTempPassword) {
        requireRole(currentUser, Role.ADMIN);
        User target = userDAO.findById(targetUserId);
        if (target == null) {
            throw new ValidationException("User ID " + targetUserId + " not found.");
        }
        if (!target.isActive()) {
            throw new ValidationException("Cannot reset password for inactive user " + target.getUsername() + ".");
        }

        String tempPassword = (customTempPassword != null && !customTempPassword.trim().isEmpty())
                ? customTempPassword.trim()
                : PasswordUtil.generateTemporaryPassword();

        if (tempPassword.length() < AppConfig.MIN_PASSWORD_LENGTH) {
            throw new ValidationException("Temporary password must be at least " + AppConfig.MIN_PASSWORD_LENGTH + " characters long.");
        }

        String hash = PasswordUtil.hashPassword(tempPassword);
        userDAO.updatePassword(targetUserId, hash, true);

        auditService.log(currentUser.getId(), "ADMIN_RESET_PASSWORD", "USER", targetUserId,
                null, "Admin reset temporary password for user: " + target.getUsername() + " (must_change_password=1)");

        return tempPassword;
    }

    /**
     * Forces a user to change their password on next login without resetting current password (ADMIN only).
     *
     * @param currentUser  authenticated administrator
     * @param targetUserId ID of user to flag
     * @return true if updated successfully
     */
    public boolean forcePasswordChange(User currentUser, int targetUserId) {
        requireRole(currentUser, Role.ADMIN);
        User target = userDAO.findById(targetUserId);
        if (target == null) {
            throw new ValidationException("User ID " + targetUserId + " not found.");
        }
        boolean success = userDAO.setMustChangePassword(targetUserId, true);
        auditService.log(currentUser.getId(), "FORCE_PASSWORD_CHANGE", "USER", targetUserId,
                "must_change_password=" + (target.isMustChangePassword() ? 1 : 0),
                "must_change_password=1 (Username: " + target.getUsername() + ")");
        return success;
    }

    /**
     * Updates an account's username with administrative authorization, uniqueness validation,
     * format verification, and audit trail logging.
     *
     * @param currentUser  authenticated administrator
     * @param targetUserId user ID to update
     * @param newUsername  new unique username
     * @return true if updated successfully
     */
    public boolean updateUsername(User currentUser, int targetUserId, String newUsername) {
        requireRole(currentUser, Role.ADMIN);
        ValidationUtil.requireNonEmpty(newUsername, "Username");
        String trimmed = newUsername.trim();
        if (trimmed.length() < 3 || trimmed.length() > 30) {
            throw new ValidationException("Username must be between 3 and 30 characters.");
        }

        User target = userDAO.findById(targetUserId);
        if (target == null) {
            throw new ValidationException("User ID " + targetUserId + " not found.");
        }

        if (target.getUsername().equalsIgnoreCase(trimmed)) {
            return true; // No change needed
        }

        User existing = userDAO.findByUsername(trimmed);
        if (existing != null && existing.getId() != targetUserId) {
            throw new ValidationException("Username '" + trimmed + "' is already assigned to another account.");
        }

        boolean updated = userDAO.updateUsername(targetUserId, trimmed);
        if (updated) {
            auditService.log(currentUser.getId(), "UPDATE_USERNAME", "USER", targetUserId,
                    target.getUsername(), trimmed);
        }
        return updated;
    }

    /**
     * Checks whether an active system administrator account exists.
     *
     * @return true if at least one active administrator exists; false otherwise
     */
    public boolean hasAdminUser() {
        return userDAO.countActiveAdmins() > 0;
    }

    /**
     * Provisions the initial system administrator account during first-run setup.
     * Enforces that no active administrator currently exists, validates username and password
     * complexity according to enterprise policy, securely hashes the password with BCrypt (factor 12),
     * and logs the action in the audit trail.
     *
     * @param username    the initial administrator username
     * @param rawPassword the initial administrator plaintext password
     * @return the newly created User with masked password hash
     * @throws IllegalStateException if an administrator account is already provisioned
     * @throws ValidationException   if username or password violates security policy
     */
    public synchronized User provisionInitialAdmin(String username, String rawPassword) {
        if (hasAdminUser()) {
            throw new IllegalStateException("Initial administrator account is already provisioned.");
        }
        ValidationUtil.requireNonEmpty(username, "Username");
        String trimmedUser = username.trim();
        if (trimmedUser.length() < 3 || trimmedUser.length() > 30) {
            throw new ValidationException("Username must be between 3 and 30 characters.");
        }
        ValidationUtil.validateStrongPassword(rawPassword);

        // Pre-compute BCrypt hash before database transaction to minimize lock hold time
        String passwordHash = PasswordUtil.hashPassword(rawPassword);

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (userDAO.countActiveAdmins(conn) > 0) {
                    throw new IllegalStateException("Initial administrator account is already provisioned.");
                }

                User admin = new User(trimmedUser, passwordHash, Role.ADMIN);
                admin.setMustChangePassword(false);
                admin.setActive(true);

                User saved = userDAO.insert(conn, admin);
                auditService.log(conn, saved.getId(), "INITIAL_ADMIN_PROVISIONED", "USER", saved.getId(),
                        null, "Initial administrator account provisioned: " + saved.getUsername());

                conn.commit();
                return copyUserWithoutSensitiveHash(saved);
            } catch (Throwable ex) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                if (ex instanceof IllegalStateException) {
                    throw (IllegalStateException) ex;
                } else if (ex instanceof ValidationException) {
                    throw (ValidationException) ex;
                } else if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                } else if (ex instanceof Error) {
                    throw (Error) ex;
                } else {
                    throw new DataAccessException("Failed to provision initial admin: " + ex.getMessage(), (SQLException) ex);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    private void validateNewPassword(String newPassword) {
        ValidationUtil.validateStrongPassword(newPassword);
    }

    private User copyUserWithoutSensitiveHash(User original) {
        User copy = new User(
                original.getId(),
                original.getUsername(),
                "[PROTECTED]",
                original.getRole(),
                original.isMustChangePassword(),
                original.isActive(),
                original.getCreatedAt(),
                original.getUpdatedAt()
        );
        return copy;
    }
}
