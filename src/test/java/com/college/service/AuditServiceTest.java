package com.college.service;

import com.college.TestDBHelper;
import com.college.dao.AuditLogDAO;
import com.college.dao.UserDAO;
import com.college.model.AuditLog;
import com.college.model.Role;
import com.college.model.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [T22] Transactional audit logging & database trigger immutability tests for {@link AuditService}.
 */
public class AuditServiceTest {

    private static AuditService auditService;
    private static UserDAO userDAO;
    private static AuditLogDAO auditLogDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        auditService = new AuditService();
        userDAO = new UserDAO();
        auditLogDAO = new AuditLogDAO();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    @Test
    @DisplayName("Verify standalone and transactional audit logging")
    void testAuditLogging() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = userDAO.insert(conn, new User("admin_audit", "$2a$12$hash", Role.ADMIN));

            // 1. Transactional logging
            auditService.log(conn, admin.getId(), "CREATE", "STUDENT", 101, null, "New Student Created");

            // 2. Standalone logging
            auditService.log(admin.getId(), "UPDATE", "STUDENT", 101, "Old Phone", "New Phone");

            List<AuditLog> logs = auditLogDAO.findAll();
            assertEquals(2, logs.size());
            assertEquals("UPDATE", logs.get(0).getAction(), "Audit logs are sorted newest-first (DESC)");
            assertEquals("CREATE", logs.get(1).getAction());
        }
    }

    @Test
    @DisplayName("Verify User.toAuditString() excludes sensitive password hashes from audit entries")
    void testPasswordExclusionInAudit() {
        User user = new User("secure_user", "$2a$12$VERY_SECRET_BCRYPT_HASH", Role.FACULTY);
        String auditStr = user.toAuditString();

        assertNotNull(auditStr);
        assertTrue(auditStr.contains("secure_user"));
        assertTrue(auditStr.contains("FACULTY"));
        assertFalse(auditStr.contains("VERY_SECRET_BCRYPT_HASH"), "Password hash must NEVER appear in audit strings");
        assertFalse(auditStr.contains("password"), "Password field key must be sanitized or excluded");
    }

    @Test
    @DisplayName("Verify database trigger trg_audit_log_no_update blocks all updates on audit_log")
    void testTriggerBlocksAuditUpdate() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = userDAO.insert(conn, new User("admin_aud_t1", "$2a$12$hash", Role.ADMIN));
            auditService.log(conn, admin.getId(), "LOGIN", "AUTH", admin.getId(), null, "User Logged In");

            try (PreparedStatement ps = conn.prepareStatement("UPDATE audit_log SET action = 'TAMPERED' WHERE user_id = ?")) {
                ps.setInt(1, admin.getId());
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate);
                assertTrue(ex.getMessage().contains("Updates to audit_log are prohibited"));
            }
        }
    }

    @Test
    @DisplayName("Verify database trigger trg_audit_log_no_delete blocks deletions on audit_log")
    void testTriggerBlocksAuditDelete() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = userDAO.insert(conn, new User("admin_aud_t2", "$2a$12$hash", Role.ADMIN));
            auditService.log(conn, admin.getId(), "LOGOUT", "AUTH", admin.getId(), null, "User Logged Out");

            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM audit_log WHERE user_id = ?")) {
                ps.setInt(1, admin.getId());
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate);
                assertTrue(ex.getMessage().contains("Deletions from audit_log are prohibited"));
            }
        }
    }
}
