package com.college.dao;

import com.college.TestDBHelper;
import com.college.exception.DataAccessException;
import com.college.model.Department;
import com.college.model.FeeStructure;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [T11] Persistence & integer paise precision tests for {@link FeeStructureDAO}.
 */
public class FeeStructureDAOTest {

    private static FeeStructureDAO feeStructureDAO;
    private static DepartmentDAO departmentDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        feeStructureDAO = new FeeStructureDAO();
        departmentDAO = new DepartmentDAO();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    private Department createTestDept(Connection conn, String code) {
        return departmentDAO.insert(conn, new Department(code, "Dept " + code));
    }

    @Test
    @DisplayName("Verify fee structure creation and exact integer paise retrieval")
    void testInsertAndFind() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = createTestDept(conn, "CS");

            // ₹ 25,000.50 = 2500050 paise
            FeeStructure fs = new FeeStructure(dept.getId(), "1", "TUITION", 2500050L, "2026-2027", "2026-09-30");
            FeeStructure inserted = feeStructureDAO.insert(conn, fs);
            assertTrue(inserted.getId() > 0);

            FeeStructure found = feeStructureDAO.findById(inserted.getId());
            assertNotNull(found);
            assertEquals(dept.getId(), found.getDepartmentId());
            assertEquals("1", found.getSemester());
            assertEquals("TUITION", found.getFeeType());
            assertEquals(2500050L, found.getAmountPaise(), "Exact integer paise amount must be preserved");
            assertEquals("2026-2027", found.getAcademicYear());
            assertEquals("2026-09-30", found.getDueDate());
            assertTrue(found.isActive());
        }
    }

    @Test
    @DisplayName("Verify unique combination constraint (department_id, semester, fee_type, academic_year)")
    void testUniqueCombinationConstraint() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = createTestDept(conn, "EC");

            FeeStructure fs1 = new FeeStructure(dept.getId(), "1", "LAB_FEE", 500000L, "2026-2027", "2026-09-30");
            feeStructureDAO.insert(conn, fs1);

            // Duplicate combination must fail
            FeeStructure dup = new FeeStructure(dept.getId(), "1", "LAB_FEE", 600000L, "2026-2027", "2026-10-31");
            assertThrows(DataAccessException.class, () -> feeStructureDAO.insert(conn, dup));
        }
    }

    @Test
    @DisplayName("Verify amount_paise CHECK constraint rejects non-positive fee amounts (<= 0)")
    void testNonPositiveAmountRejected() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = createTestDept(conn, "ME");

            FeeStructure zero = new FeeStructure(dept.getId(), "1", "TUITION", 0L, "2026-2027", "2026-09-30");
            assertThrows(DataAccessException.class, () -> feeStructureDAO.insert(conn, zero));

            FeeStructure neg = new FeeStructure(dept.getId(), "2", "TUITION", -50000L, "2026-2027", "2026-09-30");
            assertThrows(DataAccessException.class, () -> feeStructureDAO.insert(conn, neg));
        }
    }

    @Test
    @DisplayName("Verify updating fee structure due date and amount")
    void testUpdateAndDeactivate() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = createTestDept(conn, "CE");
            FeeStructure fs = feeStructureDAO.insert(conn, new FeeStructure(dept.getId(), "1", "LIBRARY", 200000L, "2026-2027", "2026-09-30"));

            fs.setDueDate("2026-10-15");
            fs.setAmountPaise(250000L);
            boolean updated = feeStructureDAO.update(conn, fs);
            assertTrue(updated);

            FeeStructure reloaded = feeStructureDAO.findById(fs.getId());
            assertEquals("2026-10-15", reloaded.getDueDate());
            assertEquals(250000L, reloaded.getAmountPaise());

            // Deactivate
            feeStructureDAO.setActive(conn, fs.getId(), false);
            assertFalse(feeStructureDAO.findById(fs.getId()).isActive());
        }
    }
}
