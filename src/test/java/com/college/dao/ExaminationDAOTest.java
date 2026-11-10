package com.college.dao;

import com.college.TestDBHelper;
import com.college.exception.DataAccessException;
import com.college.model.Course;
import com.college.model.Department;
import com.college.model.Examination;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [T10] Persistence & constraint tests for {@link ExaminationDAO}.
 */
public class ExaminationDAOTest {

    private static ExaminationDAO examinationDAO;
    private static CourseDAO courseDAO;
    private static DepartmentDAO departmentDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        examinationDAO = new ExaminationDAO();
        courseDAO = new CourseDAO();
        departmentDAO = new DepartmentDAO();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    private Course createTestCourse(Connection conn, String code) {
        Department d = departmentDAO.insert(conn, new Department("D_" + code, "Dept " + code));
        return courseDAO.insert(conn, new Course(d.getId(), code, "Course " + code, 4, "1", 50));
    }

    @Test
    @DisplayName("Verify examination creation and retrieval by ID")
    void testInsertAndFind() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Course course = createTestCourse(conn, "CS101");

            Examination exam = new Examination(course.getId(), "Midterm Exam", "MIDTERM", "2026-10-15", 100, "2026-2027");
            Examination inserted = examinationDAO.insert(conn, exam);
            assertTrue(inserted.getId() > 0);

            Examination found = examinationDAO.findById(inserted.getId());
            assertNotNull(found);
            assertEquals(course.getId(), found.getCourseId());
            assertEquals("Midterm Exam", found.getExamName());
            assertEquals("MIDTERM", found.getExamType());
            assertEquals("2026-10-15", found.getExamDate());
            assertEquals(100, found.getTotalMarks());
            assertEquals("2026-2027", found.getAcademicYear());
            assertTrue(found.isActive());
        }
    }

    @Test
    @DisplayName("Verify exam_type CHECK constraint enforces valid assessment types")
    void testInvalidExamTypeRejected() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Course course = createTestCourse(conn, "CS102");

            // Invalid type 'POP_QUIZ' must fail CHECK(exam_type IN ('MIDTERM', 'FINAL', 'QUIZ', 'ASSIGNMENT'))
            Examination invalid = new Examination(course.getId(), "Pop Quiz", "POP_QUIZ", "2026-10-15", 50, "2026-2027");
            assertThrows(DataAccessException.class, () -> examinationDAO.insert(conn, invalid));
        }
    }

    @Test
    @DisplayName("Verify total_marks CHECK constraint enforces strictly positive marks (> 0)")
    void testNegativeOrZeroMarksRejected() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Course course = createTestCourse(conn, "CS103");

            Examination zeroMarks = new Examination(course.getId(), "Zero Exam", "QUIZ", "2026-10-15", 0, "2026-2027");
            assertThrows(DataAccessException.class, () -> examinationDAO.insert(conn, zeroMarks));

            Examination negMarks = new Examination(course.getId(), "Neg Exam", "QUIZ", "2026-10-15", -10, "2026-2027");
            assertThrows(DataAccessException.class, () -> examinationDAO.insert(conn, negMarks));
        }
    }

    @Test
    @DisplayName("Verify updating examination date, total marks, and deactivation")
    void testUpdateAndDeactivate() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Course course = createTestCourse(conn, "CS104");
            Examination ex = examinationDAO.insert(conn, new Examination(course.getId(), "Final", "FINAL", "2026-12-01", 100, "2026-2027"));

            ex.setExamDate("2026-12-05");
            ex.setTotalMarks(150);
            boolean updated = examinationDAO.update(conn, ex);
            assertTrue(updated);

            Examination reloaded = examinationDAO.findById(ex.getId());
            assertEquals("2026-12-05", reloaded.getExamDate());
            assertEquals(150, reloaded.getTotalMarks());

            // Deactivate
            examinationDAO.setActive(conn, ex.getId(), false);
            assertFalse(examinationDAO.findById(ex.getId()).isActive());
        }
    }
}

