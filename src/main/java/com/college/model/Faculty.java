package com.college.model;

/**
 * Domain entity representing an academic faculty member.
 * Maps to the {@code faculty} table in the database schema.
 */
public class Faculty {

    private int id;
    private int userId;
    private int departmentId;
    private String facultyId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String specialization;
    private String joinDate;
    private boolean active = true;
    private String createdAt;
    private String updatedAt;

    // Transient display properties populated from joins
    private String departmentName;
    private String schoolName;
    private String fullName;

    /**
     * Default no-argument constructor.
     */
    public Faculty() {
    }

    /**
     * Convenience constructor for faculty registration.
     *
     * @param userId         linked user account ID
     * @param departmentId   academic department ID
     * @param facultyId      natural employee ID (e.g., "FAC2026001")
     * @param firstName      faculty first name
     * @param lastName       faculty last name
     * @param email          institutional email address
     * @param phone          contact phone number
     * @param specialization academic specialization / research area
     * @param joinDate       joining date (YYYY-MM-DD)
     */
    public Faculty(int userId, int departmentId, String facultyId, String firstName, String lastName,
                   String email, String phone, String specialization, String joinDate) {
        this.userId = userId;
        this.departmentId = departmentId;
        this.facultyId = facultyId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.specialization = specialization;
        this.joinDate = joinDate;
    }

    /**
     * Full constructor mapping all database columns.
     */
    public Faculty(int id, int userId, int departmentId, String facultyId, String firstName, String lastName,
                   String email, String phone, String specialization, String joinDate,
                   boolean active, String createdAt, String updatedAt) {
        this.id = id;
        this.userId = userId;
        this.departmentId = departmentId;
        this.facultyId = facultyId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.specialization = specialization;
        this.joinDate = joinDate;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(int departmentId) {
        this.departmentId = departmentId;
    }

    public String getFacultyId() {
        return facultyId;
    }

    public void setFacultyId(String facultyId) {
        this.facultyId = facultyId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
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

    public String getSpecialization() {
        return specialization;
    }

    public void setSpecialization(String specialization) {
        this.specialization = specialization;
    }

    public String getJoinDate() {
        return joinDate;
    }

    public void setJoinDate(String joinDate) {
        this.joinDate = joinDate;
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

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getSchoolName() {
        return schoolName;
    }

    public void setSchoolName(String schoolName) {
        this.schoolName = schoolName;
    }

    /**
     * Returns full name. If not explicitly set via transient field,
     * computes it dynamically from firstName and lastName.
     *
     * @return full faculty member name
     */
    public String getFullName() {
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        String f = firstName != null ? firstName : "";
        String l = lastName != null ? lastName : "";
        String combined = (f + " " + l).trim();
        return combined.isEmpty() ? null : combined;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    @Override
    public String toString() {
        return "Faculty{" +
                "id=" + id +
                ", userId=" + userId +
                ", departmentId=" + departmentId +
                ", facultyId='" + facultyId + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", specialization='" + specialization + '\'' +
                ", joinDate='" + joinDate + '\'' +
                ", active=" + active +
                ", createdAt='" + createdAt + '\'' +
                ", updatedAt='" + updatedAt + '\'' +
                ", departmentName='" + departmentName + '\'' +
                ", fullName='" + getFullName() + '\'' +
                '}';
    }
}

