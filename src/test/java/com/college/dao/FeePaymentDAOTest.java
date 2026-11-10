package com.college.dao;

import com.college.TestDBHelper;
import com.college.exception.DataAccessException;
import com.college.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [T12] Payment & refund persistence, column-by-column trigger immutability & FK tests
 * for {@link FeePaymentDAO} in accordance with Table 7.1 of implementation-plan.md v5.6.
 */
public class FeePaymentDAOTest {

    private static FeePaymentDAO feePaymentDAO;
    private static FeeStructureDAO feeStructureDAO;
    private static StudentDAO studentDAO;
    private static DepartmentDAO departmentDAO;
    private static UserDAO userDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        feePaymentDAO = new FeePaymentDAO();
        feeStructureDAO = new FeeStructureDAO();
        studentDAO = new StudentDAO();
        departmentDAO = new DepartmentDAO();
        userDAO = new UserDAO();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    private Student createTestStudent(Connection conn, String roll) {
        User u = userDAO.insert(conn, new User("u_" + roll, "$2a$12$hash", Role.STUDENT));
        Department d = departmentDAO.insert(conn, new Department("D_" + roll, "Dept " + roll));
        return studentDAO.insert(conn, new Student(u.getId(), d.getId(), roll, "First", "Last", roll + "@col.edu", "555", "2004-01-01", "2026-08-01", "1"));
    }

    private FeeStructure createTestFeeStructure(Connection conn, int deptId) {
        return feeStructureDAO.insert(conn, new FeeStructure(deptId, "1", "TUITION", 2500000L, "2026-2027", "2026-09-30"));
    }

    private FeePayment createOriginalPayment(Connection conn, int studentId, int feeStructureId, long amountPaise) {
        FeePayment p = new FeePayment();
        p.setStudentId(studentId);
        p.setFeeStructureId(feeStructureId);
        p.setAmountPaidPaise(amountPaise);
        p.setPaymentDate("2026-09-15");
        p.setPaymentMethod("ONLINE");
        p.setTransactionRef("TXN-" + System.nanoTime());
        p.setStatus("COMPLETED");
        return feePaymentDAO.insert(conn, p);
    }

    @Test
    @DisplayName("Verify fee payment insertion, retrieval by ID, and transaction reference uniqueness")
    void testInsertAndFind() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Student s = createTestStudent(conn, "STU_PAY1");
            FeeStructure fs = createTestFeeStructure(conn, s.getDepartmentId());

            FeePayment p = createOriginalPayment(conn, s.getId(), fs.getId(), 1500000L);
            assertTrue(p.getId() > 0);

            FeePayment found = feePaymentDAO.findById(p.getId());
            assertNotNull(found);
            assertEquals(s.getId(), found.getStudentId());
            assertEquals(fs.getId(), found.getFeeStructureId());
            assertEquals(1500000L, found.getAmountPaidPaise());
            assertEquals("COMPLETED", found.getStatus());
            assertNull(found.getOriginalPaymentId());

            // Duplicate transaction reference rejection
            FeePayment dupRef = new FeePayment(s.getId(), fs.getId(), 500000L, "2026-09-16", "CASH", p.getTransactionRef());
            assertThrows(DataAccessException.class, () -> feePaymentDAO.insert(conn, dupRef));
        }
    }

    // =========================================================================
    // Table 7.1 Column-by-Column Trigger Tests (trg_fee_payments_immutable_update)
    // =========================================================================

    @Test
    @DisplayName("1. Trigger blocks updating id on existing fee payment")
    void testTriggerBlocksUpdatingId() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Student s = createTestStudent(conn, "STU_T1");
            FeeStructure fs = createTestFeeStructure(conn, s.getDepartmentId());
            FeePayment p = createOriginalPayment(conn, s.getId(), fs.getId(), 100000L);

            try (PreparedStatement ps = conn.prepareStatement("UPDATE fee_payments SET id = 9999 WHERE id = ?")) {
                ps.setInt(1, p.getId());
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate);
                assertTrue(ex.getMessage().contains("Cannot update id on existing fee payment: field is immutable"));
            }
        }
    }

    @Test
    @DisplayName("2. Trigger blocks updating student_id on existing fee payment")
    void testTriggerBlocksUpdatingStudentId() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Student s1 = createTestStudent(conn, "STU_T2A");
            Student s2 = createTestStudent(conn, "STU_T2B");
            FeeStructure fs = createTestFeeStructure(conn, s1.getDepartmentId());
            FeePayment p = createOriginalPayment(conn, s1.getId(), fs.getId(), 100000L);

            try (PreparedStatement ps = conn.prepareStatement("UPDATE fee_payments SET student_id = ? WHERE id = ?")) {
                ps.setInt(1, s2.getId());
                ps.setInt(2, p.getId());
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate);
                assertTrue(ex.getMessage().contains("Cannot update student_id on existing fee payment: field is immutable"));
            }
        }
    }

    @Test
    @DisplayName("3. Trigger blocks updating fee_structure_id on existing fee payment")
    void testTriggerBlocksUpdatingFeeStructureId() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Student s = createTestStudent(conn, "STU_T3");
            FeeStructure fs1 = createTestFeeStructure(conn, s.getDepartmentId());
            FeeStructure fs2 = feeStructureDAO.insert(conn, new FeeStructure(s.getDepartmentId(), "2", "LAB", 500000L, "2026-2027", "2026-10-31"));
            FeePayment p = createOriginalPayment(conn, s.getId(), fs1.getId(), 100000L);

            try (PreparedStatement ps = conn.prepareStatement("UPDATE fee_payments SET fee_structure_id = ? WHERE id = ?")) {
                ps.setInt(1, fs2.getId());
                ps.setInt(2, p.getId());
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate);
                assertTrue(ex.getMessage().contains("Cannot update fee_structure_id on existing fee payment: field is immutable"));
            }
        }
    }

    @Test
    @DisplayName("4. Trigger blocks updating amount_paid_paise on existing fee payment")
    void testTriggerBlocksUpdatingAmountPaidPaise() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Student s = createTestStudent(conn, "STU_T4");
            FeeStructure fs = createTestFeeStructure(conn, s.getDepartmentId());
            FeePayment p = createOriginalPayment(conn, s.getId(), fs.getId(), 100000L);

            try (PreparedStatement ps = conn.prepareStatement("UPDATE fee_payments SET amount_paid_paise = ? WHERE id = ?")) {
                ps.setLong(1, 200000L);
                ps.setInt(2, p.getId());
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate);
                assertTrue(ex.getMessage().contains("Cannot update amount_paid_paise on existing fee payment: field is immutable"));
            }
        }
    }

    @Test
    @DisplayName("5. Trigger blocks updating payment_date on existing fee payment")
    void testTriggerBlocksUpdatingPaymentDate() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Student s = createTestStudent(conn, "STU_T5");
            FeeStructure fs = createTestFeeStructure(conn, s.getDepartmentId());
            FeePayment p = createOriginalPayment(conn, s.getId(), fs.getId(), 100000L);

            try (PreparedStatement ps = conn.prepareStatement("UPDATE fee_payments SET payment_date = '2025-01-01' WHERE id = ?")) {
                ps.setInt(1, p.getId());
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate);
                assertTrue(ex.getMessage().contains("Cannot update payment_date on existing fee payment: field is immutable"));
            }
        }
    }

    @Test
    @DisplayName("6. Trigger blocks updating payment_method on existing fee payment")
    void testTriggerBlocksUpdatingPaymentMethod() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Student s = createTestStudent(conn, "STU_T6");
            FeeStructure fs = createTestFeeStructure(conn, s.getDepartmentId());
            FeePayment p = createOriginalPayment(conn, s.getId(), fs.getId(), 100000L);

            try (PreparedStatement ps = conn.prepareStatement("UPDATE fee_payments SET payment_method = 'CHEQUE' WHERE id = ?")) {
                ps.setInt(1, p.getId());
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate);
                assertTrue(ex.getMessage().contains("Cannot update payment_method on existing fee payment: field is immutable"));
            }
        }
    }

    @Test
    @DisplayName("7. Trigger blocks updating transaction_ref on existing fee payment")
    void testTriggerBlocksUpdatingTransactionRef() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Student s = createTestStudent(conn, "STU_T7");
            FeeStructure fs = createTestFeeStructure(conn, s.getDepartmentId());
            FeePayment p = createOriginalPayment(conn, s.getId(), fs.getId(), 100000L);

            try (PreparedStatement ps = conn.prepareStatement("UPDATE fee_payments SET transaction_ref = 'NEW-REF-123' WHERE id = ?")) {
                ps.setInt(1, p.getId());
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate);
                assertTrue(ex.getMessage().contains("Cannot update transaction_ref on existing fee payment: field is immutable"));
            }
        }
    }

    @Test
    @DisplayName("8. Trigger blocks updating original_payment_id on existing fee payment")
    void testTriggerBlocksUpdatingOriginalPaymentId() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Student s = createTestStudent(conn, "STU_T8");
            FeeStructure fs = createTestFeeStructure(conn, s.getDepartmentId());
            FeePayment p = createOriginalPayment(conn, s.getId(), fs.getId(), 100000L);

            try (PreparedStatement ps = conn.prepareStatement("UPDATE fee_payments SET original_payment_id = 999 WHERE id = ?")) {
                ps.setInt(1, p.getId());
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate);
                assertTrue(ex.getMessage().contains("Cannot modify original_payment_id relationship: field is immutable"));
            }
        }
    }

    @Test
    @DisplayName("9. Trigger blocks updating created_at on existing fee payment")
    void testTriggerBlocksUpdatingCreatedAt() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Student s = createTestStudent(conn, "STU_T9");
            FeeStructure fs = createTestFeeStructure(conn, s.getDepartmentId());
            FeePayment p = createOriginalPayment(conn, s.getId(), fs.getId(), 100000L);

            try (PreparedStatement ps = conn.prepareStatement("UPDATE fee_payments SET created_at = '2020-01-01 00:00:00' WHERE id = ?")) {
                ps.setInt(1, p.getId());
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate);
                assertTrue(ex.getMessage().contains("Cannot update created_at on existing fee payment: field is immutable"));
            }
        }
    }

    @Test
    @DisplayName("10. Trigger blocks any update on refund record (REFUNDED status)")
    void testTriggerBlocksAnyUpdateOnRefundRecord() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Student s = createTestStudent(conn, "STU_T10");
            FeeStructure fs = createTestFeeStructure(conn, s.getDepartmentId());
            FeePayment original = createOriginalPayment(conn, s.getId(), fs.getId(), 1000000L);

            // Create refund
            FeePayment refund = new FeePayment();
            refund.setStudentId(s.getId());
            refund.setFeeStructureId(fs.getId());
            refund.setAmountPaidPaise(300000L);
            refund.setPaymentDate("2026-09-20");
            refund.setPaymentMethod("ONLINE");
            refund.setTransactionRef("REF-T10-" + System.nanoTime());
            refund.setStatus("REFUNDED");
            refund.setOriginalPaymentId(original.getId());
            feePaymentDAO.insert(conn, refund);

            // Attempt update on refund row
            try (PreparedStatement ps = conn.prepareStatement("UPDATE fee_payments SET status = 'COMPLETED' WHERE id = ?")) {
                ps.setInt(1, refund.getId());
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate);
                assertTrue(ex.getMessage().contains("Refund transaction records are completely immutable across all columns"));
            }
        }
    }

    @Test
    @DisplayName("11. Trigger blocks status change on original payment when refunds exist")
    void testTriggerBlocksStatusChangeWhenRefundExists() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Student s = createTestStudent(conn, "STU_T11");
            FeeStructure fs = createTestFeeStructure(conn, s.getDepartmentId());
            FeePayment original = createOriginalPayment(conn, s.getId(), fs.getId(), 1000000L);

            // Create refund against original
            FeePayment refund = new FeePayment();
            refund.setStudentId(s.getId());
            refund.setFeeStructureId(fs.getId());
            refund.setAmountPaidPaise(200000L);
            refund.setPaymentDate("2026-09-20");
            refund.setPaymentMethod("ONLINE");
            refund.setTransactionRef("REF-T11-" + System.nanoTime());
            refund.setStatus("REFUNDED");
            refund.setOriginalPaymentId(original.getId());
            feePaymentDAO.insert(conn, refund);

            // Attempt to alter status of original payment
            try (PreparedStatement ps = conn.prepareStatement("UPDATE fee_payments SET status = 'FAILED' WHERE id = ?")) {
                ps.setInt(1, original.getId());
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate);
                assertTrue(ex.getMessage().contains("Cannot alter status of a fee payment that has associated refunds"));
            }
        }
    }

    @Test
    @DisplayName("12. Trigger allows valid status transitions before refunds (PENDING -> COMPLETED, PENDING -> FAILED)")
    void testTriggerAllowsValidStatusTransitionsBeforeRefunds() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Student s = createTestStudent(conn, "STU_T12");
            FeeStructure fs = createTestFeeStructure(conn, s.getDepartmentId());

            // Create PENDING payment
            FeePayment p1 = new FeePayment(s.getId(), fs.getId(), 500000L, "2026-09-15", "ONLINE", "TXN-P1-" + System.nanoTime());
            p1.setStatus("PENDING");
            feePaymentDAO.insert(conn, p1);

            // Valid transition: PENDING -> COMPLETED
            try (PreparedStatement ps = conn.prepareStatement("UPDATE fee_payments SET status = 'COMPLETED' WHERE id = ?")) {
                ps.setInt(1, p1.getId());
                assertEquals(1, ps.executeUpdate(), "PENDING -> COMPLETED transition must succeed");
            }

            // Create another PENDING payment
            FeePayment p2 = new FeePayment(s.getId(), fs.getId(), 500000L, "2026-09-15", "ONLINE", "TXN-P2-" + System.nanoTime());
            p2.setStatus("PENDING");
            feePaymentDAO.insert(conn, p2);

            // Valid transition: PENDING -> FAILED
            try (PreparedStatement ps = conn.prepareStatement("UPDATE fee_payments SET status = 'FAILED' WHERE id = ?")) {
                ps.setInt(1, p2.getId());
                assertEquals(1, ps.executeUpdate(), "PENDING -> FAILED transition must succeed");
            }
        }
    }

    @Test
    @DisplayName("13. Trigger blocks invalid status transitions before refunds (COMPLETED -> FAILED, COMPLETED -> PENDING)")
    void testTriggerBlocksInvalidStatusTransitionsBeforeRefunds() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Student s = createTestStudent(conn, "STU_T13");
            FeeStructure fs = createTestFeeStructure(conn, s.getDepartmentId());
            FeePayment completed = createOriginalPayment(conn, s.getId(), fs.getId(), 500000L);

            // COMPLETED -> PENDING rejected
            try (PreparedStatement ps = conn.prepareStatement("UPDATE fee_payments SET status = 'PENDING' WHERE id = ?")) {
                ps.setInt(1, completed.getId());
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate);
                assertTrue(ex.getMessage().contains("Invalid payment status transition"));
            }

            // COMPLETED -> FAILED rejected
            try (PreparedStatement ps = conn.prepareStatement("UPDATE fee_payments SET status = 'FAILED' WHERE id = ?")) {
                ps.setInt(1, completed.getId());
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate);
                assertTrue(ex.getMessage().contains("Invalid payment status transition"));
            }
        }
    }

    @Test
    @DisplayName("14. Foreign key blocks delete of original payment that has associated child refunds")
    void testForeignKeyBlocksDeleteOfPaymentWithRefunds() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Student s = createTestStudent(conn, "STU_T14");
            FeeStructure fs = createTestFeeStructure(conn, s.getDepartmentId());
            FeePayment original = createOriginalPayment(conn, s.getId(), fs.getId(), 1000000L);

            FeePayment refund = new FeePayment();
            refund.setStudentId(s.getId());
            refund.setFeeStructureId(fs.getId());
            refund.setAmountPaidPaise(250000L);
            refund.setPaymentDate("2026-09-20");
            refund.setPaymentMethod("ONLINE");
            refund.setTransactionRef("REF-T14-" + System.nanoTime());
            refund.setStatus("REFUNDED");
            refund.setOriginalPaymentId(original.getId());
            feePaymentDAO.insert(conn, refund);

            // Attempt deleting parent payment with child refund
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM fee_payments WHERE id = ?")) {
                ps.setInt(1, original.getId());
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate);
                assertTrue(ex.getMessage().toLowerCase().contains("foreign key"));
            }
        }
    }

    @Test
    @DisplayName("15. SQLite integer sum overflow on completed payments aborts query execution")
    void testSqliteIntegerSumOverflowThrowsException() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Student s = createTestStudent(conn, "STU_T15");
            FeeStructure fs = createTestFeeStructure(conn, s.getDepartmentId());

            // Directly insert payments summing > Long.MAX_VALUE into DB
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(String.format(
                        "INSERT INTO fee_payments (student_id, fee_structure_id, amount_paid_paise, payment_date, payment_method, transaction_ref, status) " +
                                "VALUES (%d, %d, %d, '2026-09-01', 'ONLINE', 'OVF-1', 'COMPLETED')",
                        s.getId(), fs.getId(), Long.MAX_VALUE - 100
                ));
                stmt.executeUpdate(String.format(
                        "INSERT INTO fee_payments (student_id, fee_structure_id, amount_paid_paise, payment_date, payment_method, transaction_ref, status) " +
                                "VALUES (%d, %d, 200, '2026-09-02', 'ONLINE', 'OVF-2', 'COMPLETED')",
                        s.getId(), fs.getId()
                ));
            }

            // Querying sum must throw DataAccessException wrapping SQLITE_ERROR integer overflow
            assertThrows(DataAccessException.class, () -> feePaymentDAO.getTotalCompletedPaise(conn, s.getId(), fs.getId()));
        }
    }

    @Test
    @DisplayName("16. SQLite integer sum overflow on refunds aborts query execution")
    void testSqliteIntegerSumRefundOverflowThrowsException() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Student s = createTestStudent(conn, "STU_T16");
            FeeStructure fs = createTestFeeStructure(conn, s.getDepartmentId());
            FeePayment orig = createOriginalPayment(conn, s.getId(), fs.getId(), 5000000L);

            // Directly insert refund rows summing > Long.MAX_VALUE
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(String.format(
                        "INSERT INTO fee_payments (student_id, fee_structure_id, amount_paid_paise, payment_date, payment_method, transaction_ref, status, original_payment_id) " +
                                "VALUES (%d, %d, %d, '2026-09-20', 'ONLINE', 'REF-OVF-1', 'REFUNDED', %d)",
                        s.getId(), fs.getId(), Long.MAX_VALUE - 100, orig.getId()
                ));
                stmt.executeUpdate(String.format(
                        "INSERT INTO fee_payments (student_id, fee_structure_id, amount_paid_paise, payment_date, payment_method, transaction_ref, status, original_payment_id) " +
                                "VALUES (%d, %d, 200, '2026-09-21', 'ONLINE', 'REF-OVF-2', 'REFUNDED', %d)",
                        s.getId(), fs.getId(), orig.getId()
                ));
            }

            assertThrows(DataAccessException.class, () -> feePaymentDAO.getTotalRefundedPaise(conn, s.getId(), fs.getId()));
        }
    }
}

