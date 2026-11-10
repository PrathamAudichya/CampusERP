package com.college.service;

import com.college.TestDBHelper;
import com.college.dao.*;
import com.college.exception.AuthenticationException;
import com.college.exception.DataAccessException;
import com.college.exception.ValidationException;
import com.college.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [T19] Overflow-safe aggregates, Long.MAX_VALUE boundaries, eligibility, refunds,
 * and concurrent payment lock tests for {@link FeePaymentService} (Table 7.2 of implementation-plan.md v5.6).
 */
public class FeePaymentServiceTest {

    private static FeePaymentService feePaymentService;
    private static FeePaymentDAO feePaymentDAO;
    private static FeeStructureDAO feeStructureDAO;
    private static StudentDAO studentDAO;
    private static DepartmentDAO departmentDAO;
    private static UserDAO userDAO;
    private static AuditService auditService;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        feePaymentDAO = new FeePaymentDAO();
        feeStructureDAO = new FeeStructureDAO();
        studentDAO = new StudentDAO();
        departmentDAO = new DepartmentDAO();
        userDAO = new UserDAO();
        auditService = new AuditService();
        feePaymentService = new FeePaymentService(feePaymentDAO, feeStructureDAO, studentDAO, auditService);
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    private User createAdmin(Connection conn) {
        User u = new User("admin_fee", "$2a$12$hash", Role.ADMIN);
        u.setActive(true);
        return userDAO.insert(conn, u);
    }

    private Student createStudentWithDept(Connection conn, String roll, int deptId, String sem) {
        User u = userDAO.insert(conn, new User("u_" + roll, "$2a$12$hash", Role.STUDENT));
        return studentDAO.insert(conn, new Student(u.getId(), deptId, roll, "Fn", "Ln", roll + "@col.edu", "555", "2004-01-01", "2026-08-01", sem));
    }

    // =========================================================================
    // Table 7.2 Boundary & Arithmetic Overflow Safety Tests
    // =========================================================================

    @Test
    @DisplayName("1. Subtraction-first aggregate evaluation avoids intermediate overflow near Long.MAX_VALUE")
    void testFeeAggregatesNearLongMaxValueWithoutOverflow() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            FeeStructure fs = feeStructureDAO.insert(conn, new FeeStructure(dept.getId(), "1", "TUITION", 1000L, "2026-2027", "2026-09-30"));
            Student student = createStudentWithDept(conn, "STU_OVF_1", dept.getId(), "1");

            // Mock DAO returning aggregates near Long.MAX_VALUE:
            // totalPaid = Long.MAX_VALUE - 100, totalRefunded = Long.MAX_VALUE - 200
            // netPaid = 100, structure = 1000 -> outstanding = 900
            FeePaymentDAO mockDao = new FeePaymentDAO() {
                @Override
                public long getTotalCompletedPaise(Connection c, int sId, int fsId) {
                    return Long.MAX_VALUE - 100L;
                }
                @Override
                public long getTotalRefundedPaise(Connection c, int sId, int fsId) {
                    return Long.MAX_VALUE - 200L;
                }
                @Override
                public FeePayment insert(Connection c, FeePayment p) {
                    p.setId(777);
                    return p;
                }
            };

            FeePaymentService service = new FeePaymentService(mockDao, feeStructureDAO, studentDAO, auditService);

            // Paying 900L should succeed because outstanding = 900L
            FeePayment paid = service.processPayment(admin, student.getId(), fs.getId(), "2026-2027", 900L, "ONLINE");
            assertNotNull(paid);
            assertEquals(900L, paid.getAmountPaidPaise());
        }
    }

    @Test
    @DisplayName("2. Corrupted ledger state where net paid exceeds structure amount is rejected with ValidationException")
    void testFeeAggregateNetPaidExceedsStructureRejection() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            FeeStructure fs = feeStructureDAO.insert(conn, new FeeStructure(dept.getId(), "1", "TUITION", 2500000L, "2026-2027", "2026-09-30"));
            Student student = createStudentWithDept(conn, "STU_OVF_2", dept.getId(), "1");

            FeePaymentDAO mockDao = new FeePaymentDAO() {
                @Override
                public long getTotalCompletedPaise(Connection c, int sId, int fsId) {
                    return Long.MAX_VALUE - 50L;
                }
                @Override
                public long getTotalRefundedPaise(Connection c, int sId, int fsId) {
                    return 0L;
                }
            };

            FeePaymentService service = new FeePaymentService(mockDao, feeStructureDAO, studentDAO, auditService);

            ValidationException ex = assertThrows(ValidationException.class,
                    () -> service.processPayment(admin, student.getId(), fs.getId(), "2026-2027", 100L, "ONLINE"));
            assertTrue(ex.getMessage().contains("exceeds total fee structure amount"));
        }
    }

    @Test
    @DisplayName("3. Negative total completed payments aggregate is rejected with ValidationException")
    void testNegativeTotalPaidPaiseRejection() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            FeeStructure fs = feeStructureDAO.insert(conn, new FeeStructure(dept.getId(), "1", "TUITION", 2500000L, "2026-2027", "2026-09-30"));
            Student student = createStudentWithDept(conn, "STU_NEG_1", dept.getId(), "1");

            FeePaymentDAO mockDao = new FeePaymentDAO() {
                @Override
                public long getTotalCompletedPaise(Connection c, int sId, int fsId) {
                    return -1L;
                }
            };

            FeePaymentService service = new FeePaymentService(mockDao, feeStructureDAO, studentDAO, auditService);
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> service.processPayment(admin, student.getId(), fs.getId(), "2026-2027", 1000L, "ONLINE"));
            assertTrue(ex.getMessage().contains("cannot be negative"));
        }
    }

    @Test
    @DisplayName("4. Negative total refunds aggregate is rejected with ValidationException")
    void testNegativeTotalRefundedPaiseRejection() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            FeeStructure fs = feeStructureDAO.insert(conn, new FeeStructure(dept.getId(), "1", "TUITION", 2500000L, "2026-2027", "2026-09-30"));
            Student student = createStudentWithDept(conn, "STU_NEG_2", dept.getId(), "1");

            FeePaymentDAO mockDao = new FeePaymentDAO() {
                @Override
                public long getTotalCompletedPaise(Connection c, int sId, int fsId) {
                    return 5000L;
                }
                @Override
                public long getTotalRefundedPaise(Connection c, int sId, int fsId) {
                    return -100L;
                }
            };

            FeePaymentService service = new FeePaymentService(mockDao, feeStructureDAO, studentDAO, auditService);
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> service.processPayment(admin, student.getId(), fs.getId(), "2026-2027", 1000L, "ONLINE"));
            assertTrue(ex.getMessage().contains("cannot be negative"));
        }
    }

    @Test
    @DisplayName("5. Cumulative refunds exceeding total completed payments is rejected")
    void testCumulativeRefundsExceedingTotalPaidRejection() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            FeeStructure fs = feeStructureDAO.insert(conn, new FeeStructure(dept.getId(), "1", "TUITION", 2500000L, "2026-2027", "2026-09-30"));
            Student student = createStudentWithDept(conn, "STU_EXC_REF", dept.getId(), "1");

            FeePaymentDAO mockDao = new FeePaymentDAO() {
                @Override
                public long getTotalCompletedPaise(Connection c, int sId, int fsId) {
                    return 400000L;
                }
                @Override
                public long getTotalRefundedPaise(Connection c, int sId, int fsId) {
                    return 500000L;
                }
            };

            FeePaymentService service = new FeePaymentService(mockDao, feeStructureDAO, studentDAO, auditService);
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> service.processPayment(admin, student.getId(), fs.getId(), "2026-2027", 1000L, "ONLINE"));
            assertTrue(ex.getMessage().contains("exceed total completed payments"));
        }
    }

    @Test
    @DisplayName("6. Attempting payment exceeding remaining outstanding balance is rejected")
    void testProcessPaymentRejectsOverpayment() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            FeeStructure fs = feeStructureDAO.insert(conn, new FeeStructure(dept.getId(), "1", "TUITION", 50000L, "2026-2027", "2026-09-30"));
            Student student = createStudentWithDept(conn, "STU_OVERPAY", dept.getId(), "1");

            // Attempting to pay 50001 when balance is 50000
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> feePaymentService.processPayment(admin, student.getId(), fs.getId(), "2026-2027", 50001L, "ONLINE"));
            assertTrue(ex.getMessage().contains("exceeds remaining outstanding balance"));
        }
    }

    @Test
    @DisplayName("7. Refund arithmetic safety near Long.MAX_VALUE prevents long overflow")
    void testProcessRefundArithmeticSafetyNearLongMaxValue() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);

            // Mock original payment: original = Long.MAX_VALUE - 1000, existing refunds = Long.MAX_VALUE - 2000
            // remaining refundable = 1000. Customer requests 1001L -> rejected
            FeePayment original = new FeePayment();
            original.setId(888);
            original.setStatus("COMPLETED");
            original.setAmountPaidPaise(Long.MAX_VALUE - 1000L);

            FeePaymentDAO mockDao = new FeePaymentDAO() {
                @Override
                public FeePayment findById(Connection c, int id) {
                    return original;
                }
                @Override
                public long getCumulativeRefundsPaise(Connection c, int origId) {
                    return Long.MAX_VALUE - 2000L;
                }
            };

            FeePaymentService service = new FeePaymentService(mockDao, feeStructureDAO, studentDAO, auditService);
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> service.processRefund(admin, 888, 1001L, "Over-refund attempt"));
            assertTrue(ex.getMessage().contains("exceeds remaining refundable balance"));
        }
    }

    @Test
    @DisplayName("8. Transaction rolls back cleanly when DAO aggregate query throws DataAccessException")
    void testPaymentRollbackOnAggregateOverflow() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            FeeStructure fs = feeStructureDAO.insert(conn, new FeeStructure(dept.getId(), "1", "TUITION", 50000L, "2026-2027", "2026-09-30"));
            Student student = createStudentWithDept(conn, "STU_ROLL", dept.getId(), "1");

            FeePaymentDAO mockDao = new FeePaymentDAO() {
                @Override
                public long getTotalCompletedPaise(Connection c, int sId, int fsId) {
                    throw new DataAccessException("Simulated integer overflow on SUM(amount_paid_paise)");
                }
            };

            FeePaymentService service = new FeePaymentService(mockDao, feeStructureDAO, studentDAO, auditService);
            assertThrows(DataAccessException.class,
                    () -> service.processPayment(admin, student.getId(), fs.getId(), "2026-2027", 10000L, "ONLINE"));

            // Verify no payment row was committed
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM fee_payments")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        }
    }

    // =========================================================================
    // Special Regression Gaps (Gap 3, Gap 4, Gap 5, Gap 10)
    // =========================================================================

    @Test
    @DisplayName("Special Regression Gap 3: Fee payment rollback after an audit insertion failure")
    void testPaymentRollbackOnAuditFailure() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            FeeStructure fs = feeStructureDAO.insert(conn, new FeeStructure(dept.getId(), "1", "TUITION", 50000L, "2026-2027", "2026-09-30"));
            Student student = createStudentWithDept(conn, "STU_AUD_FAIL", dept.getId(), "1");

            // Mock AuditService throwing DataAccessException
            AuditService failingAudit = new AuditService() {
                @Override
                public AuditLog log(Connection c, int userId, String action, String entityType, Integer entityId, String oldValue, String newValue) {
                    throw new DataAccessException("Simulated audit disk failure during payment");
                }
            };

            FeePaymentService service = new FeePaymentService(feePaymentDAO, feeStructureDAO, studentDAO, failingAudit);

            assertThrows(DataAccessException.class,
                    () -> service.processPayment(admin, student.getId(), fs.getId(), "2026-2027", 20000L, "ONLINE"));

            // Verify payment record was ROLLED BACK
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM fee_payments")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Payment record must be rolled back if audit log insertion fails");
            }
        }
    }

    @Test
    @DisplayName("Special Regression Gap 4: Full and partial refund transaction integrity")
    void testRefundTransactionIntegrity() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            FeeStructure fs = feeStructureDAO.insert(conn, new FeeStructure(dept.getId(), "1", "TUITION", 100000L, "2026-2027", "2026-09-30"));
            Student student = createStudentWithDept(conn, "STU_REF_INT", dept.getId(), "1");

            FeePayment original = feePaymentService.processPayment(admin, student.getId(), fs.getId(), "2026-2027", 100000L, "ONLINE");

            // 1. Partial refund of 40,000 paise succeeds
            FeePayment partial = feePaymentService.processRefund(admin, original.getId(), 40000L, "Partial scholarship adjustment");
            assertNotNull(partial);
            assertEquals(original.getId(), partial.getOriginalPaymentId());
            assertEquals("REFUNDED", partial.getStatus());
            assertEquals(40000L, partial.getAmountPaidPaise());

            // 2. Second partial refund of 60,000 paise succeeds (total 100,000)
            FeePayment remaining = feePaymentService.processRefund(admin, original.getId(), 60000L, "Remaining balance refund");
            assertNotNull(remaining);

            // 3. Any additional refund must be rejected (0 balance remaining)
            assertThrows(ValidationException.class,
                    () -> feePaymentService.processRefund(admin, original.getId(), 1L, "Exceeding refund"));
        }
    }

    @Test
    @DisplayName("Special Regression Gap 5: Concurrent payments acquire IMMEDIATE writer lock safely")
    void testConcurrentPaymentImmediateLockAcquisition() throws Exception {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = createAdmin(conn);
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            FeeStructure fs = feeStructureDAO.insert(conn, new FeeStructure(dept.getId(), "1", "TUITION", 100000L, "2026-2027", "2026-09-30"));
            Student student = createStudentWithDept(conn, "STU_CONCUR", dept.getId(), "1");

            CountDownLatch readyLatch = new CountDownLatch(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(2);

            AtomicReference<FeePayment> p1Result = new AtomicReference<>();
            AtomicReference<FeePayment> p2Result = new AtomicReference<>();
            AtomicReference<Throwable> err1 = new AtomicReference<>();
            AtomicReference<Throwable> err2 = new AtomicReference<>();

            Thread t1 = new Thread(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(5, TimeUnit.SECONDS);
                    FeePayment p = feePaymentService.processPayment(admin, student.getId(), fs.getId(), "2026-2027", 50000L, "ONLINE");
                    p1Result.set(p);
                } catch (Throwable t) {
                    err1.set(t);
                } finally {
                    doneLatch.countDown();
                }
            });

            Thread t2 = new Thread(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(5, TimeUnit.SECONDS);
                    FeePayment p = feePaymentService.processPayment(admin, student.getId(), fs.getId(), "2026-2027", 50000L, "ONLINE");
                    p2Result.set(p);
                } catch (Throwable t) {
                    err2.set(t);
                } finally {
                    doneLatch.countDown();
                }
            });

            t1.start();
            t2.start();

            assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
            startLatch.countDown(); // Launch both simultaneously
            assertTrue(doneLatch.await(15, TimeUnit.SECONDS));

            // Immediate writer lock acquisition ensures no lost updates or corrupted overcollection:
            // One thread acquires writer lock first; the competing thread either serializes or fails
            // safely with DataAccessException (SQLITE_LOCKED/BUSY) without committing partial state
            assertTrue(p1Result.get() != null || p2Result.get() != null, "At least one concurrent payment must succeed");

            if (p1Result.get() == null) {
                assertNotNull(err1.get());
                assertInstanceOf(DataAccessException.class, err1.get());
            }
            if (p2Result.get() == null) {
                assertNotNull(err2.get());
                assertInstanceOf(DataAccessException.class, err2.get());
            }

            // Ledger consistency invariant: Total paid must be either 50000L (one succeeded, other locked out)
            // or 100000L (serialized), never exceeding fee structure amount (no overcollection race)
            long totalPaid = feePaymentDAO.getTotalCompletedPaise(conn, student.getId(), fs.getId());
            assertTrue(totalPaid == 50000L || totalPaid == 100000L, "Total paid must match valid serialized state: " + totalPaid);
        }
    }

    @Test
    @DisplayName("Special Regression Gap 10: Fee payment access restricted to authorized roles")
    void testFeeAccessRoleAuthorization() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User faculty = userDAO.insert(conn, new User("fac_no_fee", "$2a$12$hash", Role.FACULTY));
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            FeeStructure fs = feeStructureDAO.insert(conn, new FeeStructure(dept.getId(), "1", "TUITION", 50000L, "2026-2027", "2026-09-30"));
            Student student = createStudentWithDept(conn, "STU_AUTH_FEE", dept.getId(), "1");

            // Faculty is blocked from processing payments
            assertThrows(AuthenticationException.class,
                    () -> feePaymentService.processPayment(faculty, student.getId(), fs.getId(), "2026-2027", 1000L, "ONLINE"));

            // Faculty is blocked from processing refunds
            assertThrows(AuthenticationException.class,
                    () -> feePaymentService.processRefund(faculty, 1, 1000L, "reason"));
        }
    }
}
