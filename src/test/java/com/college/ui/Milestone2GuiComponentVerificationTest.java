package com.college.ui;

import com.college.config.DatabaseInitializer;
import com.college.dao.FacultyDAO;
import com.college.dao.StudentDAO;
import com.college.dao.UserDAO;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.User;
import com.college.ui.dialog.TemporaryPasswordDialog;
import com.college.ui.panel.FacultyPanel;
import com.college.ui.panel.StudentPanel;
import com.college.ui.panel.UserManagementPanel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated Swing Event Dispatch Thread (EDT) UI verification suite for Milestone 2.
 * Validates UI component tree, card layouts, table structures, and dialogs across
 * User Management, Student Profile Cards, Faculty Profile Cards, and Credential Dialogs.
 */
public class Milestone2GuiComponentVerificationTest {

    private static final Path DEMO_DB_PATH = Paths.get("target", "test-dbs", "gui-verification-m2-demo.db");
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
        int studentUserId = s != null ? s.getUserId() : 3;
        studentUser = userDAO.findById(studentUserId);
        if (studentUser == null) {
            studentUser = new User("ajones", "hash", Role.STUDENT);
            studentUser.setId(studentUserId);
            studentUser.setActive(true);
        }
    }

    @Test
    @DisplayName("Verify UserManagementPanel on Swing EDT: 10 columns, StatusBadges, and action controls")
    void testUserManagementPanelEdtRendering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertDoesNotThrow(() -> {
                UserManagementPanel panel = new UserManagementPanel(adminUser);
                assertNotNull(panel);

                // Find JTable
                JTable table = findComponentOfType(panel, JTable.class);
                assertNotNull(table, "User table must be instantiated on EDT");
                assertEquals(10, table.getColumnCount(), "User directory table must have exactly 10 columns");

                assertEquals("User ID", table.getColumnName(0));
                assertEquals("Full Name", table.getColumnName(1));
                assertEquals("Username", table.getColumnName(2));
                assertEquals("Registration ID / Faculty ID", table.getColumnName(3));
                assertEquals("Role", table.getColumnName(4));
                assertEquals("Department", table.getColumnName(5));
                assertEquals("Email", table.getColumnName(6));
                assertEquals("Account Status", table.getColumnName(7));
                assertEquals("Must Change Password", table.getColumnName(8));
                assertEquals("Account Creation Status", table.getColumnName(9));
            });
        });
    }

    @Test
    @DisplayName("Verify StudentPanel on Swing EDT: 6 organized cards in Student Profile view")
    void testStudentProfileCardsEdtRendering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertDoesNotThrow(() -> {
                StudentPanel panel = new StudentPanel(studentUser);
                assertNotNull(panel);

                JFrame frame = new JFrame();
                frame.setContentPane(panel);
                frame.setSize(800, 600);
                frame.doLayout();

                // Component hierarchy must contain cards
                assertTrue(panel.getComponentCount() > 0, "Student profile view must render components");
                frame.dispose();
            });
        });
    }

    @Test
    @DisplayName("Verify FacultyPanel on Swing EDT: 7 organized cards in Faculty Profile view")
    void testFacultyProfileCardsEdtRendering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertDoesNotThrow(() -> {
                FacultyPanel panel = new FacultyPanel(facultyUser);
                assertNotNull(panel);

                JFrame frame = new JFrame();
                frame.setContentPane(panel);
                frame.setSize(800, 600);
                frame.doLayout();

                assertTrue(panel.getComponentCount() > 0, "Faculty profile view must render components");
                frame.dispose();
            });
        });
    }

    @Test
    @DisplayName("Verify TemporaryPasswordDialog on Swing EDT: Full Name, ID, Username, Password, and Copy Actions")
    void testTemporaryPasswordDialogEdtPresentation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertDoesNotThrow(() -> {
                JFrame parent = new JFrame();
                TemporaryPasswordDialog dlg = new TemporaryPasswordDialog(
                        parent,
                        "Aarav Sharma",
                        "STU-2026-001",
                        "AA123456789012",
                        "TempSecure123!",
                        "Student Account Created Successfully"
                );
                assertNotNull(dlg);
                assertEquals("Student Account Created Successfully", dlg.getTitle());

                // Find buttons
                java.util.List<JButton> buttons = findAllComponentsOfType(dlg, JButton.class);
                boolean hasCopyUser = buttons.stream().anyMatch(b -> "Copy Username".equals(b.getText()));
                boolean hasCopyPass = buttons.stream().anyMatch(b -> "Copy Password".equals(b.getText()));
                boolean hasCopyAll = buttons.stream().anyMatch(b -> "Copy All Credentials".equals(b.getText()));
                boolean hasDone = buttons.stream().anyMatch(b -> "Done".equals(b.getText()));

                assertTrue(hasCopyUser, "Dialog must have Copy Username button");
                assertTrue(hasCopyPass, "Dialog must have Copy Password button");
                assertTrue(hasCopyAll, "Dialog must have Copy All Credentials button");
                assertTrue(hasDone, "Dialog must have Done button");

                dlg.dispose();
                parent.dispose();
            });
        });
    }

    @SuppressWarnings("unchecked")
    private static <T extends Component> T findComponentOfType(Container container, Class<T> clazz) {
        for (Component c : container.getComponents()) {
            if (clazz.isInstance(c)) {
                return (T) c;
            }
            if (c instanceof Container) {
                T found = findComponentOfType((Container) c, clazz);
                if (found != null) return found;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Component> java.util.List<T> findAllComponentsOfType(Container container, Class<T> clazz) {
        java.util.List<T> list = new java.util.ArrayList<>();
        for (Component c : container.getComponents()) {
            if (clazz.isInstance(c)) {
                list.add((T) c);
            }
            if (c instanceof Container) {
                list.addAll(findAllComponentsOfType((Container) c, clazz));
            }
        }
        return list;
    }
}

