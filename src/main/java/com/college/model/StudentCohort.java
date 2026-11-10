package com.college.model;

import java.io.Serializable;

/**
 * Domain entity representing an academic student cohort admitted into a particular programme and academic year.
 * Maps to the {@code student_cohorts} table in the database schema.
 */
public class StudentCohort implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String cohortCode;
    private int programId;
    private String admissionYear;
    private String expectedGraduationYear;
    private int currentSemester = 1;
    private String status = "ACTIVE"; // ACTIVE, GRADUATED, ARCHIVED
    private String createdAt;

    // Transient fields from joins
    private String programName;
    private String programCode;
    private String degree;
    private int studentCount;

    public StudentCohort() {}

    public StudentCohort(String cohortCode, int programId, String admissionYear, String expectedGraduationYear) {
        this(cohortCode, programId, admissionYear, expectedGraduationYear, 1, "ACTIVE");
    }

    public StudentCohort(String cohortCode, int programId, String admissionYear, String expectedGraduationYear,
                         int currentSemester, String status) {
        this.cohortCode = cohortCode;
        this.programId = programId;
        this.admissionYear = admissionYear;
        this.expectedGraduationYear = expectedGraduationYear;
        this.currentSemester = currentSemester;
        this.status = status;
    }

    public StudentCohort(int id, String cohortCode, int programId, String admissionYear,
                         String expectedGraduationYear, int currentSemester, String status, String createdAt) {
        this.id = id;
        this.cohortCode = cohortCode;
        this.programId = programId;
        this.admissionYear = admissionYear;
        this.expectedGraduationYear = expectedGraduationYear;
        this.currentSemester = currentSemester;
        this.status = status;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getCohortCode() { return cohortCode; }
    public void setCohortCode(String cohortCode) { this.cohortCode = cohortCode; }

    public int getProgramId() { return programId; }
    public void setProgramId(int programId) { this.programId = programId; }

    public String getAdmissionYear() { return admissionYear; }
    public void setAdmissionYear(String admissionYear) { this.admissionYear = admissionYear; }

    public String getExpectedGraduationYear() { return expectedGraduationYear; }
    public void setExpectedGraduationYear(String expectedGraduationYear) { this.expectedGraduationYear = expectedGraduationYear; }

    public int getCurrentSemester() { return currentSemester; }
    public void setCurrentSemester(int currentSemester) { this.currentSemester = currentSemester; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getProgramName() { return programName; }
    public void setProgramName(String programName) { this.programName = programName; }

    public String getProgramCode() { return programCode; }
    public void setProgramCode(String programCode) { this.programCode = programCode; }

    public String getDegree() { return degree; }
    public void setDegree(String degree) { this.degree = degree; }

    public int getStudentCount() { return studentCount; }
    public void setStudentCount(int studentCount) { this.studentCount = studentCount; }

    @Override
    public String toString() {
        return cohortCode + " (" + admissionYear + " - " + expectedGraduationYear + ", Sem " + currentSemester + ")";
    }
}

