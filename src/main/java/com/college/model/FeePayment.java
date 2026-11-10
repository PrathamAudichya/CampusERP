package com.college.model;

import com.college.exception.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Domain entity representing a student fee payment or refund transaction.
 * Maps to the {@code fee_payments} table in the database schema.
 *
 * <p>Financial integrity rule: The authoritative transacted amount is stored
 * as integer paise in {@code amountPaidPaise} (₹1 = 100 paise) to prevent
 * floating-point rounding errors.</p>
 */
public class FeePayment {

    private static final BigDecimal PAISE_PER_RUPEE = BigDecimal.valueOf(100);

    private int id;
    private int studentId;
    private int feeStructureId;
    private long amountPaidPaise;
    private String paymentDate;
    private String paymentMethod;
    private String transactionRef;
    private String status = "COMPLETED";
    private Integer originalPaymentId;
    private String createdAt;

    // Payment Gateway & Reconciliation fields
    private String gatewayOrderId;
    private String gatewayPaymentId;
    private String gatewaySignature;
    private String gatewayName;
    private String receiptNumber;
    private String utrNumber;
    private String verificationStatus = "VERIFIED";

    // Transient display properties populated from joins
    private String studentName;
    private String studentIdNumber;
    private String feeType;

    /**
     * Default no-argument constructor.
     */
    public FeePayment() {
    }

    /**
     * Convenience constructor for recording a fee payment.
     *
     * @param studentId       paying student ID
     * @param feeStructureId  applicable fee structure ID
     * @param amountPaidPaise monetary sum transacted in paise
     * @param paymentDate     date of payment (YYYY-MM-DD)
     * @param paymentMethod   payment method (UPI, NET_BANKING, CARD, CASH, REFUND)
     * @param transactionRef  unique receipt or gateway transaction reference
     */
    public FeePayment(int studentId, int feeStructureId, long amountPaidPaise,
                      String paymentDate, String paymentMethod, String transactionRef) {
        this.studentId = studentId;
        this.feeStructureId = feeStructureId;
        this.amountPaidPaise = amountPaidPaise;
        this.paymentDate = paymentDate;
        this.paymentMethod = paymentMethod;
        this.transactionRef = transactionRef;
    }

    /**
     * Full constructor mapping all database columns.
     */
    public FeePayment(int id, int studentId, int feeStructureId, long amountPaidPaise,
                      String paymentDate, String paymentMethod, String transactionRef,
                      String status, Integer originalPaymentId, String createdAt) {
        this.id = id;
        this.studentId = studentId;
        this.feeStructureId = feeStructureId;
        this.amountPaidPaise = amountPaidPaise;
        this.paymentDate = paymentDate;
        this.paymentMethod = paymentMethod;
        this.transactionRef = transactionRef;
        this.status = status;
        this.originalPaymentId = originalPaymentId;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public int getFeeStructureId() {
        return feeStructureId;
    }

    public void setFeeStructureId(int feeStructureId) {
        this.feeStructureId = feeStructureId;
    }

    public long getAmountPaidPaise() {
        return amountPaidPaise;
    }

    public void setAmountPaidPaise(long amountPaidPaise) {
        this.amountPaidPaise = amountPaidPaise;
    }

    /**
     * Converts the stored integer paise payment to Rupees as an exact BigDecimal with scale 2.
     * Uses exact decimal arithmetic with zero floating-point representation.
     *
     * @return amount transacted in Rupees with 2 decimal places (e.g., 25000.00)
     */
    public BigDecimal getAmountPaidInRupees() {
        return BigDecimal.valueOf(this.amountPaidPaise, 2);
    }

    /**
     * Validates and converts a Rupee payment amount into stored integer paise.
     *
     * <p>Enforces:
     * <ul>
     *   <li>Non-null amount</li>
     *   <li>Non-negative amount</li>
     *   <li>Scale of at most 2 decimal places</li>
     *   <li>Exact multiplication by 100 without floating-point conversion</li>
     *   <li>Safe overflow detection against {@code Long.MAX_VALUE}</li>
     * </ul>
     * </p>
     *
     * @param amountInRupees amount in Rupees to convert
     * @throws ValidationException if amount is null, negative, has &gt;2 decimal places, or overflows long
     */
    public void setAmountPaidFromRupees(BigDecimal amountInRupees) {
        if (amountInRupees == null) {
            throw new ValidationException("Amount in rupees cannot be null.");
        }
        if (amountInRupees.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Amount in rupees cannot be negative: " + amountInRupees);
        }
        if (amountInRupees.compareTo(BigDecimal.ZERO) == 0) {
            this.amountPaidPaise = 0L;
            return;
        }
        if (amountInRupees.stripTrailingZeros().scale() > 2) {
            throw new ValidationException(String.format(
                "Amount in rupees cannot have more than 2 decimal places: %s", amountInRupees));
        }
        try {
            BigDecimal scaled = amountInRupees.multiply(PAISE_PER_RUPEE);
            this.amountPaidPaise = scaled.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException e) {
            throw new ValidationException(
                "Amount in rupees exceeds supported maximum monetary value: " + amountInRupees, e);
        }
    }

    public String getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(String paymentDate) {
        this.paymentDate = paymentDate;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public void setTransactionRef(String transactionRef) {
        this.transactionRef = transactionRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getOriginalPaymentId() {
        return originalPaymentId;
    }

    public void setOriginalPaymentId(Integer originalPaymentId) {
        this.originalPaymentId = originalPaymentId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getStudentIdNumber() {
        return studentIdNumber;
    }

    public void setStudentIdNumber(String studentIdNumber) {
        this.studentIdNumber = studentIdNumber;
    }

    public String getFeeType() {
        return feeType;
    }

    public void setFeeType(String feeType) {
        this.feeType = feeType;
    }

    public String getGatewayOrderId() {
        return gatewayOrderId;
    }

    public void setGatewayOrderId(String gatewayOrderId) {
        this.gatewayOrderId = gatewayOrderId;
    }

    public String getGatewayPaymentId() {
        return gatewayPaymentId;
    }

    public void setGatewayPaymentId(String gatewayPaymentId) {
        this.gatewayPaymentId = gatewayPaymentId;
    }

    public String getGatewaySignature() {
        return gatewaySignature;
    }

    public void setGatewaySignature(String gatewaySignature) {
        this.gatewaySignature = gatewaySignature;
    }

    public String getGatewayName() {
        return gatewayName;
    }

    public void setGatewayName(String gatewayName) {
        this.gatewayName = gatewayName;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public void setReceiptNumber(String receiptNumber) {
        this.receiptNumber = receiptNumber;
    }

    public String getUtrNumber() {
        return utrNumber;
    }

    public void setUtrNumber(String utrNumber) {
        this.utrNumber = utrNumber;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    @Override
    public String toString() {
        return "FeePayment{" +
                "id=" + id +
                ", studentId=" + studentId +
                ", feeStructureId=" + feeStructureId +
                ", amountPaidPaise=" + amountPaidPaise +
                " (₹" + getAmountPaidInRupees() + ")" +
                ", paymentDate='" + paymentDate + '\'' +
                ", paymentMethod='" + paymentMethod + '\'' +
                ", transactionRef='" + transactionRef + '\'' +
                ", status='" + status + '\'' +
                ", originalPaymentId=" + originalPaymentId +
                ", createdAt='" + createdAt + '\'' +
                ", studentName='" + studentName + '\'' +
                ", studentIdNumber='" + studentIdNumber + '\'' +
                ", feeType='" + feeType + '\'' +
                '}';
    }
}

