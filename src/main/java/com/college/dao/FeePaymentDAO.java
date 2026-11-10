package com.college.dao;

import com.college.config.DBConnection;
import com.college.exception.DataAccessException;
import com.college.model.FeePayment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the {@code fee_payments} table.
 *
 * <p>Implements an immutable financial transaction ledger where all amounts are tracked
 * in integer paise (1 Rupee = 100 paise) with zero floating-point representation.
 * Supports payments, partial and full refunds, and overflow-safe ledger aggregate calculations.</p>
 *
 * <p>In accordance with database triggers and financial invariants:
 * <ul>
 *   <li>Financial records are strictly append-only (no arbitrary updates or deletes).</li>
 *   <li>Refunds reference their original completed payment via {@code original_payment_id}.</li>
 *   <li>Aggregates use SQLite native {@code SUM()} with hard integer overflow detection.</li>
 * </ul>
 * </p>
 */
public class FeePaymentDAO {

    private static final String BASE_SELECT =
        "SELECT fp.id, fp.student_id, fp.fee_structure_id, fp.amount_paid_paise, " +
        "       fp.payment_date, fp.payment_method, fp.transaction_ref, fp.status, " +
        "       fp.original_payment_id, fp.created_at, " +
        "       (s.first_name || ' ' || s.last_name) AS student_name, s.student_id AS student_id_number, " +
        "       fs.fee_type " +
        "FROM fee_payments fp " +
        "JOIN students s ON fp.student_id = s.id " +
        "JOIN fee_structures fs ON fp.fee_structure_id = fs.id ";

    private static Boolean extendedColumnsAvailable = null;

    private static synchronized boolean hasExtendedPaymentColumns(Connection conn) {
        if (extendedColumnsAvailable != null) {
            return extendedColumnsAvailable;
        }
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(fee_payments)")) {
            while (rs.next()) {
                if ("receipt_number".equalsIgnoreCase(rs.getString("name"))) {
                    extendedColumnsAvailable = true;
                    return true;
                }
            }
        } catch (SQLException ignored) {
        }
        extendedColumnsAvailable = false;
        return false;
    }

    /**
     * Inserts a new fee payment or refund record into the ledger.
     *
     * @param conn database connection (must participate in the active transaction)
     * @param payment payment entity to persist
     * @return persisted entity with database-generated ID and created_at timestamp
     * @throws DataAccessException if insertion fails or database triggers abort the operation
     */
    public FeePayment insert(Connection conn, FeePayment payment) {
        boolean hasExt = hasExtendedPaymentColumns(conn);
        String sql;
        if (hasExt) {
            sql = "INSERT INTO fee_payments (student_id, fee_structure_id, amount_paid_paise, " +
                  "                         payment_date, payment_method, transaction_ref, " +
                  "                         status, original_payment_id, gateway_order_id, " +
                  "                         gateway_payment_id, gateway_signature, gateway_name, " +
                  "                         receipt_number, utr_number, verification_status) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        } else {
            sql = "INSERT INTO fee_payments (student_id, fee_structure_id, amount_paid_paise, " +
                  "                         payment_date, payment_method, transaction_ref, " +
                  "                         status, original_payment_id) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        }

        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, payment.getStudentId());
            pstmt.setInt(2, payment.getFeeStructureId());
            pstmt.setLong(3, payment.getAmountPaidPaise());
            pstmt.setString(4, payment.getPaymentDate());
            pstmt.setString(5, payment.getPaymentMethod());
            pstmt.setString(6, payment.getTransactionRef());
            pstmt.setString(7, payment.getStatus() != null ? payment.getStatus() : "COMPLETED");
            if (payment.getOriginalPaymentId() != null) {
                pstmt.setInt(8, payment.getOriginalPaymentId());
            } else {
                pstmt.setNull(8, Types.INTEGER);
            }

            if (hasExt) {
                pstmt.setString(9, payment.getGatewayOrderId());
                pstmt.setString(10, payment.getGatewayPaymentId());
                pstmt.setString(11, payment.getGatewaySignature());
                pstmt.setString(12, payment.getGatewayName());
                pstmt.setString(13, payment.getReceiptNumber());
                pstmt.setString(14, payment.getUtrNumber());
                pstmt.setString(15, payment.getVerificationStatus() != null ? payment.getVerificationStatus() : "VERIFIED");
            }

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    payment.setId(rs.getInt(1));
                }
            }
            return payment;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert fee payment/refund: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to insert a payment using a managed connection.
     *
     * @param payment payment entity to persist
     * @return persisted entity
     */
    public FeePayment insert(FeePayment payment) {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, payment);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Calculates the total completed payments in paise for a student and fee structure.
     *
     * <p>Uses SQLite native {@code SUM()} which accumulates values as 64-bit signed integers.
     * If total exceeds {@code Long.MAX_VALUE}, SQLite raises an integer overflow SQLException,
     * which is caught and rethrown as {@link DataAccessException}. Never falls back to 0 or null on failure.</p>
     *
     * @param conn database connection
     * @param studentId student ID
     * @param feeStructureId fee structure ID
     * @return sum of completed payments in paise (0L if no payments exist)
     * @throws DataAccessException if database query fails or integer overflow occurs
     */
    public long getTotalCompletedPaise(Connection conn, int studentId, int feeStructureId) {
        String sql = "SELECT COALESCE(SUM(amount_paid_paise), 0) FROM fee_payments " +
                     "WHERE student_id = ? AND fee_structure_id = ? AND status = 'COMPLETED'";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, studentId);
            pstmt.setInt(2, feeStructureId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0L;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to calculate total completed fees: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to calculate total completed payments in paise using a managed connection.
     *
     * @param studentId student ID
     * @param feeStructureId fee structure ID
     * @return sum of completed payments in paise
     */
    public long getTotalCompletedPaise(int studentId, int feeStructureId) {
        try (Connection conn = DBConnection.getConnection()) {
            return getTotalCompletedPaise(conn, studentId, feeStructureId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Calculates the total refunded payments in paise for a student and fee structure.
     *
     * @param conn database connection
     * @param studentId student ID
     * @param feeStructureId fee structure ID
     * @return sum of refunds in paise (0L if no refunds exist)
     * @throws DataAccessException if database query fails or integer overflow occurs
     */
    public long getTotalRefundedPaise(Connection conn, int studentId, int feeStructureId) {
        String sql = "SELECT COALESCE(SUM(amount_paid_paise), 0) FROM fee_payments " +
                     "WHERE student_id = ? AND fee_structure_id = ? AND status = 'REFUNDED'";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, studentId);
            pstmt.setInt(2, feeStructureId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0L;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to calculate total refunded fees: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to calculate total refunded fees in paise using a managed connection.
     *
     * @param studentId student ID
     * @param feeStructureId fee structure ID
     * @return sum of refunds in paise
     */
    public long getTotalRefundedPaise(int studentId, int feeStructureId) {
        try (Connection conn = DBConnection.getConnection()) {
            return getTotalRefundedPaise(conn, studentId, feeStructureId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Calculates the net paid fee amount in paise (Completed - Refunded) for a student and fee structure.
     *
     * @param conn database connection
     * @param studentId student ID
     * @param feeStructureId fee structure ID
     * @return net paid amount in paise
     */
    public long getNetPaidPaise(Connection conn, int studentId, int feeStructureId) {
        long totalCompleted = getTotalCompletedPaise(conn, studentId, feeStructureId);
        long totalRefunded = getTotalRefundedPaise(conn, studentId, feeStructureId);
        return totalCompleted - totalRefunded;
    }

    /**
     * Convenience method to calculate net paid fee amount in paise using a managed connection.
     *
     * @param studentId student ID
     * @param feeStructureId fee structure ID
     * @return net paid amount in paise
     */
    public long getNetPaidPaise(int studentId, int feeStructureId) {
        try (Connection conn = DBConnection.getConnection()) {
            return getNetPaidPaise(conn, studentId, feeStructureId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Calculates the cumulative refunds in paise already issued against a specific original payment.
     *
     * @param conn database connection
     * @param originalPaymentId original payment transaction ID
     * @return sum of previous refunds in paise (0L if none)
     * @throws DataAccessException if database query fails or integer overflow occurs
     */
    public long getCumulativeRefundsPaise(Connection conn, int originalPaymentId) {
        String sql = "SELECT COALESCE(SUM(amount_paid_paise), 0) FROM fee_payments " +
                     "WHERE original_payment_id = ? AND status = 'REFUNDED'";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, originalPaymentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0L;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to calculate cumulative refunds for payment " + originalPaymentId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to calculate cumulative refunds using a managed connection.
     *
     * @param originalPaymentId original payment transaction ID
     * @return sum of previous refunds in paise
     */
    public long getCumulativeRefundsPaise(int originalPaymentId) {
        try (Connection conn = DBConnection.getConnection()) {
            return getCumulativeRefundsPaise(conn, originalPaymentId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a payment or refund record by primary key ID, populating transient display properties.
     *
     * @param conn database connection
     * @param id payment transaction ID
     * @return payment entity or {@code null} if not found
     */
    public FeePayment findById(Connection conn, int id) {
        String sql = BASE_SELECT + "WHERE fp.id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find fee payment by ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find a payment by ID using a managed connection.
     *
     * @param id payment transaction ID
     * @return payment entity or {@code null} if not found
     */
    public FeePayment findById(int id) {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, id);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a payment by its unique transaction reference string.
     *
     * @param conn database connection
     * @param transactionRef transaction reference (e.g. "TXN-...")
     * @return payment entity or {@code null} if not found
     */
    public FeePayment findByTransactionRef(Connection conn, String transactionRef) {
        String sql = BASE_SELECT + "WHERE fp.transaction_ref = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, transactionRef);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find payment by transactionRef '" + transactionRef + "': " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find a payment by transaction reference using a managed connection.
     *
     * @param transactionRef transaction reference
     * @return payment entity or {@code null} if not found
     */
    public FeePayment findByTransactionRef(String transactionRef) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByTransactionRef(conn, transactionRef);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all payment and refund records for a student ordered chronologically descending.
     *
     * @param conn database connection
     * @param studentId student ID
     * @return list of payments and refunds
     */
    public List<FeePayment> findByStudent(Connection conn, int studentId) {
        String sql = BASE_SELECT + "WHERE fp.student_id = ? ORDER BY fp.payment_date DESC, fp.id DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, studentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<FeePayment> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find fee payments for student " + studentId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find payments by student using a managed connection.
     *
     * @param studentId student ID
     * @return list of payments and refunds
     */
    public List<FeePayment> findByStudent(int studentId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByStudent(conn, studentId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all payments for a specific student and fee structure.
     *
     * @param conn database connection
     * @param studentId student ID
     * @param feeStructureId fee structure ID
     * @return list of payments and refunds
     */
    public List<FeePayment> findByStudentAndStructure(Connection conn, int studentId, int feeStructureId) {
        String sql = BASE_SELECT + "WHERE fp.student_id = ? AND fp.fee_structure_id = ? " +
                     "ORDER BY fp.payment_date DESC, fp.id DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, studentId);
            pstmt.setInt(2, feeStructureId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<FeePayment> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find fee payments for student and structure: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find payments by student and structure using a managed connection.
     *
     * @param studentId student ID
     * @param feeStructureId fee structure ID
     * @return list of payments and refunds
     */
    public List<FeePayment> findByStudentAndStructure(int studentId, int feeStructureId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByStudentAndStructure(conn, studentId, feeStructureId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all refund records issued against an original payment.
     *
     * @param conn database connection
     * @param originalPaymentId original payment transaction ID
     * @return list of refund records
     */
    public List<FeePayment> findRefundsForPayment(Connection conn, int originalPaymentId) {
        String sql = BASE_SELECT + "WHERE fp.original_payment_id = ? AND fp.status = 'REFUNDED' " +
                     "ORDER BY fp.payment_date DESC, fp.id DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, originalPaymentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<FeePayment> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find refunds for payment " + originalPaymentId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find refunds for a payment using a managed connection.
     *
     * @param originalPaymentId original payment transaction ID
     * @return list of refund records
     */
    public List<FeePayment> findRefundsForPayment(int originalPaymentId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findRefundsForPayment(conn, originalPaymentId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all fee payment and refund transactions.
     *
     * @param conn database connection
     * @return list of all transactions
     */
    public List<FeePayment> findAll(Connection conn) {
        String sql = BASE_SELECT + "ORDER BY fp.payment_date DESC, fp.id DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<FeePayment> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve all fee payments: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to retrieve all transactions using a managed connection.
     *
     * @return list of all transactions
     */
    public List<FeePayment> findAll() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAll(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Updates payment status for state transitions (e.g., PENDING to COMPLETED or FAILED).
     *
     * <p>Note: Database trigger {@code trg_fee_payments_immutable_update} enforces that only
     * transitions from {@code PENDING} to {@code COMPLETED} or {@code FAILED} are permitted,
     * and only if no associated refunds exist.</p>
     *
     * @param conn database connection
     * @param id payment ID
     * @param newStatus target status
     * @return {@code true} if updated; {@code false} otherwise
     * @throws DataAccessException if trigger aborts the transition or database error occurs
     */
    public boolean updateStatus(Connection conn, int id, String newStatus) {
        String sql = "UPDATE fee_payments SET status = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newStatus);
            pstmt.setInt(2, id);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update status for fee payment ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to update payment status using a managed connection.
     *
     * @param id payment ID
     * @param newStatus target status
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean updateStatus(int id, String newStatus) {
        try (Connection conn = DBConnection.getConnection()) {
            return updateStatus(conn, id, newStatus);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Maps the current row of a ResultSet to a FeePayment domain model.
     *
     * @param rs result set positioned at a valid row
     * @return populated FeePayment model
     * @throws SQLException if column reading fails
     */
    private FeePayment mapRow(ResultSet rs) throws SQLException {
        FeePayment fp = new FeePayment();
        fp.setId(rs.getInt("id"));
        fp.setStudentId(rs.getInt("student_id"));
        fp.setFeeStructureId(rs.getInt("fee_structure_id"));
        fp.setAmountPaidPaise(rs.getLong("amount_paid_paise"));
        fp.setPaymentDate(rs.getString("payment_date"));
        fp.setPaymentMethod(rs.getString("payment_method"));
        fp.setTransactionRef(rs.getString("transaction_ref"));
        fp.setStatus(rs.getString("status"));
        Object origId = rs.getObject("original_payment_id");
        fp.setOriginalPaymentId(origId != null ? rs.getInt("original_payment_id") : null);
        fp.setCreatedAt(rs.getString("created_at"));
        fp.setStudentName(rs.getString("student_name"));
        fp.setStudentIdNumber(rs.getString("student_id_number"));
        fp.setFeeType(rs.getString("fee_type"));

        try { fp.setGatewayOrderId(rs.getString("gateway_order_id")); } catch (SQLException ignored) {}
        try { fp.setGatewayPaymentId(rs.getString("gateway_payment_id")); } catch (SQLException ignored) {}
        try { fp.setGatewaySignature(rs.getString("gateway_signature")); } catch (SQLException ignored) {}
        try { fp.setGatewayName(rs.getString("gateway_name")); } catch (SQLException ignored) {}
        try { fp.setReceiptNumber(rs.getString("receipt_number")); } catch (SQLException ignored) {}
        try { fp.setUtrNumber(rs.getString("utr_number")); } catch (SQLException ignored) {}
        try { fp.setVerificationStatus(rs.getString("verification_status")); } catch (SQLException ignored) {}

        return fp;
    }
}

