package com.college.model;

import java.io.Serializable;

/**
 * Domain entity recording an atomic academic semester progression action for an enrolled student.
 * Maps to the {@code student_progression_history} table in the database schema.
 */
public class StudentProgressionHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private int studentId;
    private int previousSemester;
    private int newSemester;
    private String academicYear;
    private String action; // PROMOTED, HELD, REPEATED, LATERAL_ENTRY
    private int performedBy;
    private String reason;
    private String createdAt;

    // Transient fields from joins
    private String studentName;
    private String studentRegId;
    private String performedByName;

    public StudentProgressionHistory() {}

    public StudentProgressionHistory(int studentId, int previousSemester, int newSemester,
                                     String academicYear, String action, int performedBy, String reason) {
        this.studentId = studentId;
        this.previousSemester = previousSemester;
        this.newSemester = newSemester;
        this.academicYear = academicYear;
        this.action = action;
        this.performedBy = performedBy;
        this.reason = reason;
    }

    public StudentProgressionHistory(int id, int studentId, int previousSemester, int newSemester,
                                     String academicYear, String action, int performedBy, String reason, String createdAt) {
        this.id = id;
        this.studentId = studentId;
        this.previousSemester = previousSemester;
        this.newSemester = newSemester;
        this.academicYear = academicYear;
        this.action = action;
        this.performedBy = performedBy;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }

    public int getPreviousSemester() { return previousSemester; }
    public void setPreviousSemester(int previousSemester) { this.previousSemester = previousSemester; }

    public int getNewSemester() { return newSemester; }
    public void setNewSemester(int newSemester) { this.newSemester = newSemester; }

    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public int getPerformedBy() { return performedBy; }
    public void setPerformedBy(int performedBy) { this.performedBy = performedBy; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getStudentRegId() { return studentRegId; }
    public void setStudentRegId(String studentRegId) { this.studentRegId = studentRegId; }

    public String getPerformedByName() { return performedByName; }
    public void setPerformedByName(String performedByName) { this.performedByName = performedByName; }

    @Override
    public String toString() {
        return studentRegId + ": Sem " + previousSemester + " -> " + newSemester + " (" + action + ") - " + reason;
    }
}

