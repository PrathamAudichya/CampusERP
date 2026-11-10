package com.college.model;

import java.io.Serializable;

/**
 * Domain entity representing an individual semester, research term, or academic milestone
 * within a configured Academic Programme structure.
 */
public class ProgrammeSemester implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private int programId;
    private int semesterNumber;
    private String semesterLabel;
    private String termType = "SEMESTER"; // SEMESTER, COURSEWORK, RESEARCH_TERM, THESIS
    private int creditsTarget = 20;
    private String createdAt;

    public ProgrammeSemester() {}

    public ProgrammeSemester(int programId, int semesterNumber, String semesterLabel) {
        this(programId, semesterNumber, semesterLabel, "SEMESTER", 20);
    }

    public ProgrammeSemester(int programId, int semesterNumber, String semesterLabel, String termType, int creditsTarget) {
        this.programId = programId;
        this.semesterNumber = semesterNumber;
        this.semesterLabel = semesterLabel;
        this.termType = termType;
        this.creditsTarget = creditsTarget;
    }

    public ProgrammeSemester(int id, int programId, int semesterNumber, String semesterLabel,
                             String termType, int creditsTarget, String createdAt) {
        this.id = id;
        this.programId = programId;
        this.semesterNumber = semesterNumber;
        this.semesterLabel = semesterLabel;
        this.termType = termType;
        this.creditsTarget = creditsTarget;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getProgramId() { return programId; }
    public void setProgramId(int programId) { this.programId = programId; }

    public int getSemesterNumber() { return semesterNumber; }
    public void setSemesterNumber(int semesterNumber) { this.semesterNumber = semesterNumber; }

    public String getSemesterLabel() { return semesterLabel; }
    public void setSemesterLabel(String semesterLabel) { this.semesterLabel = semesterLabel; }

    public String getTermType() { return termType; }
    public void setTermType(String termType) { this.termType = termType; }

    public int getCreditsTarget() { return creditsTarget; }
    public void setCreditsTarget(int creditsTarget) { this.creditsTarget = creditsTarget; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return semesterLabel + " (" + termType + ", " + creditsTarget + " credits)";
    }
}

