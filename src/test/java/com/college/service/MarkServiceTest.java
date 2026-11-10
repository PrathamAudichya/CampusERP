package com.college.service;

import com.college.TestDBHelper;
import com.college.dao.*;
import com.college.exception.AuthenticationException;
import com.college.exception.ValidationException;
import com.college.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [T18] Grade calculation, boundary, decimal precision, & authorization tests for {@link MarkService}.
 */
public class MarkServiceTest {

    private static MarkService markService;
    private static UserDAO userDAO;
    private static DepartmentDAO departmentDAO;
    private static FacultyDAO facultyDAO;
    private static StudentDAO studentDAO;
    private static CourseDAO courseDAO;
    private static CourseAssignmentDAO assignmentDAO;
    private static EnrollmentDAO enrollmentDAO;
    private static ExaminationDAO examinationDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        markService = new MarkService();
        userDAO = new UserDAO();
        departmentDAO = new DepartmentDAO();
        facultyDAO = new FacultyDAO();
        studentDAO = new StudentDAO();
        courseDAO = new CourseDAO();
        assignmentDAO = new CourseAssignmentDAO();
        enrollmentDAO = new EnrollmentDAO();
        examinationDAO = new ExaminationDAO();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    @Test
    @DisplayName("Verify valid marks entry, automatic grade calculation, and decimal scale rule (<= 2 places)")
    void testValidMarksAndGradeCalculation() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = userDAO.insert(conn, new User("admin_mark", "$2a$12$hash", Role.ADMIN));
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS101", "Algorithms", 4, "1", 50));

            User stuUser = userDAO.insert(conn, new User("stu_mark", "$2a$12$hash", Role.STUDENT));
            Student student = studentDAO.insert(conn, new Student(stuUser.getId(), dept.getId(), "S101", "Al", "Smith", "al@col.edu", "555", "2004-01-01", "2026-08-01", "1"));
            Enrollment enr = new Enrollment(student.getId(), course.getId(), "2026-2027");
            enr.setStatus("ACTIVE");
            enr = enrollmentDAO.insert(conn, enr);

            Examination exam = examinationDAO.insert(conn, new Examination(course.getId(), "Final", "FINAL", "2026-12-10", 100, "2026-2027"));

            // 88.50 has 2 decimal places -> valid, grade should be A (or calculated by service)
            Mark m = markService.saveMark(admin, enr.getId(), exam.getId(), 88.50, "Well done");
            assertNotNull(m);
            assertEquals(88.50, m.getMarksObtained());
            assertNotNull(m.getGrade());
            assertEquals("A", m.getGrade());
        }
    }

    @Test
    @DisplayName("Verify decimal scale rule: Marks with > 2 decimal places are rejected (Item 3)")
    void testMarksDecimalScaleRejection() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = userDAO.insert(conn, new User("admin_mark2", "$2a$12$hash", Role.ADMIN));
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS102", "Course", 4, "1", 50));

            User stuUser = userDAO.insert(conn, new User("stu_mark2", "$2a$12$hash", Role.STUDENT));
            Student student = studentDAO.insert(conn, new Student(stuUser.getId(), dept.getId(), "S102", "Al", "Smith", "al2@col.edu", "555", "2004-01-01", "2026-08-01", "1"));
            Enrollment enr = new Enrollment(student.getId(), course.getId(), "2026-2027");
            enr.setStatus("ACTIVE");
            enr = enrollmentDAO.insert(conn, enr);
            Examination exam = examinationDAO.insert(conn, new Examination(course.getId(), "Final", "FINAL", "2026-12-10", 100, "2026-2027"));

            // 88.125 has 3 decimal places -> must throw ValidationException
            final int enrId = enr.getId();
            final int examId = exam.getId();
            assertThrows(ValidationException.class, () -> markService.saveMark(admin, enrId, examId, 88.125, ""));
            // 99.999 has 3 decimal places -> must throw ValidationException
            assertThrows(ValidationException.class, () -> markService.saveMark(admin, enrId, examId, 99.999, ""));
        }
    }

    @Test
    @DisplayName("Verify rejection of NaN, Infinite, and out-of-range marks")
    void testNanAndOutOfRangeMarksRejected() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = userDAO.insert(conn, new User("admin_mark3", "$2a$12$hash", Role.ADMIN));
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS103", "Course", 4, "1", 50));

            User stuUser = userDAO.insert(conn, new User("stu_mark3", "$2a$12$hash", Role.STUDENT));
            Student student = studentDAO.insert(conn, new Student(stuUser.getId(), dept.getId(), "S103", "Al", "Smith", "al3@col.edu", "555", "2004-01-01", "2026-08-01", "1"));
            Enrollment enr = new Enrollment(student.getId(), course.getId(), "2026-2027");
            enr.setStatus("ACTIVE");
            enr = enrollmentDAO.insert(conn, enr);
            Examination exam = examinationDAO.insert(conn, new Examination(course.getId(), "Final", "FINAL", "2026-12-10", 100, "2026-2027"));

            final int enrId = enr.getId();
            final int examId = exam.getId();
            // NaN rejected
            assertThrows(ValidationException.class, () -> markService.saveMark(admin, enrId, examId, Double.NaN, ""));
            // Positive Infinity rejected
            assertThrows(ValidationException.class, () -> markService.saveMark(admin, enrId, examId, Double.POSITIVE_INFINITY, ""));
            // Negative marks rejected
            assertThrows(ValidationException.class, () -> markService.saveMark(admin, enrId, examId, -5.0, ""));
            // Exceeding total marks (105 > 100) rejected
            assertThrows(ValidationException.class, () -> markService.saveMark(admin, enrId, examId, 105.0, ""));
        }
    }

    @Test
    @DisplayName("Special Regression Gap 7: Faculty assignment-scoped marks access enforcement (SEC-03)")
    void testFacultyMarksAccessScopedToAssignedCourses() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));

            Course cAssigned = courseDAO.insert(conn, new Course(dept.getId(), "CS_ASG", "Assigned Course", 4, "1", 50));
            Course cUnassigned = courseDAO.insert(conn, new Course(dept.getId(), "CS_UNASG", "Unassigned Course", 4, "1", 50));

            User facUser = userDAO.insert(conn, new User("fac_marking", "$2a$12$hash", Role.FACULTY));
            Faculty faculty = facultyDAO.insert(conn, new Faculty(facUser.getId(), dept.getId(), "F_MARK", "John", "Prof", "prof@col.edu", "555", "Systems", "2020-01-01"));

            // Assign faculty to cAssigned only
            assignmentDAO.insert(conn, new CourseAssignment(cAssigned.getId(), faculty.getId(), "2026-2027"));

            User stuUser = userDAO.insert(conn, new User("stu_m", "$2a$12$hash", Role.STUDENT));
            Student student = studentDAO.insert(conn, new Student(stuUser.getId(), dept.getId(), "S_M", "Alice", "S", "alice@col.edu", "555", "2004-01-01", "2026-08-01", "1"));

            Enrollment enrA = new Enrollment(student.getId(), cAssigned.getId(), "2026-2027");
            enrA.setStatus("ACTIVE");
            Enrollment enrAssigned = enrollmentDAO.insert(conn, enrA);

            Enrollment enrU = new Enrollment(student.getId(), cUnassigned.getId(), "2026-2027");
            enrU.setStatus("ACTIVE");
            Enrollment enrUnassigned = enrollmentDAO.insert(conn, enrU);

            Examination examAssigned = examinationDAO.insert(conn, new Examination(cAssigned.getId(), "Exam 1", "FINAL", "2026-12-10", 100, "2026-2027"));
            Examination examUnassigned = examinationDAO.insert(conn, new Examination(cUnassigned.getId(), "Exam 2", "FINAL", "2026-12-10", 100, "2026-2027"));

            // 1. Marking assigned course succeeds
            Mark m1 = markService.saveMark(facUser, enrAssigned.getId(), examAssigned.getId(), 85.0, "Good");
            assertNotNull(m1);

            // 2. Marking unassigned course fails (AuthenticationException / ValidationException)
            assertThrows(Exception.class, () -> markService.saveMark(facUser, enrUnassigned.getId(), examUnassigned.getId(), 90.0, "Unauthorized"));
        }
    }
}
