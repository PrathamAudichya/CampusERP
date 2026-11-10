package com.college.model;

/**
 * Domain entity representing a student's enrollment in a course.
 * Maps to the {@code enrollments} table in the database schema.
 */
public class Enrollment {

    private int id;
    private int studentId;
    private int courseId;
    private String academicYear;
    private String enrolledDate;
    private String status = "ACTIVE";
    private String createdAt;
    private String updatedAt;

    // Transient display properties populated from joins
    private String studentName;
    private String studentIdNumber;
    private String courseCode;
    private String courseName;

    /**
     * Default no-argument constructor.
     */
    public Enrollment() {
    }

    /**
     * Convenience constructor for enrolling a student.
     *
     * @param studentId    enrolled student ID
     * @param courseId     enrolled course ID
     * @param academicYear academic year (e.g., "2026-2027")
     */
    public Enrollment(int studentId, int courseId, String academicYear) {
        this.studentId = studentId;
        this.courseId = courseId;
        this.academicYear = academicYear;
    }

    /**
     * Full constructor mapping all database columns.
     */
    public Enrollment(int id, int studentId, int courseId, String academicYear,
                      String enrolledDate, String status, String createdAt, String updatedAt) {
        this.id = id;
        this.studentId = studentId;
        this.courseId = courseId;
        this.academicYear = academicYear;
        this.enrolledDate = enrolledDate;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public int getCourseId() {
        return courseId;
    }

    public void setCourseId(int courseId) {
        this.courseId = courseId;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public String getEnrolledDate() {
        return enrolledDate;
    }

    public void setEnrolledDate(String enrolledDate) {
        this.enrolledDate = enrolledDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getStudentIdNumber() {
        return studentIdNumber;
    }

    public void setStudentIdNumber(String studentIdNumber) {
        this.studentIdNumber = studentIdNumber;
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

    @Override
    public String toString() {
        return "Enrollment{" +
                "id=" + id +
                ", studentId=" + studentId +
                ", courseId=" + courseId +
                ", academicYear='" + academicYear + '\'' +
                ", enrolledDate='" + enrolledDate + '\'' +
                ", status='" + status + '\'' +
                ", createdAt='" + createdAt + '\'' +
                ", updatedAt='" + updatedAt + '\'' +
                ", studentName='" + studentName + '\'' +
                ", studentIdNumber='" + studentIdNumber + '\'' +
                ", courseCode='" + courseCode + '\'' +
                ", courseName='" + courseName + '\'' +
                '}';
    }
}

