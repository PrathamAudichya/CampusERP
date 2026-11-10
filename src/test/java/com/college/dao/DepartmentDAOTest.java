package com.college.dao;

import com.college.TestDBHelper;
import com.college.exception.DataAccessException;
import com.college.model.Department;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [T4] Persistence & reactivation tests for {@link DepartmentDAO}.
 */
public class DepartmentDAOTest {

    private static DepartmentDAO departmentDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        departmentDAO = new DepartmentDAO();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    @Test
    @DisplayName("Verify department creation and lookup by ID and Code")
    void testInsertAndFind() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = new Department("CS", "Computer Science");
            Department inserted = departmentDAO.insert(conn, dept);

            assertTrue(inserted.getId() > 0);
            assertEquals("CS", inserted.getCode());

            Department byId = departmentDAO.findById(inserted.getId());
            assertNotNull(byId);
            assertEquals("Computer Science", byId.getName());

            Department byCode = departmentDAO.findByCode("CS");
            assertNotNull(byCode);
            assertEquals(inserted.getId(), byCode.getId());
        }
    }

    @Test
    @DisplayName("Verify duplicate department code is rejected by unique constraint")
    void testDuplicateCodeRejected() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            departmentDAO.insert(conn, new Department("EC", "Electronics"));
            assertThrows(DataAccessException.class, () -> departmentDAO.insert(conn, new Department("EC", "Electronics 2")));
        }
    }

    @Test
    @DisplayName("Verify department update modifies name while preserving code")
    void testUpdateDepartment() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("ME", "Mechanical"));
            dept.setName("Mechanical Engineering");

            boolean updated = departmentDAO.update(conn, dept);
            assertTrue(updated);

            Department reloaded = departmentDAO.findById(dept.getId());
            assertEquals("Mechanical Engineering", reloaded.getName());
            assertEquals("ME", reloaded.getCode());
        }
    }

    @Test
    @DisplayName("Verify soft deactivation and reactivation of department")
    void testDeactivationAndReactivation() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CIVIL", "Civil Engineering"));

            // Soft delete
            departmentDAO.setActive(conn, dept.getId(), false);
            assertFalse(departmentDAO.findById(dept.getId()).isActive());
            assertTrue(departmentDAO.findAllActive().isEmpty());
            assertEquals(1, departmentDAO.findAll().size());

            // Reactivate
            departmentDAO.setActive(conn, dept.getId(), true);
            assertTrue(departmentDAO.findById(dept.getId()).isActive());
            assertEquals(1, departmentDAO.findAllActive().size());
        }
    }
}
