package com.college.dao;

import com.college.TestDBHelper;
import com.college.exception.DataAccessException;
import com.college.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [T9] Persistence & constraint tests for {@link AttendanceDAO}.
 */
public class AttendanceDAOTest {

    private static AttendanceDAO attendanceDAO;
    private static EnrollmentDAO enrollmentDAO;
    private static StudentDAO studentDAO;
    private static CourseDAO courseDAO;
    private static DepartmentDAO departmentDAO;
    private static UserDAO userDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        attendanceDAO = new AttendanceDAO();
        enrollmentDAO = new EnrollmentDAO();
        studentDAO = new StudentDAO();
        courseDAO = new CourseDAO();
        departmentDAO = new DepartmentDAO();
        userDAO = new UserDAO();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    private Enrollment createTestEnrollment(Connection conn, String stuId, String cCode) {
        User u = userDAO.insert(conn, new User("u_" + stuId, "$2a$12$hash", Role.STUDENT));
        Department d = departmentDAO.insert(conn, new Department("D_" + stuId, "Dept " + stuId));
        Student s = studentDAO.insert(conn, new Student(u.getId(), d.getId(), stuId, "First", "Last", stuId + "@col.edu", "555-01", "2004-01-01", "2026-08-01", "1"));
        Course c = courseDAO.insert(conn, new Course(d.getId(), cCode, "Course " + cCode, 3, "1", 30));
        Enrollment enr = new Enrollment(s.getId(), c.getId(), "2026-2027");
        enr.setStatus("ACTIVE");
        enr.setEnrolledDate("2026-08-15");
        return enrollmentDAO.insert(conn, enr);
    }

    @Test
    @DisplayName("Verify attendance creation and retrieval by ID and date")
    void testInsertAndFind() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Enrollment enr = createTestEnrollment(conn, "STU001", "CS101");

            Attendance att = new Attendance(enr.getId(), "2026-09-01", "PRESENT", "On time");
            Attendance inserted = attendanceDAO.insert(conn, att);
            assertTrue(inserted.getId() > 0);

            Attendance found = attendanceDAO.findById(inserted.getId());
            assertNotNull(found);
            assertEquals(enr.getId(), found.getEnrollmentId());
            assertEquals("2026-09-01", found.getAttendanceDate());
            assertEquals("PRESENT", found.getStatus());
            assertEquals("On time", found.getRemarks());
        }
    }

    @Test
    @DisplayName("Verify duplicate attendance on same date for enrollment is rejected")
    void testDuplicateAttendanceDateRejected() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Enrollment enr = createTestEnrollment(conn, "STU002", "CS102");

            attendanceDAO.insert(conn, new Attendance(enr.getId(), "2026-09-01", "PRESENT", "First record"));

            // Same date and enrollment must fail UNIQUE(enrollment_id, attendance_date)
            Attendance dup = new Attendance(enr.getId(), "2026-09-01", "ABSENT", "Duplicate attempt");
            assertThrows(DataAccessException.class, () -> attendanceDAO.insert(conn, dup));
        }
    }

    @Test
    @DisplayName("Verify status CHECK constraint rejects invalid attendance statuses")
    void testInvalidStatusRejected() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Enrollment enr = createTestEnrollment(conn, "STU003", "CS103");

            Attendance invalid = new Attendance(enr.getId(), "2026-09-01", "EXCUSED", "Invalid status");
            assertThrows(DataAccessException.class, () -> attendanceDAO.insert(conn, invalid));
        }
    }

    @Test
    @DisplayName("Verify attendance recording and findByEnrollment across multiple enrollments")
    void testMultipleEnrollmentAttendance() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            Enrollment e1 = createTestEnrollment(conn, "STU_B1", "CS_B1");
            Enrollment e2 = createTestEnrollment(conn, "STU_B2", "CS_B2");

            attendanceDAO.insert(conn, new Attendance(e1.getId(), "2026-09-10", "PRESENT", ""));
            attendanceDAO.insert(conn, new Attendance(e2.getId(), "2026-09-10", "LATE", "10 min"));

            List<Attendance> list1 = attendanceDAO.findByEnrollment(conn, e1.getId());
            assertEquals(1, list1.size());
            assertEquals("PRESENT", list1.get(0).getStatus());

            List<Attendance> list2 = attendanceDAO.findByEnrollment(conn, e2.getId());
            assertEquals(1, list2.size());
            assertEquals("LATE", list2.get(0).getStatus());
        }
    }
}

