package com.college.service;

import com.college.config.DBConnection;
import com.college.dao.FeePaymentDAO;
import com.college.dao.FeeStructureDAO;
import com.college.dao.StudentDAO;
import com.college.exception.ValidationException;
import com.college.model.FeePayment;
import com.college.model.FeeStructure;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.User;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Enterprise Payment Gateway Service integrating Razorpay and PayU in Sandbox/Simulation modes.
 *
 * <p>Architectural Safety Guarantees:
 * <ul>
 *   <li>All payment orders are created server-side with unique order IDs and receipt references.</li>
 *   <li>Cryptographic signatures (HMAC SHA-256 for Razorpay, SHA-512 for PayU) are verified strictly server-side.</li>
 *   <li>Idempotent payment processing ensures duplicate webhooks or multiple callback invocations are safely rejected.</li>
 *   <li>Transactions are recorded into the immutable financial ledger with audit trails.</li>
 *   <li>No merchant secret keys are exposed to client presentation layers.</li>
 * </ul>
 * </p>
 */
public class PaymentGatewayService {

    // Sandbox test credentials (standard documented simulator keys)
    public static final String RAZORPAY_TEST_KEY_ID = "rzp_test_campus_erp_key";
    private static final String RAZORPAY_TEST_KEY_SECRET = "rzp_test_secret_invar_789";

    public static final String PAYU_TEST_MERCHANT_KEY = "payu_test_merchant_key";
    private static final String PAYU_TEST_SALT = "payu_test_salt_invar_456";

    private final FeePaymentDAO feePaymentDAO;
    private final FeeStructureDAO feeStructureDAO;
    private final StudentDAO studentDAO;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public PaymentGatewayService() {
        this(new FeePaymentDAO(), new FeeStructureDAO(), new StudentDAO(), new AuditService());
    }

    public PaymentGatewayService(FeePaymentDAO feePaymentDAO, FeeStructureDAO feeStructureDAO,
                                 StudentDAO studentDAO, AuditService auditService) {
        this.feePaymentDAO = feePaymentDAO;
        this.feeStructureDAO = feeStructureDAO;
        this.studentDAO = studentDAO;
        this.auditService = auditService;
    }

    /**
     * DTO representing a server-generated payment order for checkout initialization.
     */
    public static class PaymentOrder {
        private final String orderId;
        private final String gateway;
        private final String keyId;
        private final long amountPaise;
        private final String currency = "INR";
        private final String receiptNumber;
        private final int feeStructureId;
        private final int studentId;

        public PaymentOrder(String orderId, String gateway, String keyId, long amountPaise,
                            String receiptNumber, int feeStructureId, int studentId) {
            this.orderId = orderId;
            this.gateway = gateway;
            this.keyId = keyId;
            this.amountPaise = amountPaise;
            this.receiptNumber = receiptNumber;
            this.feeStructureId = feeStructureId;
            this.studentId = studentId;
        }

        public String getOrderId() { return orderId; }
        public String getGateway() { return gateway; }
        public String getKeyId() { return keyId; }
        public long getAmountPaise() { return amountPaise; }
        public String getCurrency() { return currency; }
        public String getReceiptNumber() { return receiptNumber; }
        public int getFeeStructureId() { return feeStructureId; }
        public int getStudentId() { return studentId; }
    }

    /**
     * Generates a formal, standardized institutional receipt number.
     * Format: REC-YYYYMMDD-XXXXXX
     */
    public static String generateReceiptNumber() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int rand = 100000 + new SecureRandom().nextInt(900000);
        return "REC-" + dateStr + "-" + rand;
    }

    /**
     * Creates a server-side payment order for student fee checkout.
     *
     * @param currentUser    authenticated student
     * @param feeStructureId target fee structure ID
     * @param gateway        selected gateway ("RAZORPAY" or "PAYU")
     * @param customAmountPaise payment amount in integer paise (if 0, uses fee structure balance)
     * @return PaymentOrder descriptor
     */
    public PaymentOrder createPaymentOrder(User currentUser, int feeStructureId, String gateway, long customAmountPaise) {
        AuthService.requireRole(currentUser, Role.STUDENT);
        Student student = studentDAO.findByUserId(currentUser.getId());
        if (student == null) {
            throw new ValidationException("No student profile linked to user ID " + currentUser.getId());
        }

        FeeStructure structure = feeStructureDAO.findById(feeStructureId);
        if (structure == null || !structure.isActive()) {
            throw new ValidationException("Fee structure not found or inactive.");
        }

        long amountToPay = customAmountPaise > 0 ? customAmountPaise : structure.getAmountPaise();
        if (amountToPay <= 0) {
            throw new ValidationException("Payment amount must be greater than zero.");
        }

        String gw = gateway != null ? gateway.toUpperCase().trim() : "RAZORPAY";
        String orderId;
        String keyId;

        if ("PAYU".equals(gw)) {
            orderId = "payu_ord_" + System.currentTimeMillis() + "_" + (1000 + secureRandom.nextInt(9000));
            keyId = PAYU_TEST_MERCHANT_KEY;
        } else {
            gw = "RAZORPAY";
            orderId = "order_" + System.currentTimeMillis() + "_" + (1000 + secureRandom.nextInt(9000));
            keyId = RAZORPAY_TEST_KEY_ID;
        }

        String receiptNumber = generateReceiptNumber();

        return new PaymentOrder(orderId, gw, keyId, amountToPay, receiptNumber, feeStructureId, student.getId());
    }

    /**
     * Verifies cryptographic signature for Razorpay sandbox response:
     * HMAC-SHA256(orderId + "|" + paymentId, key_secret)
     */
    public static boolean verifyRazorpaySignature(String orderId, String paymentId, String signature) {
        if (orderId == null || paymentId == null || signature == null) return false;
        try {
            String payload = orderId + "|" + paymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(RAZORPAY_TEST_KEY_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().equalsIgnoreCase(signature.trim());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifies cryptographic hash for PayU sandbox response:
     * SHA-512(salt|status||||||udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key)
     */
    public static boolean verifyPayUSignature(String txnId, String amount, String status, String key, String signature) {
        if (txnId == null || signature == null) return false;
        try {
            String raw = PAYU_TEST_SALT + "|" + (status != null ? status : "success") + "|||||||||||" +
                         (amount != null ? amount : "") + "|" + txnId + "|" + (key != null ? key : PAYU_TEST_MERCHANT_KEY);
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().equalsIgnoreCase(signature.trim());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Generates a valid sandbox signature for simulating complete end-to-end gateway checkouts.
     */
    public static String generateSandboxSignature(String orderId, String paymentId, String gateway) {
        if ("PAYU".equalsIgnoreCase(gateway)) {
            try {
                String raw = PAYU_TEST_SALT + "|success|||||||||||1.00|" + orderId + "|" + PAYU_TEST_MERCHANT_KEY;
                MessageDigest md = MessageDigest.getInstance("SHA-512");
                byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) sb.append(String.format("%02x", b));
                return sb.toString();
            } catch (Exception e) {
                return UUID.randomUUID().toString();
            }
        } else {
            try {
                String payload = orderId + "|" + paymentId;
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(RAZORPAY_TEST_KEY_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder();
                for (byte b : hash) hex.append(String.format("%02x", b));
                return hex.toString();
            } catch (Exception e) {
                return UUID.randomUUID().toString();
            }
        }
    }

    /**
     * Verifies payment signature, performs duplicate payment detection, and commits
     * the verified payment into the immutable ledger inside an atomic transaction.
     *
     * @param currentUser authenticated student
     * @param order       the original server payment order
     * @param paymentId   gateway payment ID returned by gateway checkout
     * @param signature   cryptographic signature returned by gateway
     * @return persisted FeePayment record with generated receipt
     */
    public FeePayment processVerifiedPayment(User currentUser, PaymentOrder order, String paymentId, String signature) {
        AuthService.requireRole(currentUser, Role.STUDENT);

        // 1. Signature Verification
        boolean validSignature;
        if ("PAYU".equalsIgnoreCase(order.getGateway())) {
            validSignature = verifyPayUSignature(order.getOrderId(), String.valueOf(order.getAmountPaise() / 100.0), "success", order.getKeyId(), signature);
        } else {
            validSignature = verifyRazorpaySignature(order.getOrderId(), paymentId, signature);
        }

        if (!validSignature) {
            auditService.log(currentUser.getId(), "PAYMENT_SIGNATURE_FAILED", "FEE_PAYMENT", 0,
                    null, "Cryptographic signature verification failed for Order: " + order.getOrderId() + " via " + order.getGateway());
            throw new ValidationException("Payment verification failed: Invalid cryptographic signature from " + order.getGateway() + ".");
        }

        // 2. Transactional Persistence & Idempotency Check
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Check duplicate transaction ref or payment ID
                FeePayment existing = feePaymentDAO.findByTransactionRef(conn, paymentId);
                if (existing != null) {
                    conn.rollback();
                    throw new ValidationException("Duplicate payment detected: Transaction " + paymentId + " has already been processed.");
                }

                FeePayment payment = new FeePayment();
                payment.setStudentId(order.getStudentId());
                payment.setFeeStructureId(order.getFeeStructureId());
                payment.setAmountPaidPaise(order.getAmountPaise());
                payment.setPaymentDate(LocalDate.now().toString());
                payment.setPaymentMethod(order.getGateway() + "_ONLINE");
                payment.setTransactionRef(paymentId);
                payment.setStatus("COMPLETED");
                payment.setGatewayOrderId(order.getOrderId());
                payment.setGatewayPaymentId(paymentId);
                payment.setGatewaySignature(signature);
                payment.setGatewayName(order.getGateway());
                payment.setReceiptNumber(order.getReceiptNumber());
                payment.setVerificationStatus("VERIFIED");

                FeePayment inserted = feePaymentDAO.insert(conn, payment);

                auditService.log(conn, currentUser.getId(), "ONLINE_FEE_PAYMENT", "FEE_PAYMENT", inserted.getId(),
                        null, "Online fee payment processed via " + order.getGateway() + " (Amount: ₹" + (order.getAmountPaise() / 100.0) + ", Receipt: " + order.getReceiptNumber() + ")");

                conn.commit();
                return inserted;
            } catch (Exception e) {
                conn.rollback();
                if (e instanceof ValidationException) throw (ValidationException) e;
                throw new ValidationException("Failed to persist verified fee payment: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new ValidationException("Database connection failure during payment processing: " + e.getMessage());
        }
    }

    /**
     * Submits a manual bank transfer (UTR / NEFT / IMPS) record for administrative verification.
     */
    public FeePayment submitManualBankTransfer(User currentUser, int feeStructureId, long amountPaise,
                                               String utrNumber, String paymentDate) {
        AuthService.requireRole(currentUser, Role.STUDENT);
        Student student = studentDAO.findByUserId(currentUser.getId());
        if (student == null) throw new ValidationException("Student profile not found.");

        if (utrNumber == null || utrNumber.trim().length() < 6) {
            throw new ValidationException("Please provide a valid UTR / Bank Transaction Reference Number.");
        }
        if (amountPaise <= 0) {
            throw new ValidationException("Transfer amount must be greater than zero.");
        }

        String receiptNum = generateReceiptNumber();

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                FeePayment payment = new FeePayment();
                payment.setStudentId(student.getId());
                payment.setFeeStructureId(feeStructureId);
                payment.setAmountPaidPaise(amountPaise);
                payment.setPaymentDate(paymentDate != null ? paymentDate : LocalDate.now().toString());
                payment.setPaymentMethod("BANK_TRANSFER");
                payment.setTransactionRef(utrNumber.trim());
                payment.setStatus("PENDING");
                payment.setUtrNumber(utrNumber.trim());
                payment.setReceiptNumber(receiptNum);
                payment.setVerificationStatus("PENDING_VERIFICATION");

                FeePayment inserted = feePaymentDAO.insert(conn, payment);

                auditService.log(conn, currentUser.getId(), "SUBMIT_BANK_TRANSFER", "FEE_PAYMENT", inserted.getId(),
                        null, "Student submitted manual bank transfer UTR: " + utrNumber.trim() + " for ₹" + (amountPaise / 100.0));

                conn.commit();
                return inserted;
            } catch (Exception e) {
                conn.rollback();
                throw new ValidationException("Failed to submit bank transfer: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new ValidationException("Database error: " + e.getMessage());
        }
    }
}

