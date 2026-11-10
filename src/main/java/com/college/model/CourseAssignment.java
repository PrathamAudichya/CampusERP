package com.college.model;

/**
 * Domain entity representing an instructor assignment to a course.
 * Maps to the {@code course_assignments} table in the database schema.
 */
public class CourseAssignment {

    private int id;
    private int courseId;
    private int facultyId;
    private String academicYear;
    private boolean active = true;
    private String assignedDate;

    // Transient display properties populated from joins
    private String courseCode;
    private String courseName;
    private String facultyName;

    /**
     * Default no-argument constructor.
     */
    public CourseAssignment() {
    }

    /**
     * Convenience constructor for assigning faculty to a course.
     *
     * @param courseId     assigned course ID
     * @param facultyId    assigned faculty member ID
     * @param academicYear academic year (e.g., "2026-2027")
     */
    public CourseAssignment(int courseId, int facultyId, String academicYear) {
        this.courseId = courseId;
        this.facultyId = facultyId;
        this.academicYear = academicYear;
    }

    /**
     * Full constructor mapping all database columns.
     */
    public CourseAssignment(int id, int courseId, int facultyId, String academicYear,
                            boolean active, String assignedDate) {
        this.id = id;
        this.courseId = courseId;
        this.facultyId = facultyId;
        this.academicYear = academicYear;
        this.active = active;
        this.assignedDate = assignedDate;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getCourseId() {
        return courseId;
    }

    public void setCourseId(int courseId) {
        this.courseId = courseId;
    }

    public int getFacultyId() {
        return facultyId;
    }

    public void setFacultyId(int facultyId) {
        this.facultyId = facultyId;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
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

    public String getAssignedDate() {
        return assignedDate;
    }

    public void setAssignedDate(String assignedDate) {
        this.assignedDate = assignedDate;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getFacultyName() {
        return facultyName;
    }

    public void setFacultyName(String facultyName) {
        this.facultyName = facultyName;
    }

    @Override
    public String toString() {
        return "CourseAssignment{" +
                "id=" + id +
                ", courseId=" + courseId +
                ", facultyId=" + facultyId +
                ", academicYear='" + academicYear + '\'' +
                ", active=" + active +
                ", assignedDate='" + assignedDate + '\'' +
                ", courseCode='" + courseCode + '\'' +
                ", courseName='" + courseName + '\'' +
                ", facultyName='" + facultyName + '\'' +
                '}';
    }
}

