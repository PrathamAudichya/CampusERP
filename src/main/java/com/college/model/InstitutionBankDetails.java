package com.college.model;

import java.io.Serializable;

/**
 * Model representing institutional bank and payment collection account details.
 */
public class InstitutionBankDetails implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String beneficiaryName;
    private String bankName;
    private String branchName;
    private String accountNumber;
    private String ifscCode;
    private String accountType;
    private String upiId;
    private String instructions;
    private String updatedAt;

    public InstitutionBankDetails() {
    }

    public InstitutionBankDetails(String beneficiaryName, String bankName, String branchName,
                                  String accountNumber, String ifscCode, String accountType,
                                  String upiId, String instructions) {
        this.beneficiaryName = beneficiaryName;
        this.bankName = bankName;
        this.branchName = branchName;
        this.accountNumber = accountNumber;
        this.ifscCode = ifscCode;
        this.accountType = accountType;
        this.upiId = upiId;
        this.instructions = instructions;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public void setBeneficiaryName(String beneficiaryName) {
        this.beneficiaryName = beneficiaryName;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    /**
     * Returns masked account number showing only the last 4 digits (e.g. XXXXXXXXXXXX1234).
     */
    public String getMaskedAccountNumber() {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return accountNumber;
        }
        int len = accountNumber.length();
        return "X".repeat(len - 4) + accountNumber.substring(len - 4);
    }

    public String getIfscCode() {
        return ifscCode;
    }

    public void setIfscCode(String ifscCode) {
        this.ifscCode = ifscCode;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getUpiId() {
        return upiId;
    }

    public void setUpiId(String upiId) {
        this.upiId = upiId;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}

