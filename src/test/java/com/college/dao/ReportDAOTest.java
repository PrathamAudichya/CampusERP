package com.college.dao;

import com.college.TestDBHelper;
import com.college.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [T13] Aggregate reporting & query isolation tests for {@link ReportDAO}.
 */
public class ReportDAOTest {

    private static ReportDAO reportDAO;
    private static StudentDAO studentDAO;
    private static DepartmentDAO departmentDAO;
    private static UserDAO userDAO;
    private static CourseDAO courseDAO;
    private static EnrollmentDAO enrollmentDAO;
    private static ExaminationDAO examinationDAO;
    private static MarkDAO markDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        reportDAO = new ReportDAO();
        studentDAO = new StudentDAO();
        departmentDAO = new DepartmentDAO();
        userDAO = new UserDAO();
        courseDAO = new CourseDAO();
        enrollmentDAO = new EnrollmentDAO();
        examinationDAO = new ExaminationDAO();
        markDAO = new MarkDAO();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    @Test
    @DisplayName("Verify student performance report query with student-level isolation")
    void testStudentPerformanceReportIsolation() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course c1 = courseDAO.insert(conn, new Course(dept.getId(), "CS101", "Intro", 4, "1", 50));
            Course c2 = courseDAO.insert(conn, new Course(dept.getId(), "CS102", "Data Structures", 4, "1", 50));

            User u1 = userDAO.insert(conn, new User("stu1", "$2a$12$hash", Role.STUDENT));
            Student s1 = studentDAO.insert(conn, new Student(u1.getId(), dept.getId(), "S101", "Alice", "Smith", "alice@col.edu", "555-01", "2004-01-01", "2026-08-01", "1"));

            User u2 = userDAO.insert(conn, new User("stu2", "$2a$12$hash", Role.STUDENT));
            Student s2 = studentDAO.insert(conn, new Student(u2.getId(), dept.getId(), "S102", "Bob", "Jones", "bob@col.edu", "555-02", "2004-01-01", "2026-08-01", "1"));

            Enrollment enr1 = new Enrollment(s1.getId(), c1.getId(), "2026-2027");
            enr1.setStatus("ACTIVE");
            Enrollment e1 = enrollmentDAO.insert(conn, enr1);

            Enrollment enr2 = new Enrollment(s2.getId(), c2.getId(), "2026-2027");
            enr2.setStatus("ACTIVE");
            Enrollment e2 = enrollmentDAO.insert(conn, enr2);

            Examination exam1 = examinationDAO.insert(conn, new Examination(c1.getId(), "Midterm", "MIDTERM", "2026-10-15", 100, "2026-2027"));
            Examination exam2 = examinationDAO.insert(conn, new Examination(c2.getId(), "Midterm", "MIDTERM", "2026-10-15", 100, "2026-2027"));

            markDAO.insert(conn, new Mark(e1.getId(), exam1.getId(), 88.5, "A", "Good"));
            markDAO.insert(conn, new Mark(e2.getId(), exam2.getId(), 92.0, "A+", "Excellent"));

            // 1. Full institutional report (Admin scope)
            List<Map<String, Object>> adminReport = reportDAO.getStudentPerformanceReport(conn, "2026-2027", null, null, null);
            assertEquals(2, adminReport.size());

            // 2. Isolated student report (Student scope)
            List<Map<String, Object>> s1Report = reportDAO.getStudentPerformanceReport(conn, "2026-2027", null, null, s1.getId());
            assertEquals(1, s1Report.size());
            assertEquals("S101", s1Report.get(0).get("student_id_number"));

            // 3. Isolated faculty report (Faculty scope: restricted to assigned courses)
            List<Map<String, Object>> facultyReport = reportDAO.getStudentPerformanceReport(conn, "2026-2027", null, null, null, List.of(c1.getId()));
            assertEquals(1, facultyReport.size());
            assertEquals("CS101", facultyReport.get(0).get("course_code"));
        }
    }
}
