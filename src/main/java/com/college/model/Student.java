package com.college.model;

/**
 * Domain entity representing an enrolled student.
 * Maps to the {@code students} table in the database schema.
 */
public class Student {

    private int id;
    private int userId;
    private int departmentId;
    private Integer programId;
    private Integer cohortId;
    private String studentId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String dateOfBirth;
    private String admissionDate;
    private String admissionYear;
    private String expectedGraduationYear;
    private String semester;
    private boolean active = true;
    private String createdAt;
    private String updatedAt;

    // Transient display properties populated from joins
    private String departmentName;
    private String fullName;
    private String programName;
    private String programCode;
    private String degree;
    private String cohortCode;
    private String schoolName;

    /**
     * Default no-argument constructor.
     */
    public Student() {
    }

    /**
     * Convenience constructor for student registration.
     *
     * @param userId        linked user account ID
     * @param departmentId  academic department ID
     * @param studentId     natural registration ID (e.g., "STU2026001")
     * @param firstName     student first name
     * @param lastName      student last name
     * @param email         institutional email address
     * @param phone         contact phone number
     * @param dateOfBirth   date of birth (YYYY-MM-DD)
     * @param admissionDate date of admission (YYYY-MM-DD)
     * @param semester      current semester designation
     */
    public Student(int userId, int departmentId, String studentId, String firstName, String lastName,
                   String email, String phone, String dateOfBirth, String admissionDate, String semester) {
        this.userId = userId;
        this.departmentId = departmentId;
        this.studentId = studentId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.dateOfBirth = dateOfBirth;
        this.admissionDate = admissionDate;
        this.semester = semester;
    }

    /**
     * Full constructor mapping all database columns.
     */
    public Student(int id, int userId, int departmentId, String studentId, String firstName, String lastName,
                   String email, String phone, String dateOfBirth, String admissionDate, String semester,
                   boolean active, String createdAt, String updatedAt) {
        this.id = id;
        this.userId = userId;
        this.departmentId = departmentId;
        this.studentId = studentId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.dateOfBirth = dateOfBirth;
        this.admissionDate = admissionDate;
        this.semester = semester;
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

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
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

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getAdmissionDate() {
        return admissionDate;
    }

    public void setAdmissionDate(String admissionDate) {
        this.admissionDate = admissionDate;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
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

    /**
     * Returns full name. If not explicitly set via transient field,
     * computes it dynamically from firstName and lastName.
     *
     * @return full student name
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

    public Integer getProgramId() { return programId; }
    public void setProgramId(Integer programId) { this.programId = programId; }

    public Integer getCohortId() { return cohortId; }
    public void setCohortId(Integer cohortId) { this.cohortId = cohortId; }

    public String getAdmissionYear() { return admissionYear; }
    public void setAdmissionYear(String admissionYear) { this.admissionYear = admissionYear; }

    public String getExpectedGraduationYear() { return expectedGraduationYear; }
    public void setExpectedGraduationYear(String expectedGraduationYear) { this.expectedGraduationYear = expectedGraduationYear; }

    public String getProgramName() { return programName; }
    public void setProgramName(String programName) { this.programName = programName; }

    public String getProgramCode() { return programCode; }
    public void setProgramCode(String programCode) { this.programCode = programCode; }

    public String getDegree() { return degree; }
    public void setDegree(String degree) { this.degree = degree; }

    public String getCohortCode() { return cohortCode; }
    public void setCohortCode(String cohortCode) { this.cohortCode = cohortCode; }

    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }

    @Override
    public String toString() {
        return "Student{" +
                "id=" + id +
                ", userId=" + userId +
                ", departmentId=" + departmentId +
                ", studentId='" + studentId + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", dateOfBirth='" + dateOfBirth + '\'' +
                ", admissionDate='" + admissionDate + '\'' +
                ", semester='" + semester + '\'' +
                ", active=" + active +
                ", createdAt='" + createdAt + '\'' +
                ", updatedAt='" + updatedAt + '\'' +
                ", departmentName='" + departmentName + '\'' +
                ", fullName='" + getFullName() + '\'' +
                '}';
    }
}

