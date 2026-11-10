package com.college.model;

import java.math.BigDecimal;

/**
 * Domain entity representing marks scored by an enrolled student on an examination.
 * Maps to the {@code marks} table in the database schema.
 */
public class Mark {

    private int id;
    private int enrollmentId;
    private int examinationId;
    private double marksObtained;
    private String grade;
    private String remarks;
    private String createdAt;
    private String updatedAt;

    // Transient display properties populated from joins
    private String studentName;
    private String studentIdNumber;
    private String examName;
    private Integer totalMarks;

    /**
     * Default no-argument constructor.
     */
    public Mark() {
    }

    /**
     * Convenience constructor for entering student marks.
     *
     * @param enrollmentId  student enrollment ID
     * @param examinationId examination ID
     * @param marksObtained scored marks
     */
    public Mark(int enrollmentId, int examinationId, double marksObtained) {
        this.enrollmentId = enrollmentId;
        this.examinationId = examinationId;
        this.marksObtained = marksObtained;
    }

    /**
     * Convenience constructor with grade and remarks.
     *
     * @param enrollmentId  student enrollment ID
     * @param examinationId examination ID
     * @param marksObtained scored marks
     * @param grade         computed letter grade
     * @param remarks       evaluator feedback
     */
    public Mark(int enrollmentId, int examinationId, double marksObtained, String grade, String remarks) {
        this.enrollmentId = enrollmentId;
        this.examinationId = examinationId;
        this.marksObtained = marksObtained;
        this.grade = grade;
        this.remarks = remarks;
    }

    /**
     * Full constructor mapping all database columns.
     */
    public Mark(int id, int enrollmentId, int examinationId, double marksObtained,
                String grade, String remarks, String createdAt, String updatedAt) {
        this.id = id;
        this.enrollmentId = enrollmentId;
        this.examinationId = examinationId;
        this.marksObtained = marksObtained;
        this.grade = grade;
        this.remarks = remarks;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(int enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public int getExaminationId() {
        return examinationId;
    }

    public void setExaminationId(int examinationId) {
        this.examinationId = examinationId;
    }

    public double getMarksObtained() {
        return marksObtained;
    }

    public void setMarksObtained(double marksObtained) {
        this.marksObtained = marksObtained;
    }

    /**
     * Convenience getter returning marks as exact BigDecimal.
     *
     * @return marks as BigDecimal representation
     */
    public BigDecimal getMarksObtainedAsBigDecimal() {
        return BigDecimal.valueOf(marksObtained);
    }

    /**
     * Convenience setter accepting marks as BigDecimal.
     *
     * @param marksBd marks as BigDecimal
     */
    public void setMarksObtained(BigDecimal marksBd) {
        this.marksObtained = (marksBd != null) ? marksBd.doubleValue() : 0.0;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getStudentIdNumber() {
        return studentIdNumber;
    }

    public void setStudentIdNumber(String studentIdNumber) {
        this.studentIdNumber = studentIdNumber;
    }

    public String getExamName() {
        return examName;
    }

    public void setExamName(String examName) {
        this.examName = examName;
    }

    public Integer getTotalMarks() {
        return totalMarks;
    }

    public void setTotalMarks(Integer totalMarks) {
        this.totalMarks = totalMarks;
    }

    @Override
    public String toString() {
        return "Mark{" +
                "id=" + id +
                ", enrollmentId=" + enrollmentId +
                ", examinationId=" + examinationId +
                ", marksObtained=" + marksObtained +
                ", grade='" + grade + '\'' +
                ", remarks='" + remarks + '\'' +
                ", createdAt='" + createdAt + '\'' +
                ", updatedAt='" + updatedAt + '\'' +
                ", studentName='" + studentName + '\'' +
                ", studentIdNumber='" + studentIdNumber + '\'' +
                ", examName='" + examName + '\'' +
                ", totalMarks=" + totalMarks +
                '}';
    }
}

