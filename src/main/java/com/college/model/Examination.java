package com.college.model;

/**
 * Domain entity representing an examination / assessment.
 * Maps to the {@code examinations} table in the database schema.
 */
public class Examination {

    private int id;
    private int courseId;
    private String examName;
    private String examType;
    private String examDate;
    private int totalMarks;
    private String academicYear;
    private boolean active = true;
    private String createdAt;

    // Transient display properties populated from joins
    private String courseCode;
    private String courseName;

    /**
     * Default no-argument constructor.
     */
    public Examination() {
    }

    /**
     * Convenience constructor for scheduling an examination.
     *
     * @param courseId     examined course ID
     * @param examName     assessment title (e.g., "Midterm Exam")
     * @param examType     assessment category (MIDTERM, FINAL, QUIZ, ASSIGNMENT)
     * @param examDate     scheduled date (YYYY-MM-DD)
     * @param totalMarks   maximum marks achievable
     * @param academicYear academic year (e.g., "2026-2027")
     */
    public Examination(int courseId, String examName, String examType, String examDate,
                       int totalMarks, String academicYear) {
        this.courseId = courseId;
        this.examName = examName;
        this.examType = examType;
        this.examDate = examDate;
        this.totalMarks = totalMarks;
        this.academicYear = academicYear;
    }

    /**
     * Full constructor mapping all database columns.
     */
    public Examination(int id, int courseId, String examName, String examType, String examDate,
                       int totalMarks, String academicYear, boolean active, String createdAt) {
        this.id = id;
        this.courseId = courseId;
        this.examName = examName;
        this.examType = examType;
        this.examDate = examDate;
        this.totalMarks = totalMarks;
        this.academicYear = academicYear;
        this.active = active;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getCourseId() {
        return courseId;
    }

    public void setCourseId(int courseId) {
        this.courseId = courseId;
    }

    public String getExamName() {
        return examName;
    }

    public void setExamName(String examName) {
        this.examName = examName;
    }

    public String getExamType() {
        return examType;
    }

    public void setExamType(String examType) {
        this.examType = examType;
    }

    public String getExamDate() {
        return examDate;
    }

    public void setExamDate(String examDate) {
        this.examDate = examDate;
    }

    public int getTotalMarks() {
        return totalMarks;
    }

    public void setTotalMarks(int totalMarks) {
        this.totalMarks = totalMarks;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public boolean isActive() {
        return active;
    }

    public boolean getActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    @Override
    public String toString() {
        return "Examination{" +
                "id=" + id +
                ", courseId=" + courseId +
                ", examName='" + examName + '\'' +
                ", examType='" + examType + '\'' +
                ", examDate='" + examDate + '\'' +
                ", totalMarks=" + totalMarks +
                ", academicYear='" + academicYear + '\'' +
                ", active=" + active +
                ", createdAt='" + createdAt + '\'' +
                ", courseCode='" + courseCode + '\'' +
                ", courseName='" + courseName + '\'' +
                '}';
    }
}

