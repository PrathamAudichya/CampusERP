package com.college.service;

import com.college.dao.BankDetailsDAO;
import com.college.exception.ValidationException;
import com.college.model.InstitutionBankDetails;
import com.college.model.Role;
import com.college.model.User;

/**
 * Service for managing institutional bank accounts and student payment instructions.
 */
public class BankDetailsService {

    private final BankDetailsDAO bankDetailsDAO;
    private final AuditService auditService;

    public BankDetailsService() {
        this(new BankDetailsDAO(), new AuditService());
    }

    public BankDetailsService(BankDetailsDAO bankDetailsDAO, AuditService auditService) {
        this.bankDetailsDAO = bankDetailsDAO;
        this.auditService = auditService;
    }

    /**
     * Retrieves the institutional bank account details.
     * Accessible by all authenticated roles (Admin, Faculty, Student).
     */
    public InstitutionBankDetails getBankDetails(User currentUser) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY, Role.STUDENT);
        InstitutionBankDetails details = bankDetailsDAO.getBankDetails();
        if (details == null) {
            // Return default institutional details
            details = new InstitutionBankDetails(
                    "CAMPUS ERP INSTITUTIONAL TRUST",
                    "State Bank of India",
                    "University Campus Branch",
                    "38920194829104",
                    "SBIN0001234",
                    "Current Account",
                    "campuserp@sbi",
                    "Please mention Student Registration Number in NEFT/IMPS/UPI remarks. Retain UTR receipt for accounting verification."
            );
        }
        return details;
    }

    /**
     * Updates institutional bank details (ADMIN only).
     */
    public void updateBankDetails(User currentUser, InstitutionBankDetails details) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        if (details == null) throw new ValidationException("Bank details cannot be null.");
        if (details.getBeneficiaryName() == null || details.getBeneficiaryName().trim().isEmpty()) {
            throw new ValidationException("Beneficiary name is required.");
        }
        if (details.getAccountNumber() == null || details.getAccountNumber().trim().isEmpty()) {
            throw new ValidationException("Account number is required.");
        }
        if (details.getIfscCode() == null || details.getIfscCode().trim().isEmpty()) {
            throw new ValidationException("IFSC code is required.");
        }

        bankDetailsDAO.saveOrUpdateBankDetails(details);
        auditService.log(currentUser.getId(), "UPDATE_BANK_DETAILS", "INSTITUTION_BANK", details.getId(),
                null, "Administrator updated institutional bank and UPI details: " + details.getBankName() + " (" + details.getIfscCode() + ")");
    }
}

