package com.college.dao;

import com.college.TestDBHelper;
import com.college.exception.DataAccessException;
import com.college.model.Role;
import com.college.model.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [T3] Persistence tests for {@link UserDAO}.
 */
public class UserDAOTest {

    private static UserDAO userDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        userDAO = new UserDAO();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    @Test
    @DisplayName("Verify user insertion and retrieval by generated ID")
    void testInsertAndFindById() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User user = new User("testuser", "$2a$12$somehash123", Role.STUDENT);
            user.setMustChangePassword(true);
            user.setActive(true);

            User inserted = userDAO.insert(conn, user);
            assertTrue(inserted.getId() > 0, "Generated ID must be positive");

            User found = userDAO.findById(inserted.getId());
            assertNotNull(found);
            assertEquals("testuser", found.getUsername());
            assertEquals("$2a$12$somehash123", found.getPasswordHash());
            assertEquals(Role.STUDENT, found.getRole());
            assertTrue(found.isMustChangePassword());
            assertTrue(found.isActive());
        }
    }

    @Test
    @DisplayName("Verify user retrieval by username and null on non-existent username")
    void testFindByUsername() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User user = new User("admin_test", "$2a$12$adminhash", Role.ADMIN);
            userDAO.insert(conn, user);

            User found = userDAO.findByUsername("admin_test");
            assertNotNull(found);
            assertEquals(Role.ADMIN, found.getRole());

            User notFound = userDAO.findByUsername("does_not_exist");
            assertNull(notFound);
        }
    }

    @Test
    @DisplayName("Verify duplicate username insertion is rejected by unique constraint")
    void testDuplicateUsernameRejected() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User user1 = new User("unique_user", "$2a$12$hash1", Role.STUDENT);
            userDAO.insert(conn, user1);

            User user2 = new User("unique_user", "$2a$12$hash2", Role.FACULTY);
            assertThrows(DataAccessException.class, () -> userDAO.insert(conn, user2));
        }
    }

    @Test
    @DisplayName("Verify updating password hash and must_change_password flag")
    void testUpdatePasswordAndFlag() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User user = new User("change_user", "$2a$12$oldhash", Role.FACULTY);
            user.setMustChangePassword(true);
            userDAO.insert(conn, user);

            // Update password and flag in one operation
            boolean passUpdated = userDAO.updatePassword(conn, user.getId(), "$2a$12$newhash", false);
            assertTrue(passUpdated);

            User reloaded = userDAO.findById(conn, user.getId());
            assertNotNull(reloaded);
            assertEquals("$2a$12$newhash", reloaded.getPasswordHash());
            assertFalse(reloaded.isMustChangePassword());
        }
    }

    @Test
    @DisplayName("Verify active user filtering and soft deactivation")
    void testActiveFilteringAndSoftDeactivation() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User u1 = userDAO.insert(conn, new User("user1", "hash1", Role.STUDENT));
            User u2 = userDAO.insert(conn, new User("user2", "hash2", Role.FACULTY));

            // Soft deactivation of u2
            userDAO.setActive(conn, u2.getId(), false);

            User reloadedU1 = userDAO.findById(conn, u1.getId());
            assertTrue(reloadedU1.isActive());

            User reloadedU2 = userDAO.findById(conn, u2.getId());
            assertFalse(reloadedU2.isActive());

            List<User> allUsers = userDAO.findAll(conn);
            assertEquals(2, allUsers.size());
        }
    }
}
