package com.college.model;

import com.college.exception.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Domain entity representing an academic fee structure.
 * Maps to the {@code fee_structures} table in the database schema.
 *
 * <p>Financial integrity rule: The authoritative monetary amount is stored
 * as integer paise in {@code amountPaise} (₹1 = 100 paise) to prevent
 * floating-point rounding errors.</p>
 */
public class FeeStructure {

    private static final BigDecimal PAISE_PER_RUPEE = BigDecimal.valueOf(100);

    private int id;
    private int departmentId;
    private String semester;
    private String feeType;
    private long amountPaise;
    private String academicYear;
    private String dueDate;
    private boolean active = true;
    private String createdAt;

    // Transient display property populated from department join
    private String departmentName;

    /**
     * Default no-argument constructor.
     */
    public FeeStructure() {
    }

    /**
     * Convenience constructor for defining a fee structure.
     *
     * @param departmentId applicable department ID
     * @param semester     target semester (e.g., "1", "2")
     * @param feeType      description (e.g., "Tuition", "Lab Fee")
     * @param amountPaise  fee amount in integer paise
     * @param academicYear academic year (e.g., "2026-2027")
     * @param dueDate      payment due date (YYYY-MM-DD)
     */
    public FeeStructure(int departmentId, String semester, String feeType,
                        long amountPaise, String academicYear, String dueDate) {
        this.departmentId = departmentId;
        this.semester = semester;
        this.feeType = feeType;
        this.amountPaise = amountPaise;
        this.academicYear = academicYear;
        this.dueDate = dueDate;
    }

    /**
     * Full constructor mapping all database columns.
     */
    public FeeStructure(int id, int departmentId, String semester, String feeType,
                        long amountPaise, String academicYear, String dueDate,
                        boolean active, String createdAt) {
        this.id = id;
        this.departmentId = departmentId;
        this.semester = semester;
        this.feeType = feeType;
        this.amountPaise = amountPaise;
        this.academicYear = academicYear;
        this.dueDate = dueDate;
        this.active = active;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(int departmentId) {
        this.departmentId = departmentId;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public String getFeeType() {
        return feeType;
    }

    public void setFeeType(String feeType) {
        this.feeType = feeType;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public void setAmountPaise(long amountPaise) {
        this.amountPaise = amountPaise;
    }

    /**
     * Converts the stored integer paise amount to Rupees as an exact BigDecimal with scale 2.
     * Uses exact decimal arithmetic with zero floating-point representation.
     *
     * @return amount in Rupees with 2 decimal places (e.g., 25000.00)
     */
    public BigDecimal getAmountInRupees() {
        return BigDecimal.valueOf(this.amountPaise, 2);
    }

    /**
     * Validates and converts a Rupee amount into stored integer paise.
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
    public void setAmountFromRupees(BigDecimal amountInRupees) {
        if (amountInRupees == null) {
            throw new ValidationException("Amount in rupees cannot be null.");
        }
        if (amountInRupees.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Amount in rupees cannot be negative: " + amountInRupees);
        }
        if (amountInRupees.compareTo(BigDecimal.ZERO) == 0) {
            this.amountPaise = 0L;
            return;
        }
        if (amountInRupees.stripTrailingZeros().scale() > 2) {
            throw new ValidationException(String.format(
                "Amount in rupees cannot have more than 2 decimal places: %s", amountInRupees));
        }
        try {
            BigDecimal scaled = amountInRupees.multiply(PAISE_PER_RUPEE);
            this.amountPaise = scaled.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException e) {
            throw new ValidationException(
                "Amount in rupees exceeds supported maximum monetary value: " + amountInRupees, e);
        }
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    public boolean isActive() {
        return active;
    }

    public boolean getActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    @Override
    public String toString() {
        return "FeeStructure{" +
                "id=" + id +
                ", departmentId=" + departmentId +
                ", semester='" + semester + '\'' +
                ", feeType='" + feeType + '\'' +
                ", amountPaise=" + amountPaise +
                " (₹" + getAmountInRupees() + ")" +
                ", academicYear='" + academicYear + '\'' +
                ", dueDate='" + dueDate + '\'' +
                ", active=" + active +
                ", createdAt='" + createdAt + '\'' +
                ", departmentName='" + departmentName + '\'' +
                '}';
    }
}

