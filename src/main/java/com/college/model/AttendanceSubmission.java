package com.college.model;

import java.io.Serializable;

/**
 * Model representing a student's check-in submission for an attendance session.
 */
public class AttendanceSubmission implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private int sessionId;
    private int studentId;
    private int enrollmentId;
    private String submittedCode;
    private String status; // ACCEPTED, INVALID_CODE, EXPIRED, ALREADY_SUBMITTED, REJECTED
    private String submissionTime;

    public AttendanceSubmission() {}

    public AttendanceSubmission(int sessionId, int studentId, int enrollmentId,
                                String submittedCode, String status) {
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.enrollmentId = enrollmentId;
        this.submittedCode = submittedCode;
        this.status = status;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getSessionId() { return sessionId; }
    public void setSessionId(int sessionId) { this.sessionId = sessionId; }

    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }

    public int getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(int enrollmentId) { this.enrollmentId = enrollmentId; }

    public String getSubmittedCode() { return submittedCode; }
    public void setSubmittedCode(String submittedCode) { this.submittedCode = submittedCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSubmissionTime() { return submissionTime; }
    public void setSubmissionTime(String submissionTime) { this.submissionTime = submissionTime; }
}

