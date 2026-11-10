package com.college.model;

import java.io.Serializable;

/**
 * Domain entity representing an academic degree or qualification programme.
 * Maps to the {@code academic_programs} table in the database schema.
 */
public class AcademicProgram implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private int departmentId;
    private String name;
    private String code;
    private String degree;
    private String level = "UNDERGRADUATE"; // UNDERGRADUATE, POSTGRADUATE, RESEARCH, DIPLOMA
    private int durationYears = 4;
    private int totalSemesters = 8;
    private String description;
    private int creditsRequired = 160;
    private String academicRegulation = "R2026";
    private boolean isResearch = false;
    private boolean active = true;
    private String createdAt;

    // Transient fields populated from joins
    private String departmentName;
    private String departmentCode;
    private String schoolName;
    private int enrolledCount;

    public AcademicProgram() {}

    public AcademicProgram(int departmentId, String name, String code, String degree,
                           String level, int durationYears, int totalSemesters) {
        this.departmentId = departmentId;
        this.name = name;
        this.code = code;
        this.degree = degree;
        this.level = level;
        this.durationYears = durationYears;
        this.totalSemesters = totalSemesters;
        this.isResearch = "RESEARCH".equalsIgnoreCase(level);
    }

    public AcademicProgram(int id, int departmentId, String name, String code, String degree,
                           String level, int durationYears, int totalSemesters, String description,
                           int creditsRequired, String academicRegulation, boolean isResearch,
                           boolean active, String createdAt) {
        this.id = id;
        this.departmentId = departmentId;
        this.name = name;
        this.code = code;
        this.degree = degree;
        this.level = level;
        this.durationYears = durationYears;
        this.totalSemesters = totalSemesters;
        this.description = description;
        this.creditsRequired = creditsRequired;
        this.academicRegulation = academicRegulation;
        this.isResearch = isResearch;
        this.active = active;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getDepartmentId() { return departmentId; }
    public void setDepartmentId(int departmentId) { this.departmentId = departmentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDegree() { return degree; }
    public void setDegree(String degree) { this.degree = degree; }

    public String getLevel() { return level; }
    public void setLevel(String level) {
        this.level = level;
        if ("RESEARCH".equalsIgnoreCase(level)) {
            this.isResearch = true;
        }
    }

    public int getDurationYears() { return durationYears; }
    public void setDurationYears(int durationYears) { this.durationYears = durationYears; }

    public int getTotalSemesters() { return totalSemesters; }
    public void setTotalSemesters(int totalSemesters) { this.totalSemesters = totalSemesters; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getCreditsRequired() { return creditsRequired; }
    public void setCreditsRequired(int creditsRequired) { this.creditsRequired = creditsRequired; }

    public String getAcademicRegulation() { return academicRegulation; }
    public void setAcademicRegulation(String academicRegulation) { this.academicRegulation = academicRegulation; }

    public boolean isResearch() { return isResearch; }
    public void setResearch(boolean research) { isResearch = research; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public String getDepartmentCode() { return departmentCode; }
    public void setDepartmentCode(String departmentCode) { this.departmentCode = departmentCode; }

    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }
    public String getDepartmentSchool() { return schoolName; }
    public void setDepartmentSchool(String school) { this.schoolName = school; }

    public int getEnrolledCount() { return enrolledCount; }
    public void setEnrolledCount(int enrolledCount) { this.enrolledCount = enrolledCount; }

    @Override
    public String toString() {
        return code + " - " + name + " (" + degree + ")";
    }
}
