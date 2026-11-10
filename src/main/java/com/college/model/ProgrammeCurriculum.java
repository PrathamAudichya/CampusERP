package com.college.model;

import java.io.Serializable;

/**
 * Domain entity mapping a course to a specific semester and regulation scheme of an Academic Programme.
 * Maps to the {@code programme_curriculum} table in the database schema.
 */
public class ProgrammeCurriculum implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private int programId;
    private int semesterNumber;
    private int courseId;
    private String courseCategory = "CORE"; // CORE, ELECTIVE, OPEN_ELECTIVE, LABORATORY, PROJECT, INTERNSHIP, SEMINAR, RESEARCH
    private boolean isMandatory = true;
    private String academicRegulation = "R2026";
    private Integer prerequisiteCourseId;
    private boolean active = true;
    private String createdAt;

    // Transient fields from joins
    private String courseCode;
    private String courseName;
    private int credits;
    private String prerequisiteCode;
    private String prerequisiteName;

    public ProgrammeCurriculum() {}

    public ProgrammeCurriculum(int programId, int semesterNumber, int courseId, String courseCategory, boolean isMandatory) {
        this.programId = programId;
        this.semesterNumber = semesterNumber;
        this.courseId = courseId;
        this.courseCategory = courseCategory;
        this.isMandatory = isMandatory;
    }

    public ProgrammeCurriculum(int programId, int semesterNumber, int courseId,
                               String courseCategory, boolean isMandatory,
                               String academicRegulation, Integer prerequisiteCourseId) {
        this.programId = programId;
        this.semesterNumber = semesterNumber;
        this.courseId = courseId;
        this.courseCategory = courseCategory;
        this.isMandatory = isMandatory;
        this.academicRegulation = academicRegulation;
        this.prerequisiteCourseId = prerequisiteCourseId;
    }

    public ProgrammeCurriculum(int id, int programId, int semesterNumber, int courseId,
                               String courseCategory, boolean isMandatory, String academicRegulation,
                               Integer prerequisiteCourseId, boolean active, String createdAt) {
        this.id = id;
        this.programId = programId;
        this.semesterNumber = semesterNumber;
        this.courseId = courseId;
        this.courseCategory = courseCategory;
        this.isMandatory = isMandatory;
        this.academicRegulation = academicRegulation;
        this.prerequisiteCourseId = prerequisiteCourseId;
        this.active = active;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getProgramId() { return programId; }
    public void setProgramId(int programId) { this.programId = programId; }

    public int getSemesterNumber() { return semesterNumber; }
    public void setSemesterNumber(int semesterNumber) { this.semesterNumber = semesterNumber; }

    public int getCourseId() { return courseId; }
    public void setCourseId(int courseId) { this.courseId = courseId; }

    public String getCourseCategory() { return courseCategory; }
    public void setCourseCategory(String courseCategory) { this.courseCategory = courseCategory; }

    public boolean isMandatory() { return isMandatory; }
    public void setMandatory(boolean mandatory) { isMandatory = mandatory; }

    public String getAcademicRegulation() { return academicRegulation; }
    public void setAcademicRegulation(String academicRegulation) { this.academicRegulation = academicRegulation; }

    public Integer getPrerequisiteCourseId() { return prerequisiteCourseId; }
    public void setPrerequisiteCourseId(Integer prerequisiteCourseId) { this.prerequisiteCourseId = prerequisiteCourseId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public int getCredits() { return credits; }
    public void setCredits(int credits) { this.credits = credits; }

    public String getPrerequisiteCode() { return prerequisiteCode; }
    public void setPrerequisiteCode(String prerequisiteCode) { this.prerequisiteCode = prerequisiteCode; }

    public String getPrerequisiteName() { return prerequisiteName; }
    public void setPrerequisiteName(String prerequisiteName) { this.prerequisiteName = prerequisiteName; }

    @Override
    public String toString() {
        return courseCode + " - " + courseName + " (" + courseCategory + ", Sem " + semesterNumber + ")";
    }
}
