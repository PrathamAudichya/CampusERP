package com.college.service;

import com.college.dao.DepartmentDAO;
import com.college.dao.FeeStructureDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.ValidationException;
import com.college.model.Department;
import com.college.model.FeeStructure;
import com.college.model.Role;
import com.college.model.User;
import com.college.util.ValidationUtil;

import java.util.List;

/**
 * Service managing departmental fee structures, semester schedules, and soft-deletion reactivation.
 */
public class FeeStructureService {

    private final FeeStructureDAO feeStructureDAO;
    private final DepartmentDAO departmentDAO;
    private final AuditService auditService;

    public FeeStructureService() {
        this.feeStructureDAO = new FeeStructureDAO();
        this.departmentDAO = new DepartmentDAO();
        this.auditService = new AuditService();
    }

    public FeeStructureService(FeeStructureDAO feeStructureDAO, DepartmentDAO departmentDAO, AuditService auditService) {
        this.feeStructureDAO = feeStructureDAO;
        this.departmentDAO = departmentDAO;
        this.auditService = auditService;
    }

    /**
     * Creates a new fee structure or reactivates an existing inactive one with identical unique constraint keys.
     *
     * @param currentUser  authenticated user (ADMIN only)
     * @param feeStructure fee structure entity containing fee schedule details
     * @return persisted or reactivated FeeStructure
     */
    public FeeStructure createFeeStructure(User currentUser, FeeStructure feeStructure) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        validateFeeStructureInput(feeStructure);

        Department dept = departmentDAO.findById(feeStructure.getDepartmentId());
        if (dept == null || !dept.isActive()) {
            throw new ValidationException("Department ID " + feeStructure.getDepartmentId() + " does not exist or is inactive.");
        }

        String semester = feeStructure.getSemester().trim();
        String feeType = feeStructure.getFeeType().trim();
        String academicYear = feeStructure.getAcademicYear().trim();

        FeeStructure existing = feeStructureDAO.findByDeptSemesterTypeYear(
                feeStructure.getDepartmentId(), semester, feeType, academicYear);

        if (existing != null) {
            if (existing.isActive()) {
                throw new ValidationException(String.format(
                        "Fee structure already exists for department %s, semester %s, fee type '%s', and academic year %s.",
                        dept.getCode(), semester, feeType, academicYear));
            }
            // Section 4.7: Inactive Match Reactivation Behavior
            existing.setAmountPaise(feeStructure.getAmountPaise());
            existing.setDueDate(feeStructure.getDueDate().trim());
            existing.setActive(true);

            feeStructureDAO.update(existing);
            feeStructureDAO.setActive(existing.getId(), true);

            auditService.log(currentUser.getId(), "REACTIVATE_FEE_STRUCTURE", "FEE_STRUCTURE", existing.getId(),
                    "active=0", String.format("Reactivated fee structure: %s %s (%s) - ₹%s",
                            dept.getCode(), semester, feeType, feeStructure.getAmountInRupees().toPlainString()));
            return existing;
        }

        feeStructure.setSemester(semester);
        feeStructure.setFeeType(feeType);
        feeStructure.setAcademicYear(academicYear);
        feeStructure.setDueDate(feeStructure.getDueDate().trim());
        feeStructure.setActive(true);

        FeeStructure saved = feeStructureDAO.insert(feeStructure);

        auditService.log(currentUser.getId(), "CREATE_FEE_STRUCTURE", "FEE_STRUCTURE", saved.getId(),
                null, String.format("Created fee structure: %s %s (%s) - ₹%s (%s)",
                        dept.getCode(), semester, feeType, saved.getAmountInRupees().toPlainString(), academicYear));
        return saved;
    }

    /**
     * Updates an existing fee structure's amount or due date (ADMIN only).
     */
    public FeeStructure updateFeeStructure(User currentUser, FeeStructure feeStructure) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        if (feeStructure == null || feeStructure.getId() <= 0) {
            throw new ValidationException("Valid fee structure ID required.");
        }
        validateFeeStructureInput(feeStructure);

        FeeStructure current = feeStructureDAO.findById(feeStructure.getId());
        if (current == null) {
            throw new ValidationException("Fee structure " + feeStructure.getId() + " not found.");
        }

        feeStructureDAO.update(feeStructure);

        auditService.log(currentUser.getId(), "UPDATE_FEE_STRUCTURE", "FEE_STRUCTURE", feeStructure.getId(),
                String.format("Amount: ₹%s, Due: %s", current.getAmountInRupees().toPlainString(), current.getDueDate()),
                String.format("Amount: ₹%s, Due: %s", feeStructure.getAmountInRupees().toPlainString(), feeStructure.getDueDate()));
        return feeStructure;
    }

    /**
     * Soft-deactivates a fee structure (ADMIN only).
     */
    public void deleteFeeStructure(User currentUser, int feeStructureId) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        FeeStructure fs = feeStructureDAO.findById(feeStructureId);
        if (fs == null) {
            throw new ValidationException("Fee structure " + feeStructureId + " not found.");
        }
        if (!fs.isActive()) {
            throw new ValidationException("Fee structure " + feeStructureId + " is already inactive.");
        }

        feeStructureDAO.setActive(feeStructureId, false);
        auditService.log(currentUser.getId(), "DEACTIVATE_FEE_STRUCTURE", "FEE_STRUCTURE", feeStructureId,
                "active=1", "active=0 (Fee Type: " + fs.getFeeType() + ", Year: " + fs.getAcademicYear() + ")");
    }

    /**
     * Explicitly reactivates a soft-deleted fee structure (ADMIN only).
     */
    public FeeStructure reactivateFeeStructure(User currentUser, int feeStructureId) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        FeeStructure fs = feeStructureDAO.findById(feeStructureId);
        if (fs == null) {
            throw new ValidationException("Fee structure " + feeStructureId + " not found.");
        }
        if (fs.isActive()) {
            throw new ValidationException("Fee structure " + feeStructureId + " is already active.");
        }

        feeStructureDAO.setActive(feeStructureId, true);
        fs.setActive(true);
        auditService.log(currentUser.getId(), "REACTIVATE_FEE_STRUCTURE", "FEE_STRUCTURE", feeStructureId,
                "active=0", "active=1 (Fee Type: " + fs.getFeeType() + ", Year: " + fs.getAcademicYear() + ")");
        return fs;
    }

    public FeeStructure getFeeStructureById(User currentUser, int id) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        return feeStructureDAO.findById(id);
    }

    public List<FeeStructure> getFeeStructuresByDepartmentAndYear(User currentUser, int departmentId, String academicYear) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        ValidationUtil.requireNonEmpty(academicYear, "Academic year");
        return feeStructureDAO.findByDepartmentAndYear(departmentId, academicYear.trim());
    }

    public List<FeeStructure> getAllActiveFeeStructures(User currentUser) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        return feeStructureDAO.findAllActive();
    }

    public List<FeeStructure> getAllFeeStructures(User currentUser) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        return feeStructureDAO.findAll();
    }

    private void validateFeeStructureInput(FeeStructure fs) {
        if (fs == null) {
            throw new ValidationException("FeeStructure entity cannot be null.");
        }
        ValidationUtil.requireNonEmpty(fs.getSemester(), "Semester");
        ValidationUtil.requireNonEmpty(fs.getFeeType(), "Fee type");
        ValidationUtil.requireNonEmpty(fs.getAcademicYear(), "Academic year");
        ValidationUtil.requireValidIsoDate(fs.getDueDate(), "Due date");
        if (fs.getDepartmentId() <= 0) {
            throw new ValidationException("Department ID must be a positive integer.");
        }
        if (fs.getAmountPaise() <= 0L) {
            throw new ValidationException("Fee structure amount must be strictly positive.");
        }
    }
}
