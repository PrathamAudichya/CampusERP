package com.college.model;

/**
 * Domain entity representing an academic course.
 * Maps to the {@code courses} table in the database schema.
 */
public class Course {

    private int id;
    private int departmentId;
    private String courseCode;
    private String name;
    private int credits;
    private String semester;
    private Integer maxCapacity;
    private boolean active = true;
    private String createdAt;
    private String updatedAt;

    // Transient display property populated from department join
    private String departmentName;

    /**
     * Default no-argument constructor.
     */
    public Course() {
    }

    /**
     * Convenience constructor for creating a course.
     *
     * @param departmentId offering department ID
     * @param courseCode   catalog course code (e.g., "CS101")
     * @param name         course title
     * @param credits      credit count
     * @param semester     recommended semester
     * @param maxCapacity  enrollment capacity limit (can be null for unlimited)
     */
    public Course(int departmentId, String courseCode, String name, int credits,
                  String semester, Integer maxCapacity) {
        this.departmentId = departmentId;
        this.courseCode = courseCode;
        this.name = name;
        this.credits = credits;
        this.semester = semester;
        this.maxCapacity = maxCapacity;
    }

    /**
     * Full constructor mapping all database columns.
     */
    public Course(int id, int departmentId, String courseCode, String name, int credits,
                  String semester, Integer maxCapacity, boolean active,
                  String createdAt, String updatedAt) {
        this.id = id;
        this.departmentId = departmentId;
        this.courseCode = courseCode;
        this.name = name;
        this.credits = credits;
        this.semester = semester;
        this.maxCapacity = maxCapacity;
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

    public int getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(int departmentId) {
        this.departmentId = departmentId;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCredits() {
        return credits;
    }

    public void setCredits(int credits) {
        this.credits = credits;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public Integer getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(Integer maxCapacity) {
        this.maxCapacity = maxCapacity;
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

    @Override
    public String toString() {
        return "Course{" +
                "id=" + id +
                ", departmentId=" + departmentId +
                ", courseCode='" + courseCode + '\'' +
                ", name='" + name + '\'' +
                ", credits=" + credits +
                ", semester='" + semester + '\'' +
                ", maxCapacity=" + maxCapacity +
                ", active=" + active +
                ", createdAt='" + createdAt + '\'' +
                ", updatedAt='" + updatedAt + '\'' +
                ", departmentName='" + departmentName + '\'' +
                '}';
    }
}

