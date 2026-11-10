package com.college.model;

import java.io.Serializable;

/**
 * Model representing an interactive attendance check-in session created by faculty.
 */
public class AttendanceSession implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private int courseId;
    private int facultyId;
    private String sessionDate;
    private String sessionCode;
    private String academicYear;
    private String expiresAt;
    private String status; // OPEN, CLOSED, FINALIZED
    private String sessionNumber = "1";
    private String finalizedAt;
    private Integer finalizedBy;
    private String createdAt;

    public AttendanceSession() {}

    public AttendanceSession(int courseId, int facultyId, String sessionDate, String sessionCode,
                             String academicYear, String expiresAt, String status) {
        this(courseId, facultyId, sessionDate, sessionCode, academicYear, expiresAt, status, "1");
    }

    public AttendanceSession(int courseId, int facultyId, String sessionDate, String sessionCode,
                             String academicYear, String expiresAt, String status, String sessionNumber) {
        this.courseId = courseId;
        this.facultyId = facultyId;
        this.sessionDate = sessionDate;
        this.sessionCode = sessionCode;
        this.academicYear = academicYear;
        this.expiresAt = expiresAt;
        this.status = status;
        this.sessionNumber = sessionNumber != null ? sessionNumber : "1";
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getCourseId() { return courseId; }
    public void setCourseId(int courseId) { this.courseId = courseId; }

    public int getFacultyId() { return facultyId; }
    public void setFacultyId(int facultyId) { this.facultyId = facultyId; }

    public String getSessionDate() { return sessionDate; }
    public void setSessionDate(String sessionDate) { this.sessionDate = sessionDate; }

    public String getSessionCode() { return sessionCode; }
    public void setSessionCode(String sessionCode) { this.sessionCode = sessionCode; }

    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }

    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSessionNumber() { return sessionNumber; }
    public void setSessionNumber(String sessionNumber) { this.sessionNumber = sessionNumber; }

    public String getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(String finalizedAt) { this.finalizedAt = finalizedAt; }

    public Integer getFinalizedBy() { return finalizedBy; }
    public void setFinalizedBy(Integer finalizedBy) { this.finalizedBy = finalizedBy; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}

