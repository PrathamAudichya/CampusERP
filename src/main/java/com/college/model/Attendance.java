package com.college.model;

/**
 * Domain entity representing class session attendance for an enrolled student.
 * Maps to the {@code attendance} table in the database schema.
 */
public class Attendance {

    private int id;
    private int enrollmentId;
    private String attendanceDate;
    private String status;
    private String remarks;
    private String sessionCode;

    // Transient display properties populated from joins
    private String studentName;
    private String studentIdNumber;
    private String courseCode;

    /**
     * Default no-argument constructor.
     */
    public Attendance() {
    }

    /**
     * Convenience constructor for recording attendance.
     *
     * @param enrollmentId   student enrollment ID
     * @param attendanceDate session date (YYYY-MM-DD)
     * @param status         attendance status (PRESENT, ABSENT, LATE)
     * @param remarks        optional remarks/excuses
     */
    public Attendance(int enrollmentId, String attendanceDate, String status, String remarks) {
        this.enrollmentId = enrollmentId;
        this.attendanceDate = attendanceDate;
        this.status = status;
        this.remarks = remarks;
    }

    /**
     * Full constructor mapping all database columns.
     */
    public Attendance(int id, int enrollmentId, String attendanceDate, String status, String remarks) {
        this.id = id;
        this.enrollmentId = enrollmentId;
        this.attendanceDate = attendanceDate;
        this.status = status;
        this.remarks = remarks;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(int enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public String getAttendanceDate() {
        return attendanceDate;
    }

    public void setAttendanceDate(String attendanceDate) {
        this.attendanceDate = attendanceDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
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

    public String getSessionCode() {
        return sessionCode;
    }

    public void setSessionCode(String sessionCode) {
        this.sessionCode = sessionCode;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    @Override
    public String toString() {
        return "Attendance{" +
                "id=" + id +
                ", enrollmentId=" + enrollmentId +
                ", attendanceDate='" + attendanceDate + '\'' +
                ", status='" + status + '\'' +
                ", remarks='" + remarks + '\'' +
                ", studentName='" + studentName + '\'' +
                ", studentIdNumber='" + studentIdNumber + '\'' +
                ", courseCode='" + courseCode + '\'' +
                '}';
    }
}

