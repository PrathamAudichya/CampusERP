package com.college.service;

import com.college.config.DBConnection;
import com.college.dao.FeePaymentDAO;
import com.college.dao.FeeStructureDAO;
import com.college.dao.StudentDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.DataAccessException;
import com.college.exception.ValidationException;
import com.college.model.FeePayment;
import com.college.model.FeeStructure;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.User;
import com.college.util.ValidationUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Financially critical service managing student fee payments, partial/full refunds, and ledger invariants.
 *
 * <p>Financial Rules:
 * <ul>
 *   <li>All internal arithmetic operates strictly in 64-bit integer paise (1 INR = 100 paise).</li>
 *   <li>Subtraction-first bounds validation prevents Long.MAX_VALUE arithmetic overflow.</li>
 *   <li>Never uses Math.max(0, ...) to mask corrupt or negative ledger balances.</li>
 *   <li>Immediate SQLite write transactions prevent concurrent balance over-collection.</li>
 *   <li>Append-only ledger: Completed payments are never modified; refunds are recorded as child transactions.</li>
 * </ul>
 * </p>
 */
public class FeePaymentService {

    private final FeePaymentDAO feePaymentDAO;
    private final FeeStructureDAO feeStructureDAO;
    private final StudentDAO studentDAO;
    private final AuditService auditService;

    public FeePaymentService() {
        this.feePaymentDAO = new FeePaymentDAO();
        this.feeStructureDAO = new FeeStructureDAO();
        this.studentDAO = new StudentDAO();
        this.auditService = new AuditService();
    }

    public FeePaymentService(FeePaymentDAO feePaymentDAO, FeeStructureDAO feeStructureDAO,
                             StudentDAO studentDAO, AuditService auditService) {
        this.feePaymentDAO = feePaymentDAO;
        this.feeStructureDAO = feeStructureDAO;
        this.studentDAO = studentDAO;
        this.auditService = auditService;
    }

    /**
     * Atomically validates and records a student fee payment.
     * Follows the workflow defined in Section 4.3 of the authoritative plan.
     *
     * @param currentUser        authenticated user (ADMIN only)
     * @param studentId          student ID
     * @param feeStructureId     fee structure ID
     * @param academicYear       intended academic year
     * @param paymentAmountPaise payment amount in integer paise
     * @param paymentMethod      payment method (e.g. 'UPI', 'CARD', 'NET_BANKING', 'CASH')
     * @return persisted FeePayment entity
     */
    public FeePayment processPayment(User currentUser, int studentId, int feeStructureId,
                                     String academicYear, long paymentAmountPaise,
                                     String paymentMethod) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        ValidationUtil.requireNonEmpty(academicYear, "Academic year");
        ValidationUtil.requireNonEmpty(paymentMethod, "Payment method");
        if (paymentAmountPaise <= 0L) {
            throw new ValidationException("Payment amount must be strictly positive.");
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Student active validation
                Student student = studentDAO.findById(conn, studentId);
                if (student == null || !student.isActive()) {
                    throw new ValidationException("Student " + studentId + " is inactive or not found.");
                }

                // 2. Fee structure active validation
                FeeStructure feeStructure = feeStructureDAO.findById(conn, feeStructureId);
                if (feeStructure == null || !feeStructure.isActive()) {
                    throw new ValidationException("Fee structure " + feeStructureId + " is inactive or not found.");
                }

                // 3. Department matching
                if (student.getDepartmentId() != feeStructure.getDepartmentId()) {
                    throw new ValidationException(String.format(
                            "Student department (%d) does not match fee structure department (%d).",
                            student.getDepartmentId(), feeStructure.getDepartmentId()));
                }

                // 4. Semester matching
                if (!student.getSemester().trim().equalsIgnoreCase(feeStructure.getSemester().trim())) {
                    throw new ValidationException(String.format(
                            "Student semester (%s) does not match fee structure semester (%s).",
                            student.getSemester(), feeStructure.getSemester()));
                }

                // 5. Academic-year matching
                if (!feeStructure.getAcademicYear().trim().equalsIgnoreCase(academicYear.trim())) {
                    throw new ValidationException(String.format(
                            "Supplied academic year (%s) does not match fee structure academic year (%s).",
                            academicYear, feeStructure.getAcademicYear()));
                }

                // 6. Strict ledger aggregate validation & overflow-safe balance calculation
                long totalPaidPaise = feePaymentDAO.getTotalCompletedPaise(conn, studentId, feeStructureId);
                long totalRefundedPaise = feePaymentDAO.getTotalRefundedPaise(conn, studentId, feeStructureId);

                // Invariant validation: reject corrupted historical ledger data
                if (totalPaidPaise < 0L) {
                    throw new ValidationException("Corrupted ledger state: total completed payments cannot be negative (" + totalPaidPaise + ").");
                }
                if (totalRefundedPaise < 0L) {
                    throw new ValidationException("Corrupted ledger state: total refunds cannot be negative (" + totalRefundedPaise + ").");
                }
                if (totalRefundedPaise > totalPaidPaise) {
                    throw new ValidationException(String.format(
                            "Corrupted ledger state: cumulative refunds (%d paise) exceed total completed payments (%d paise).",
                            totalRefundedPaise, totalPaidPaise));
                }

                long structureAmountPaise = feeStructure.getAmountPaise();
                if (structureAmountPaise <= 0L) {
                    throw new ValidationException("Invalid fee structure amount: " + structureAmountPaise);
                }

                // Net paid is guaranteed non-negative and overflow-free
                long netPaidPaise = totalPaidPaise - totalRefundedPaise;
                if (netPaidPaise > structureAmountPaise) {
                    throw new ValidationException(String.format(
                            "Corrupted ledger state: net paid (%d paise) exceeds total fee structure amount (%d paise).",
                            netPaidPaise, structureAmountPaise));
                }

                // Outstanding balance calculation via subtraction-first arithmetic
                long outstandingPaise = structureAmountPaise - netPaidPaise;
                if (paymentAmountPaise > outstandingPaise) {
                    BigDecimal paymentRupees = BigDecimal.valueOf(paymentAmountPaise).divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY);
                    BigDecimal outstandingRupees = BigDecimal.valueOf(outstandingPaise).divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY);
                    throw new ValidationException(String.format(
                            "Payment of ₹%s exceeds remaining outstanding balance of ₹%s.",
                            paymentRupees.toPlainString(), outstandingRupees.toPlainString()));
                }

                // 7. Insert payment record with collision-resistant transaction ref
                FeePayment payment = new FeePayment();
                payment.setStudentId(studentId);
                payment.setFeeStructureId(feeStructureId);
                payment.setAmountPaidPaise(paymentAmountPaise);
                payment.setPaymentDate(LocalDate.now().toString());
                payment.setPaymentMethod(paymentMethod.trim().toUpperCase());
                String transactionRef = String.format("PAY-%d-%s",
                        Instant.now().toEpochMilli(), UUID.randomUUID().toString().replace("-", "").toUpperCase());
                payment.setTransactionRef(transactionRef);
                payment.setStatus("COMPLETED");
                payment.setOriginalPaymentId(null);

                FeePayment saved = feePaymentDAO.insert(conn, payment);

                // 8. Transactional audit log
                BigDecimal paidRupees = BigDecimal.valueOf(paymentAmountPaise).divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY);
                auditService.log(conn, currentUser.getId(), "PROCESS_PAYMENT", "FEE_PAYMENT", saved.getId(),
                        null, String.format("Paid: ₹%s via %s (Student: %s, Structure: %d)",
                                paidRupees.toPlainString(), paymentMethod, student.getStudentId(), feeStructureId));

                conn.commit();
                return saved;
            } catch (Throwable ex) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                if (ex instanceof ValidationException) {
                    throw (ValidationException) ex;
                } else if (ex instanceof AuthenticationException) {
                    throw (AuthenticationException) ex;
                } else if (ex instanceof DataAccessException) {
                    throw (DataAccessException) ex;
                } else if (ex instanceof SQLException) {
                    throw new DataAccessException("Database failure during payment processing: " + ex.getMessage(), (SQLException) ex);
                } else if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                } else if (ex instanceof Error) {
                    throw (Error) ex;
                } else {
                    throw new RuntimeException("Unexpected error during payment processing", ex);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Atomically validates and issues a partial or full refund against an existing COMPLETED payment.
     * Follows the overflow-safe workflow defined in Section 4.4 of the authoritative plan.
     *
     * @param currentUser       authenticated user (ADMIN only)
     * @param originalPaymentId primary key of the original COMPLETED payment
     * @param refundAmountPaise refund amount in integer paise
     * @param reason            audit explanation for issuing refund
     * @return persisted refund FeePayment entity
     */
    public FeePayment processRefund(User currentUser, int originalPaymentId,
                                    long refundAmountPaise, String reason) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        if (refundAmountPaise <= 0L) {
            throw new ValidationException("Refund amount must be strictly positive.");
        }
        ValidationUtil.requireNonEmpty(reason, "Refund reason");

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Fetch original payment
                FeePayment original = feePaymentDAO.findById(conn, originalPaymentId);
                if (original == null) {
                    throw new ValidationException("Original payment record " + originalPaymentId + " not found.");
                }

                // 2. State & referential integrity validation
                if (!"COMPLETED".equalsIgnoreCase(original.getStatus())) {
                    throw new ValidationException("Only COMPLETED payments can be refunded. Current status: " + original.getStatus());
                }
                if (original.getOriginalPaymentId() != null) {
                    throw new ValidationException("Cannot refund a transaction that is itself a refund.");
                }
                long originalAmount = original.getAmountPaidPaise();
                if (originalAmount <= 0L) {
                    throw new ValidationException("Invalid original payment amount: " + originalAmount);
                }

                // 3. Query existing cumulative refunds with integrity check
                long existingRefunds = feePaymentDAO.getCumulativeRefundsPaise(conn, originalPaymentId);
                if (existingRefunds < 0L || existingRefunds > originalAmount) {
                    throw new DataAccessException(String.format(
                            "Corrupted refund history detected for payment ID %d: cumulative refunds (%d paise) exceed original (%d paise).",
                            originalPaymentId, existingRefunds, originalAmount));
                }

                // 4. Subtraction-first overflow-safe arithmetic validation:
                // remainingRefundable = originalAmount - existingRefunds (guaranteed non-negative)
                long remainingRefundable = originalAmount - existingRefunds;
                if (refundAmountPaise > remainingRefundable) {
                    BigDecimal requestedRupees = BigDecimal.valueOf(refundAmountPaise).divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY);
                    BigDecimal maxRefundableRupees = BigDecimal.valueOf(remainingRefundable).divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY);
                    throw new ValidationException(String.format(
                            "Refund of ₹%s exceeds remaining refundable balance of ₹%s.",
                            requestedRupees.toPlainString(), maxRefundableRupees.toPlainString()));
                }

                // 5. Create refund record with collision-resistant reference
                FeePayment refund = new FeePayment();
                refund.setStudentId(original.getStudentId());
                refund.setFeeStructureId(original.getFeeStructureId());
                refund.setAmountPaidPaise(refundAmountPaise);
                refund.setPaymentDate(LocalDate.now().toString());
                refund.setPaymentMethod("REFUND");
                String collisionResistantRef = String.format("REF-%d-%s",
                        Instant.now().toEpochMilli(), UUID.randomUUID().toString().replace("-", "").toUpperCase());
                refund.setTransactionRef(collisionResistantRef);
                refund.setStatus("REFUNDED");
                refund.setOriginalPaymentId(originalPaymentId);

                FeePayment savedRefund = feePaymentDAO.insert(conn, refund);

                // 6. Transactional audit log
                BigDecimal refundedRupees = BigDecimal.valueOf(refundAmountPaise).divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY);
                auditService.log(conn, currentUser.getId(), "REFUND", "FEE_PAYMENT", savedRefund.getId(),
                        "Original Payment ID: " + originalPaymentId,
                        String.format("Refunded: ₹%s, Reason: %s", refundedRupees.toPlainString(), reason.trim()));

                conn.commit();
                return savedRefund;
            } catch (Throwable ex) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                if (ex instanceof ValidationException) {
                    throw (ValidationException) ex;
                } else if (ex instanceof AuthenticationException) {
                    throw (AuthenticationException) ex;
                } else if (ex instanceof DataAccessException) {
                    throw (DataAccessException) ex;
                } else if (ex instanceof SQLException) {
                    throw new DataAccessException("Database failure during refund: " + ex.getMessage(), (SQLException) ex);
                } else if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                } else if (ex instanceof Error) {
                    throw (Error) ex;
                } else {
                    throw new RuntimeException("Unexpected error during refund processing", ex);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Calculates the outstanding fee dues in integer paise for a given student and fee structure.
     */
    public long getOutstandingBalance(User currentUser, int studentId, int feeStructureId) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.STUDENT);
        Student student = studentDAO.findById(studentId);
        if (student == null) {
            throw new ValidationException("Student " + studentId + " not found.");
        }
        if (currentUser.getRole() == Role.STUDENT && currentUser.getId() != student.getUserId()) {
            throw new AuthenticationException("Students can only view their own fee balances.");
        }

        FeeStructure feeStructure = feeStructureDAO.findById(feeStructureId);
        if (feeStructure == null) {
            throw new ValidationException("Fee structure " + feeStructureId + " not found.");
        }

        long totalPaid = feePaymentDAO.getTotalCompletedPaise(studentId, feeStructureId);
        long totalRefunded = feePaymentDAO.getTotalRefundedPaise(studentId, feeStructureId);
        long netPaid = totalPaid - totalRefunded;

        return feeStructure.getAmountPaise() - netPaid;
    }

    public List<FeePayment> getPaymentHistoryByStudent(User currentUser, int studentId) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.STUDENT);
        Student student = studentDAO.findById(studentId);
        if (student == null) {
            throw new ValidationException("Student " + studentId + " not found.");
        }
        if (currentUser.getRole() == Role.STUDENT && currentUser.getId() != student.getUserId()) {
            throw new AuthenticationException("Students can only view their own payment history.");
        }
        return feePaymentDAO.findByStudent(studentId);
    }

    public List<FeePayment> getRefundsForPayment(User currentUser, int originalPaymentId) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        return feePaymentDAO.findRefundsForPayment(originalPaymentId);
    }

    public FeePayment getPaymentById(User currentUser, int paymentId) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.STUDENT);
        FeePayment payment = feePaymentDAO.findById(paymentId);
        if (payment == null) {
            return null;
        }
        if (currentUser.getRole() == Role.STUDENT) {
            Student student = studentDAO.findById(payment.getStudentId());
            if (student == null || student.getUserId() != currentUser.getId()) {
                throw new AuthenticationException("Students can only view their own payments.");
            }
        }
        return payment;
    }
}

