package com.college.model;

import java.io.Serializable;

/**
 * Model representing an intentional manual attendance override during session review.
 * Enforces mandatory justification remarks for any deviation from the automated check-in status.
 */
public class AttendanceCorrection implements Serializable {

    private static final long serialVersionUID = 1L;

    private int studentId;
    private int enrollmentId;
    private String initialStatus; // Proposed status before override: PRESENT, ABSENT, LATE
    private String status;        // Final corrected status: PRESENT, ABSENT, LATE
    private String remarks;

    public AttendanceCorrection() {}

    public AttendanceCorrection(int studentId, int enrollmentId, String status, String remarks) {
        this(studentId, enrollmentId, null, status, remarks);
    }

    public AttendanceCorrection(int studentId, int enrollmentId, String initialStatus, String status, String remarks) {
        this.studentId = studentId;
        this.enrollmentId = enrollmentId;
        this.initialStatus = initialStatus;
        this.status = status;
        this.remarks = remarks;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public int getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(int enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public String getInitialStatus() {
        return initialStatus;
    }

    public void setInitialStatus(String initialStatus) {
        this.initialStatus = initialStatus;
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
}

