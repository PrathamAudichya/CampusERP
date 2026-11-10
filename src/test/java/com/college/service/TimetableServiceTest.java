package com.college.service;

import com.college.TestDBHelper;
import com.college.dao.CourseAssignmentDAO;
import com.college.dao.CourseDAO;
import com.college.dao.DepartmentDAO;
import com.college.dao.FacultyDAO;
import com.college.dao.StudentDAO;
import com.college.dao.TimetableDAO;
import com.college.dao.UserDAO;
import com.college.exception.ValidationException;
import com.college.model.Course;
import com.college.model.CourseAssignment;
import com.college.model.Department;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.TimetableEntry;
import com.college.model.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TimetableService: CRUD operations, schedule conflict detection (faculty, classroom, cohort),
 * time validation, and role-based timetable queries.
 */
public class TimetableServiceTest {

    private static TimetableService timetableService;
    private static TimetableDAO timetableDAO;
    private static CourseDAO courseDAO;
    private static FacultyDAO facultyDAO;
    private static DepartmentDAO departmentDAO;
    private static CourseAssignmentDAO courseAssignmentDAO;
    private static StudentDAO studentDAO;
    private static UserDAO userDAO;

    @BeforeAll
    static void setUpAll() throws Exception {
        TestDBHelper.initTestDatabase();
        timetableDAO = new TimetableDAO();
        courseDAO = new CourseDAO();
        facultyDAO = new FacultyDAO();
        departmentDAO = new DepartmentDAO();
        courseAssignmentDAO = new CourseAssignmentDAO();
        studentDAO = new StudentDAO();
        userDAO = new UserDAO();
        timetableService = new TimetableService(timetableDAO, courseDAO, facultyDAO, departmentDAO, courseAssignmentDAO, studentDAO, new AuditService());
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    @Test
    @DisplayName("Verify successful creation and retrieval of timetable entry")
    void testCreateTimetableSuccess() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = userDAO.insert(conn, new User("admin_tt", "$2a$12$hash", Role.ADMIN));
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS101", "Data Structures", 4, "1", 60));

            User uFac = userDAO.insert(conn, new User("fac_tt", "$2a$12$hash", Role.FACULTY));
            Faculty faculty = facultyDAO.insert(conn, new Faculty(uFac.getId(), dept.getId(), "FAC2026111", "Donald", "Knuth", "dknuth@college.edu", "555-1111", "Algorithms", "2026-01-01"));

            // Assign faculty to course
            courseAssignmentDAO.insert(conn, new CourseAssignment(course.getId(), faculty.getId(), "2026-2027"));

            TimetableEntry entry = new TimetableEntry(
                    course.getId(), faculty.getId(), dept.getId(), "1",
                    "MONDAY", "09:00", "10:30", "LH-101", "2026-2027"
            );

            TimetableEntry created = timetableService.createTimetableEntry(admin, entry);
            assertNotNull(created);
            assertTrue(created.getId() > 0);
            assertEquals("09:00", created.getStartTime());
            assertEquals("10:30", created.getEndTime());
            assertEquals("LH-101", created.getClassroom());

            // Query via service
            List<TimetableEntry> list = timetableService.getTimetablesByDepartmentAndSemester(admin, dept.getId(), "1", "2026-2027");
            assertEquals(1, list.size());
            assertEquals("CS101", list.get(0).getCourseCode());
        }
    }

    @Test
    @DisplayName("Verify rejection when end time is earlier than or equal to start time")
    void testTimeOrderingValidation() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = userDAO.insert(conn, new User("admin_val", "$2a$12$hash", Role.ADMIN));
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course course = courseDAO.insert(conn, new Course(dept.getId(), "CS101", "Data Structures", 4, "1", 60));
            User uFac = userDAO.insert(conn, new User("fac_val", "$2a$12$hash", Role.FACULTY));
            Faculty faculty = facultyDAO.insert(conn, new Faculty(uFac.getId(), dept.getId(), "FAC2026112", "Edsger", "Dijkstra", "dijkstra@college.edu", "555-2222", "Graph Algorithms", "2026-01-01"));
            courseAssignmentDAO.insert(conn, new CourseAssignment(course.getId(), faculty.getId(), "2026-2027"));

            // Equal time (10:00 -> 10:00)
            TimetableEntry invalid1 = new TimetableEntry(course.getId(), faculty.getId(), dept.getId(), "1", "MONDAY", "10:00", "10:00", "LH-101", "2026-2027");
            assertThrows(ValidationException.class, () -> timetableService.createTimetableEntry(admin, invalid1));

            // Reversed time (11:00 -> 09:00)
            TimetableEntry invalid2 = new TimetableEntry(course.getId(), faculty.getId(), dept.getId(), "1", "MONDAY", "11:00", "09:00", "LH-101", "2026-2027");
            assertThrows(ValidationException.class, () -> timetableService.createTimetableEntry(admin, invalid2));
        }
    }

    @Test
    @DisplayName("Verify faculty conflict detection when same instructor is scheduled in overlapping slots")
    void testFacultyConflictDetection() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = userDAO.insert(conn, new User("admin_fc", "$2a$12$hash", Role.ADMIN));
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course c1 = courseDAO.insert(conn, new Course(dept.getId(), "CS101", "Data Structures", 4, "1", 60));
            Course c2 = courseDAO.insert(conn, new Course(dept.getId(), "CS201", "Algorithms", 4, "2", 60));

            User uFac = userDAO.insert(conn, new User("fac_fc", "$2a$12$hash", Role.FACULTY));
            Faculty faculty = facultyDAO.insert(conn, new Faculty(uFac.getId(), dept.getId(), "FAC2026113", "Claude", "Shannon", "shannon@college.edu", "555-3333", "Information Theory", "2026-01-01"));

            courseAssignmentDAO.insert(conn, new CourseAssignment(c1.getId(), faculty.getId(), "2026-2027"));
            courseAssignmentDAO.insert(conn, new CourseAssignment(c2.getId(), faculty.getId(), "2026-2027"));

            // Schedule slot 1: 09:00 - 10:30 in LH-101
            timetableService.createTimetableEntry(admin, new TimetableEntry(c1.getId(), faculty.getId(), dept.getId(), "1", "MONDAY", "09:00", "10:30", "LH-101", "2026-2027"));

            // Attempt overlapping slot with same faculty: 10:00 - 11:30 in LH-202
            TimetableEntry conflict = new TimetableEntry(c2.getId(), faculty.getId(), dept.getId(), "2", "MONDAY", "10:00", "11:30", "LH-202", "2026-2027");
            ValidationException ex = assertThrows(ValidationException.class, () -> timetableService.createTimetableEntry(admin, conflict));
            assertTrue(ex.getMessage().contains("Faculty Conflict"));
        }
    }

    @Test
    @DisplayName("Verify classroom occupancy conflict detection when two classes booked in same room at same time")
    void testClassroomConflictDetection() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = userDAO.insert(conn, new User("admin_cc", "$2a$12$hash", Role.ADMIN));
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course c1 = courseDAO.insert(conn, new Course(dept.getId(), "CS101", "Data Structures", 4, "1", 60));
            Course c2 = courseDAO.insert(conn, new Course(dept.getId(), "CS201", "Algorithms", 4, "2", 60));

            User uFac1 = userDAO.insert(conn, new User("fac_cc1", "$2a$12$hash", Role.FACULTY));
            Faculty f1 = facultyDAO.insert(conn, new Faculty(uFac1.getId(), dept.getId(), "FAC2026114", "John", "Backus", "backus@college.edu", "555-4444", "Compilers", "2026-01-01"));

            User uFac2 = userDAO.insert(conn, new User("fac_cc2", "$2a$12$hash", Role.FACULTY));
            Faculty f2 = facultyDAO.insert(conn, new Faculty(uFac2.getId(), dept.getId(), "FAC2026115", "Tim", "Berners-Lee", "tbl@college.edu", "555-5555", "Web Tech", "2026-01-01"));

            courseAssignmentDAO.insert(conn, new CourseAssignment(c1.getId(), f1.getId(), "2026-2027"));
            courseAssignmentDAO.insert(conn, new CourseAssignment(c2.getId(), f2.getId(), "2026-2027"));

            // Schedule Room LH-101 for Course 1: 09:00 - 10:30
            timetableService.createTimetableEntry(admin, new TimetableEntry(c1.getId(), f1.getId(), dept.getId(), "1", "TUESDAY", "09:00", "10:30", "LH-101", "2026-2027"));

            // Attempt to book same room LH-101 for Course 2: 09:30 - 11:00
            TimetableEntry roomConflict = new TimetableEntry(c2.getId(), f2.getId(), dept.getId(), "2", "TUESDAY", "09:30", "11:00", "LH-101", "2026-2027");
            ValidationException ex = assertThrows(ValidationException.class, () -> timetableService.createTimetableEntry(admin, roomConflict));
            assertTrue(ex.getMessage().contains("Classroom Conflict"));
        }
    }

    @Test
    @DisplayName("Verify cohort overlap conflict detection when same department and semester has two overlapping classes")
    void testCohortConflictDetection() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = userDAO.insert(conn, new User("admin_coh", "$2a$12$hash", Role.ADMIN));
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course c1 = courseDAO.insert(conn, new Course(dept.getId(), "CS101", "Data Structures", 4, "1", 60));
            Course c2 = courseDAO.insert(conn, new Course(dept.getId(), "CS102", "Digital Logic", 4, "1", 60));

            User uFac1 = userDAO.insert(conn, new User("fac_coh1", "$2a$12$hash", Role.FACULTY));
            Faculty f1 = facultyDAO.insert(conn, new Faculty(uFac1.getId(), dept.getId(), "FAC2026116", "Barbara", "Liskov", "liskov@college.edu", "555-6666", "Distributed Systems", "2026-01-01"));

            User uFac2 = userDAO.insert(conn, new User("fac_coh2", "$2a$12$hash", Role.FACULTY));
            Faculty f2 = facultyDAO.insert(conn, new Faculty(uFac2.getId(), dept.getId(), "FAC2026117", "Leslie", "Lamport", "lamport@college.edu", "555-7777", "Formal Systems", "2026-01-01"));

            courseAssignmentDAO.insert(conn, new CourseAssignment(c1.getId(), f1.getId(), "2026-2027"));
            courseAssignmentDAO.insert(conn, new CourseAssignment(c2.getId(), f2.getId(), "2026-2027"));

            // Schedule CS Sem 1 Class 1: 14:00 - 15:30 in LH-101
            timetableService.createTimetableEntry(admin, new TimetableEntry(c1.getId(), f1.getId(), dept.getId(), "1", "WEDNESDAY", "14:00", "15:30", "LH-101", "2026-2027"));

            // Attempt to schedule CS Sem 1 Class 2: 15:00 - 16:30 in a different room LH-202
            TimetableEntry cohortConflict = new TimetableEntry(c2.getId(), f2.getId(), dept.getId(), "1", "WEDNESDAY", "15:00", "16:30", "LH-202", "2026-2027");
            ValidationException ex = assertThrows(ValidationException.class, () -> timetableService.createTimetableEntry(admin, cohortConflict));
            assertTrue(ex.getMessage().contains("Cohort Conflict"));
        }
    }

    @Test
    @DisplayName("Verify rejection when faculty is not assigned to course for academic year")
    void testUnassignedFacultyRejection() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = userDAO.insert(conn, new User("admin_unass", "$2a$12$hash", Role.ADMIN));
            Department dept = departmentDAO.insert(conn, new Department("CS", "Computer Science"));
            Course c1 = courseDAO.insert(conn, new Course(dept.getId(), "CS101", "Data Structures", 4, "1", 60));

            User uFac = userDAO.insert(conn, new User("fac_unass", "$2a$12$hash", Role.FACULTY));
            Faculty faculty = facultyDAO.insert(conn, new Faculty(uFac.getId(), dept.getId(), "FAC2026118", "George", "Boole", "boole@college.edu", "555-8888", "Boolean Algebra", "2026-01-01"));

            // DO NOT assign faculty to course
            TimetableEntry entry = new TimetableEntry(c1.getId(), faculty.getId(), dept.getId(), "1", "THURSDAY", "09:00", "10:30", "LH-101", "2026-2027");
            ValidationException ex = assertThrows(ValidationException.class, () -> timetableService.createTimetableEntry(admin, entry));
            assertTrue(ex.getMessage().contains("is not assigned to course"));
        }
    }

    @Test
    @DisplayName("Verify student timetable retrieval based on enrolled department and semester")
    void testStudentTimetableRetrieval() throws SQLException {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            User admin = userDAO.insert(conn, new User("admin_stu_tt", "$2a$12$hash", Role.ADMIN));
            Department dept = departmentDAO.insert(conn, new Department("EC", "Electronics"));
            Course c = courseDAO.insert(conn, new Course(dept.getId(), "EC101", "Circuit Theory", 3, "1", 60));

            User uFac = userDAO.insert(conn, new User("fac_ec", "$2a$12$hash", Role.FACULTY));
            Faculty faculty = facultyDAO.insert(conn, new Faculty(uFac.getId(), dept.getId(), "FAC2026119", "Nikola", "Tesla", "tesla@college.edu", "555-9999", "Electromagnetics", "2026-01-01"));
            courseAssignmentDAO.insert(conn, new CourseAssignment(c.getId(), faculty.getId(), "2026-2027"));

            // Schedule class
            timetableService.createTimetableEntry(admin, new TimetableEntry(c.getId(), faculty.getId(), dept.getId(), "1", "FRIDAY", "11:00", "12:30", "LH-301", "2026-2027"));

            // Student in EC Department, Semester 1
            User uStudent = userDAO.insert(conn, new User("stu_ec_1", "$2a$12$hash", Role.STUDENT));
            Student student = new Student();
            student.setUserId(uStudent.getId());
            student.setDepartmentId(dept.getId());
            student.setStudentId("STU2026333");
            student.setFirstName("Max");
            student.setLastName("Planck");
            student.setEmail("planck@college.edu");
            student.setPhone("555-0000");
            student.setSemester("1");
            student.setDateOfBirth("2005-04-23");
            student.setAdmissionDate("2026-08-01");
            studentDAO.insert(conn, student);

            List<TimetableEntry> studentSchedule = timetableService.getTimetablesForStudent(uStudent, "2026-2027");
            assertEquals(1, studentSchedule.size());
            assertEquals("EC101", studentSchedule.get(0).getCourseCode());
            assertEquals("FRIDAY", studentSchedule.get(0).getDayOfWeek());
        }
    }
}
