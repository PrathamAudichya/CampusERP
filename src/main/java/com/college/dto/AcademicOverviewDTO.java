package com.college.dto;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data Transfer Object encapsulating aggregate metrics for the Academic Overview dashboard.
 */
public class AcademicOverviewDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private int totalSchools;
    private int totalDepartments;
    private int activeDepartments;
    private int totalPrograms;
    private int activePrograms;
    private int totalStudents;

    // Distribution maps
    private Map<String, Integer> programStudentDistribution = new LinkedHashMap<>();
    private Map<String, Integer> semesterStudentDistribution = new LinkedHashMap<>();
    private Map<String, Integer> departmentFacultyDistribution = new LinkedHashMap<>();
    private Map<String, Integer> cohortStudentDistribution = new LinkedHashMap<>();

    public AcademicOverviewDTO() {}

    public int getTotalSchools() { return totalSchools; }
    public void setTotalSchools(int totalSchools) { this.totalSchools = totalSchools; }

    public int getTotalDepartments() { return totalDepartments; }
    public void setTotalDepartments(int totalDepartments) { this.totalDepartments = totalDepartments; }

    public int getActiveDepartments() { return activeDepartments; }
    public void setActiveDepartments(int activeDepartments) { this.activeDepartments = activeDepartments; }

    public int getTotalPrograms() { return totalPrograms; }
    public void setTotalPrograms(int totalPrograms) { this.totalPrograms = totalPrograms; }

    public int getActivePrograms() { return activePrograms; }
    public void setActivePrograms(int activePrograms) { this.activePrograms = activePrograms; }

    public int getTotalStudents() { return totalStudents; }
    public void setTotalStudents(int totalStudents) { this.totalStudents = totalStudents; }

    public Map<String, Integer> getProgramStudentDistribution() { return programStudentDistribution; }
    public void setProgramStudentDistribution(Map<String, Integer> programStudentDistribution) {
        this.programStudentDistribution = programStudentDistribution;
    }

    public Map<String, Integer> getSemesterStudentDistribution() { return semesterStudentDistribution; }
    public void setSemesterStudentDistribution(Map<String, Integer> semesterStudentDistribution) {
        this.semesterStudentDistribution = semesterStudentDistribution;
    }

    public Map<String, Integer> getDepartmentFacultyDistribution() { return departmentFacultyDistribution; }
    public void setDepartmentFacultyDistribution(Map<String, Integer> departmentFacultyDistribution) {
        this.departmentFacultyDistribution = departmentFacultyDistribution;
    }

    public Map<String, Integer> getCohortStudentDistribution() { return cohortStudentDistribution; }
    public void setCohortStudentDistribution(Map<String, Integer> cohortStudentDistribution) {
        this.cohortStudentDistribution = cohortStudentDistribution;
    }
}

