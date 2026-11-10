package com.college.config;

import com.college.TestDBHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [T2] Configuration & connection tests for {@link DBConnection}.
 */
public class DBConnectionTest {

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    @Test
    @DisplayName("Verify connection is successfully opened to the configured database")
    void testConnectionOpen() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            assertNotNull(conn);
            assertFalse(conn.isClosed());
        }
    }

    @Test
    @DisplayName("Verify PRAGMA foreign_keys is enabled (1) by default on connections")
    void testForeignKeyPragmaEnforced() throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "SQLite foreign_keys PRAGMA must be ON (1)");
        }
    }

    @Test
    @DisplayName("Verify independent connections to shared-memory URL share table state")
    void testSharedMemoryDatabaseSharing() throws SQLException {
        // Connection 1 inserts a test department
        try (Connection conn1 = TestDBHelper.getTestConnection();
             Statement stmt1 = conn1.createStatement()) {
            stmt1.executeUpdate("INSERT INTO departments (id, code, name, active) VALUES (101, 'TEST_SHARED', 'Shared Dept', 1)");
        }

        // Connection 2 reads the inserted row
        try (Connection conn2 = TestDBHelper.getTestConnection();
             Statement stmt2 = conn2.createStatement();
             ResultSet rs = stmt2.executeQuery("SELECT name FROM departments WHERE code = 'TEST_SHARED'")) {
            assertTrue(rs.next(), "Connection 2 must observe row inserted by Connection 1 in shared memory");
            assertEquals("Shared Dept", rs.getString("name"));
        }
    }

    @Test
    @DisplayName("Verify ensureDirectoryExists handles in-memory and blank paths without throwing")
    void testEnsureDirectoryExistsSafe() {
        assertDoesNotThrow(() -> DBConnection.ensureDirectoryExists(":memory:"));
        assertDoesNotThrow(() -> DBConnection.ensureDirectoryExists(""));
        assertDoesNotThrow(() -> DBConnection.ensureDirectoryExists(null));
        assertDoesNotThrow(() -> DBConnection.ensureDirectoryExists(TestDBHelper.TEST_DB_URL));
    }

    @Test
    @DisplayName("Verify extractPathFromUri correctly processes SQLite URI variants")
    void testExtractPathFromUri() {
        assertEquals(":memory:", DBConnection.extractPathFromUri(":memory:"));
        assertEquals(":memory:", DBConnection.extractPathFromUri("jdbc:sqlite::memory:"));
        assertEquals(":memory:", DBConnection.extractPathFromUri("jdbc:sqlite:file:testdb?mode=memory&cache=shared"));
        assertEquals("data/college.db", DBConnection.extractPathFromUri("data/college.db"));
        assertEquals("data/college.db", DBConnection.extractPathFromUri("jdbc:sqlite:data/college.db"));

        // Unsupported scheme throws IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> DBConnection.extractPathFromUri("http://example.com/db.db"));
        assertThrows(IllegalArgumentException.class, () -> DBConnection.extractPathFromUri("ftp://server/db.db"));
    }

    @Test
    @DisplayName("Verify decodePercent complies with RFC 3986 URI rules")
    void testDecodePercent() {
        assertEquals("hello world", DBConnection.decodePercent("hello%20world"));
        assertEquals("hello+world", DBConnection.decodePercent("hello+world"), "Literal plus must be preserved");
        assertEquals("hello+world", DBConnection.decodePercent("hello%2Bworld"));
        assertEquals("test", DBConnection.decodePercent("test"));
        assertEquals("", DBConnection.decodePercent(""));
        assertEquals("", DBConnection.decodePercent(null));

        // Malformed percent-encoding sequences throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> DBConnection.decodePercent("bad%"));
        assertThrows(IllegalArgumentException.class, () -> DBConnection.decodePercent("bad%1"));
        assertThrows(IllegalArgumentException.class, () -> DBConnection.decodePercent("bad%ZZ"));
    }
}

