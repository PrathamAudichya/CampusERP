package com.college.model;

/**
 * Data Transfer Object representing a comprehensive user entry in the Institutional User Directory.
 * Aggregates core account authentication attributes (users table) with linked academic identity
 * (students/faculty and departments) via non-multiplying LEFT JOINs.
 */
public class UserDirectoryItem {

    private int userId;
    private String username;
    private Role role;
    private boolean mustChangePassword;
    private boolean active;
    private String createdAt;
    private String updatedAt;

    // Joined Profile Details
    private String naturalId;
    private String fullName;
    private String email;
    private String phone;
    private String departmentCode;
    private String departmentName;
    private String semester;
    private String specialization;
    private boolean accountCreated = true;

    public UserDirectoryItem() {
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public boolean isActive() {
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

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getNaturalId() {
        return naturalId;
    }

    public void setNaturalId(String naturalId) {
        this.naturalId = naturalId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public void setDepartmentCode(String departmentCode) {
        this.departmentCode = departmentCode;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public String getSpecialization() {
        return specialization;
    }

    public void setSpecialization(String specialization) {
        this.specialization = specialization;
    }

    /**
     * Returns a human-friendly display natural ID.
     */
    public String getDisplayNaturalId() {
        if (naturalId != null && !naturalId.isBlank()) {
            return naturalId;
        }
        if (role == Role.ADMIN) {
            return "ADM-" + String.format("%03d", userId);
        }
        return "—";
    }

    /**
     * Returns a human-friendly display department.
     */
    public String getDisplayDepartment() {
        if (departmentCode != null && !departmentCode.isBlank()) {
            return departmentCode + " (" + (departmentName != null ? departmentName : "") + ")";
        }
        if (role == Role.ADMIN) {
            return "Central Administration";
        }
        return "Unassigned";
    }

    public boolean isAccountCreated() {
        return accountCreated;
    }

    public void setAccountCreated(boolean accountCreated) {
        this.accountCreated = accountCreated;
    }

    public String getDisplayAccountCreationStatus() {
        return accountCreated ? "YES" : "NO";
    }
}

