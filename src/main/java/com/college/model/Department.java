package com.college.model;

/**
 * Domain entity representing an academic department.
 * Maps to the {@code departments} table in the database schema.
 */
public class Department {

    private int id;
    private String code;
    private String name;
    private String school = "School of Engineering";
    private String description;
    private String headOfDepartment;
    private String contactEmail;
    private String contactPhone;
    private boolean active = true;
    private String createdAt;

    // Transient statistical properties
    private int programCount;
    private int facultyCount;
    private int studentCount;
    private int courseCount;

    /**
     * Default no-argument constructor.
     */
    public Department() {
    }

    /**
     * Convenience constructor for creating a new department.
     *
     * @param code department code (e.g., "CS", "EC")
     * @param name full department name
     */
    public Department(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * Constructor with code, name, and school.
     */
    public Department(String code, String name, String school) {
        this.code = code;
        this.name = name;
        this.school = school != null ? school : "School of Engineering";
    }

    public Department(String code, String name, String school, String description,
                      String headOfDepartment, String contactEmail, String contactPhone) {
        this.code = code;
        this.name = name;
        this.school = school != null ? school : "School of Engineering";
        this.description = description;
        this.headOfDepartment = headOfDepartment;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
    }

    /**
     * Constructor mapping core columns.
     */
    public Department(int id, String code, String name, boolean active, String createdAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.active = active;
        this.createdAt = createdAt;
    }

    /**
     * Full constructor mapping all database columns.
     */
    public Department(int id, String code, String name, String school, String description,
                      String headOfDepartment, String contactEmail, String contactPhone,
                      boolean active, String createdAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.school = school != null ? school : "School of Engineering";
        this.description = description;
        this.headOfDepartment = headOfDepartment;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.active = active;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSchool() {
        return school;
    }

    public void setSchool(String school) {
        this.school = (school != null && !school.trim().isEmpty()) ? school.trim() : "School of Engineering";
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getHeadOfDepartment() {
        return headOfDepartment;
    }

    public void setHeadOfDepartment(String headOfDepartment) {
        this.headOfDepartment = headOfDepartment;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
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

    public int getProgramCount() {
        return programCount;
    }

    public void setProgramCount(int programCount) {
        this.programCount = programCount;
    }

    public int getFacultyCount() {
        return facultyCount;
    }

    public void setFacultyCount(int facultyCount) {
        this.facultyCount = facultyCount;
    }

    public int getStudentCount() {
        return studentCount;
    }

    public void setStudentCount(int studentCount) {
        this.studentCount = studentCount;
    }

    public int getCourseCount() {
        return courseCount;
    }

    public void setCourseCount(int courseCount) {
        this.courseCount = courseCount;
    }

    @Override
    public String toString() {
        return "Department{" +
                "id=" + id +
                ", code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", school='" + school + '\'' +
                ", headOfDepartment='" + headOfDepartment + '\'' +
                ", active=" + active +
                ", createdAt='" + createdAt + '\'' +
                '}';
    }
}

