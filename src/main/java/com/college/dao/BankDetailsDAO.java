package com.college.dao;

import com.college.config.DBConnection;
import com.college.exception.DataAccessException;
import com.college.model.InstitutionBankDetails;

import java.sql.*;

/**
 * Pure JDBC DAO for institution bank and payment details.
 */
public class BankDetailsDAO {

    public InstitutionBankDetails getBankDetails() {
        try (Connection conn = DBConnection.getConnection()) {
            return getBankDetails(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load bank details: " + e.getMessage(), e);
        }
    }

    public InstitutionBankDetails getBankDetails(Connection conn) throws SQLException {
        String sql = "SELECT id, beneficiary_name, bank_name, branch_name, account_number, " +
                     "ifsc_code, account_type, upi_id, instructions, updated_at " +
                     "FROM institution_bank_details ORDER BY id ASC LIMIT 1";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                InstitutionBankDetails b = new InstitutionBankDetails();
                b.setId(rs.getInt("id"));
                b.setBeneficiaryName(rs.getString("beneficiary_name"));
                b.setBankName(rs.getString("bank_name"));
                b.setBranchName(rs.getString("branch_name"));
                b.setAccountNumber(rs.getString("account_number"));
                b.setIfscCode(rs.getString("ifsc_code"));
                b.setAccountType(rs.getString("account_type"));
                b.setUpiId(rs.getString("upi_id"));
                b.setInstructions(rs.getString("instructions"));
                b.setUpdatedAt(rs.getString("updated_at"));
                return b;
            }
        }
        return null;
    }

    public void saveOrUpdateBankDetails(InstitutionBankDetails details) {
        try (Connection conn = DBConnection.getConnection()) {
            saveOrUpdateBankDetails(conn, details);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save bank details: " + e.getMessage(), e);
        }
    }

    public void saveOrUpdateBankDetails(Connection conn, InstitutionBankDetails details) throws SQLException {
        InstitutionBankDetails existing = getBankDetails(conn);
        if (existing == null) {
            String sql = "INSERT INTO institution_bank_details (beneficiary_name, bank_name, branch_name, " +
                         "account_number, ifsc_code, account_type, upi_id, instructions) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, details.getBeneficiaryName());
                ps.setString(2, details.getBankName());
                ps.setString(3, details.getBranchName());
                ps.setString(4, details.getAccountNumber());
                ps.setString(5, details.getIfscCode());
                ps.setString(6, details.getAccountType());
                ps.setString(7, details.getUpiId());
                ps.setString(8, details.getInstructions());
                ps.executeUpdate();
                try (ResultSet gk = ps.getGeneratedKeys()) {
                    if (gk.next()) details.setId(gk.getInt(1));
                }
            }
        } else {
            String sql = "UPDATE institution_bank_details SET beneficiary_name = ?, bank_name = ?, branch_name = ?, " +
                         "account_number = ?, ifsc_code = ?, account_type = ?, upi_id = ?, instructions = ?, " +
                         "updated_at = CURRENT_TIMESTAMP WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, details.getBeneficiaryName());
                ps.setString(2, details.getBankName());
                ps.setString(3, details.getBranchName());
                ps.setString(4, details.getAccountNumber());
                ps.setString(5, details.getIfscCode());
                ps.setString(6, details.getAccountType());
                ps.setString(7, details.getUpiId());
                ps.setString(8, details.getInstructions());
                ps.setInt(9, existing.getId());
                ps.executeUpdate();
                details.setId(existing.getId());
            }
        }
    }
}

