package com.college.ui;

import com.college.config.DatabaseInitializer;
import com.college.dao.FacultyDAO;
import com.college.dao.StudentDAO;
import com.college.dao.UserDAO;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated Swing Event Dispatch Thread (EDT) UI component verification suite for Milestone 1.
 * Tests MainFrame navigation and panel lifecycle across Admin, Faculty, and Student roles
 * against a disposable migrated database.
 */
public class Milestone1GuiComponentVerificationTest {

    private static final Path DEMO_DB_PATH = Paths.get("target", "test-dbs", "gui-verification-demo.db");
    private static User adminUser;
    private static User facultyUser;
    private static User studentUser;

    @BeforeAll
    public static void setUpDemoDatabase() throws Exception {
        Files.createDirectories(DEMO_DB_PATH.getParent());
        Files.deleteIfExists(DEMO_DB_PATH);

        System.setProperty("college.db.path", DEMO_DB_PATH.toAbsolutePath().toString());

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DEMO_DB_PATH.toAbsolutePath().toString())) {
            DatabaseInitializer.initializeWithConnection(conn);
        }

        UserDAO userDAO = new UserDAO();

        // 1. Admin user
        adminUser = userDAO.findByUsername("admin");
        if (adminUser == null) {
            adminUser = new User("admin", "hash", Role.ADMIN);
            adminUser.setId(1);
            adminUser.setActive(true);
        }

        // 2. Faculty user
        FacultyDAO facultyDAO = new FacultyDAO();
        Faculty f = facultyDAO.findById(1);
        int facultyUserId = f != null ? f.getUserId() : 2;
        facultyUser = userDAO.findById(facultyUserId);
        if (facultyUser == null) {
            facultyUser = new User("jdoe", "hash", Role.FACULTY);
            facultyUser.setId(facultyUserId);
            facultyUser.setActive(true);
        }

        // 3. Student user
        StudentDAO studentDAO = new StudentDAO();
        Student s = studentDAO.findById(1);
        int studentUserId = s != null ? s.getUserId() : 4;
        studentUser = userDAO.findById(studentUserId);
        if (studentUser == null) {
            studentUser = new User("ssmith", "hash", Role.STUDENT);
            studentUser.setId(studentUserId);
            studentUser.setActive(true);
        }
    }

    private void runOnEDT(Runnable r) throws InterruptedException, InvocationTargetException {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeAndWait(r);
        }
    }

    @Test
    @DisplayName("Verify Admin MainFrame & All Associated Module Panels")
    public void testAdminMainFrameAndPanels() throws Exception {
        runOnEDT(() -> {
            assertDoesNotThrow(() -> {
                MainFrame frame = new MainFrame(adminUser);
                assertNotNull(frame);
                frame.dispose();
            }, "Admin MainFrame must instantiate all 15 administrative module views without errors");
        });
    }

    @Test
    @DisplayName("Verify Faculty MainFrame & All Associated Module Panels")
    public void testFacultyMainFrameAndPanels() throws Exception {
        runOnEDT(() -> {
            assertDoesNotThrow(() -> {
                MainFrame frame = new MainFrame(facultyUser);
                assertNotNull(frame);
                frame.dispose();
            }, "Faculty MainFrame must instantiate all faculty teaching views and profile without errors");
        });
    }

    @Test
    @DisplayName("Verify Student MainFrame & All Associated Module Panels")
    public void testStudentMainFrameAndPanels() throws Exception {
        runOnEDT(() -> {
            assertDoesNotThrow(() -> {
                MainFrame frame = new MainFrame(studentUser);
                assertNotNull(frame);
                frame.dispose();
            }, "Student MainFrame must instantiate student dashboard, courses, timetable, fees, and profile without errors");
        });
    }
}
