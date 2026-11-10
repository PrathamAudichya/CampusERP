package com.college.ui;

import com.college.config.DatabaseInitializer;
import com.college.dao.CourseAssignmentDAO;
import com.college.dao.CourseDAO;
import com.college.dao.FacultyDAO;
import com.college.dao.StudentDAO;
import com.college.dao.UserDAO;
import com.college.model.AttendanceSession;
import com.college.model.Course;
import com.college.model.CourseAssignment;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.User;
import com.college.service.AttendanceSessionService;
import com.college.ui.dialog.ReviewAttendanceDialog;
import com.college.ui.dialog.StudentCheckInDialog;
import com.college.ui.panel.AttendancePanel;
import com.college.ui.panel.ReportPanel;
import com.college.ui.panel.StudentDashboardPanel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated Swing Event Dispatch Thread (EDT) UI verification suite for Milestone 3.
 * Validates EDT component instantiation, layouts, dialog hierarchies, countdown displays,
 * live review grids, student self-service dialogs, and analytical CSV export controls.
 */
public class Milestone3GuiComponentVerificationTest {

    private static final Path DEMO_DB_PATH = Paths.get("target", "test-dbs", "gui-verification-m3-demo.db");
    private static User adminUser;
    private static User facultyUser;
    private static User studentUser;
    private static AttendanceSession testSession;
    private static AttendanceSessionService sessionService;

    @BeforeAll
    public static void setUpDemoDatabase() throws Exception {
        Files.createDirectories(DEMO_DB_PATH.getParent());
        Files.deleteIfExists(DEMO_DB_PATH);

        System.setProperty("college.db.path", DEMO_DB_PATH.toAbsolutePath().toString());

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DEMO_DB_PATH.toAbsolutePath().toString())) {
            DatabaseInitializer.initializeWithConnection(conn);
            try (java.sql.Statement stmt = conn.createStatement()) {
                stmt.execute("UPDATE users SET must_change_password = 0");
            }
        }

        UserDAO userDAO = new UserDAO();
        FacultyDAO facultyDAO = new FacultyDAO();
        StudentDAO studentDAO = new StudentDAO();
        CourseDAO courseDAO = new CourseDAO();
        CourseAssignmentDAO assignmentDAO = new CourseAssignmentDAO();

        adminUser = userDAO.findByUsername("admin");
        if (adminUser == null) {
            adminUser = new User("admin", "hash", Role.ADMIN);
            adminUser.setId(1);
            adminUser.setActive(true);
        }

        Faculty f = facultyDAO.findById(1);
        int facultyUserId = f != null ? f.getUserId() : 2;
        facultyUser = userDAO.findById(facultyUserId);
        if (facultyUser == null) {
            facultyUser = new User("jdoe", "hash", Role.FACULTY);
            facultyUser.setId(facultyUserId);
            facultyUser.setActive(true);
        }

        Student s = studentDAO.findById(1);
        int studentUserId = s != null ? s.getUserId() : 3;
        studentUser = userDAO.findById(studentUserId);
        if (studentUser == null) {
            studentUser = new User("ajones", "hash", Role.STUDENT);
            studentUser.setId(studentUserId);
            studentUser.setActive(true);
        }

        sessionService = new AttendanceSessionService();

        // Assign Course 1 to Faculty 1 if not already assigned
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DEMO_DB_PATH.toAbsolutePath().toString())) {
            Course c = courseDAO.findById(conn, 1);
            if (c != null && f != null) {
                CourseAssignment ca = assignmentDAO.findActiveByCourseAndFaculty(conn, c.getId(), f.getId(), "2026-2027");
                if (ca == null) {
                    assignmentDAO.insert(conn, new CourseAssignment(c.getId(), f.getId(), "2026-2027"));
                }
            }
        }

        try {
            testSession = sessionService.openSession(facultyUser, 1, "2026-2027", 10, "Lecture 1");
        } catch (Exception e) {
            testSession = new AttendanceSession(1, f != null ? f.getId() : 1, "2026-11-10", "123456", "2026-2027",
                    java.time.Instant.now().plus(java.time.Duration.ofMinutes(10)).toString(), "OPEN", "Lecture 1");
            testSession.setId(1);
        }
    }

    @Test
    @DisplayName("Verify AttendancePanel Faculty View on EDT: Tabs, Roster table, Action controls")
    void testAttendancePanelFacultyViewEdtRendering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertDoesNotThrow(() -> {
                AttendancePanel panel = new AttendancePanel(facultyUser);
                assertNotNull(panel);

                JTabbedPane tabs = findComponentOfType(panel, JTabbedPane.class);
                assertNotNull(tabs, "Faculty attendance view must contain JTabbedPane");
                assertEquals(2, tabs.getTabCount(), "Must have Mark Attendance and Attendance History tabs");
                assertEquals("Mark Attendance", tabs.getTitleAt(0));
                assertEquals("Attendance History", tabs.getTitleAt(1));

                List<JButton> buttons = findAllComponentsOfType(panel, JButton.class);
                assertTrue(buttons.stream().anyMatch(b -> "Load Roster".equals(b.getText())));
                assertTrue(buttons.stream().anyMatch(b -> "Open Timed Check-In".equals(b.getText())));
                assertTrue(buttons.stream().anyMatch(b -> "Mark All Present".equals(b.getText())));
            });
        });
    }

    @Test
    @DisplayName("Verify AttendancePanel Student View on EDT: Check-In card, History table, Overview label")
    void testAttendancePanelStudentViewEdtRendering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertDoesNotThrow(() -> {
                AttendancePanel panel = new AttendancePanel(studentUser);
                assertNotNull(panel);

                // History table
                JTable table = findComponentOfType(panel, JTable.class);
                assertNotNull(table, "Student view must display history table");
                assertEquals(4, table.getColumnCount());
                assertEquals("Course", table.getColumnName(0));
                assertEquals("Date", table.getColumnName(1));
                assertEquals("Status", table.getColumnName(2));
                assertEquals("Remarks", table.getColumnName(3));

                List<JButton> buttons = findAllComponentsOfType(panel, JButton.class);
                assertTrue(buttons.stream().anyMatch(b -> "Submit Check-In".equals(b.getText())));
                assertTrue(buttons.stream().anyMatch(b -> "Select Active Session".equals(b.getText())));
            });
        });
    }

    @Test
    @DisplayName("Verify LiveSessionMonitorDialog on EDT: Large code display, Countdown, Actions")
    void testLiveSessionMonitorDialogEdtRendering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertDoesNotThrow(() -> {
                JFrame frame = new JFrame();
                AttendancePanel.LiveSessionMonitorDialog dlg =
                        new AttendancePanel.LiveSessionMonitorDialog(frame, testSession, sessionService, facultyUser, () -> {});
                assertNotNull(dlg);

                List<JButton> buttons = findAllComponentsOfType(dlg, JButton.class);
                assertTrue(buttons.stream().anyMatch(b -> "Copy Code".equals(b.getText())));
                assertTrue(buttons.stream().anyMatch(b -> "Regenerate Code".equals(b.getText())));
                assertTrue(buttons.stream().anyMatch(b -> "Close Check-In".equals(b.getText())));
                assertTrue(buttons.stream().anyMatch(b -> "Review & Finalize".equals(b.getText())));

                JTable table = findComponentOfType(dlg, JTable.class);
                assertNotNull(table, "Live submissions table must be present");
                assertEquals(3, table.getColumnCount());

                dlg.dispose();
                frame.dispose();
            });
        });
    }

    @Test
    @DisplayName("Verify ReviewAttendanceDialog on EDT: Roster table, Override dropdown, Finalize button")
    void testReviewAttendanceDialogEdtRendering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertDoesNotThrow(() -> {
                JFrame frame = new JFrame();
                ReviewAttendanceDialog dlg =
                        new ReviewAttendanceDialog(frame, testSession, sessionService, facultyUser, () -> {});
                assertNotNull(dlg);

                JTable table = findComponentOfType(dlg, JTable.class);
                assertNotNull(table, "Review roster table must be present");
                assertEquals(8, table.getColumnCount());
                assertEquals("Roll Number", table.getColumnName(2));
                assertEquals("Student Name", table.getColumnName(3));
                assertEquals("Check-In Time", table.getColumnName(4));
                assertEquals("Check-In Status", table.getColumnName(5));
                assertEquals("Final Status", table.getColumnName(6));
                assertEquals("Mandatory Remarks", table.getColumnName(7));

                List<JButton> buttons = findAllComponentsOfType(dlg, JButton.class);
                assertTrue(buttons.stream().anyMatch(b -> "Confirm Finalize Attendance".equals(b.getText())));

                dlg.dispose();
                frame.dispose();
            });
        });
    }

    @Test
    @DisplayName("Verify StudentCheckInDialog on EDT: Active session selector, 6-digit code input, Submit button")
    void testStudentCheckInDialogEdtRendering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertDoesNotThrow(() -> {
                JFrame frame = new JFrame();
                StudentCheckInDialog dlg =
                        new StudentCheckInDialog(frame, studentUser, sessionService, () -> {});
                assertNotNull(dlg);

                JComboBox<?> combo = findComponentOfType(dlg, JComboBox.class);
                assertNotNull(combo, "Active session selector combo must be present");

                JTextField textField = findComponentOfType(dlg, JTextField.class);
                assertNotNull(textField, "6-digit code entry field must be present");

                List<JButton> buttons = findAllComponentsOfType(dlg, JButton.class);
                assertTrue(buttons.stream().anyMatch(b -> "SUBMIT ATTENDANCE".equals(b.getText())));

                dlg.dispose();
                frame.dispose();
            });
        });
    }

    @Test
    @DisplayName("Verify StudentDashboardPanel on EDT: Prominent MARK ATTENDANCE action button")
    void testStudentDashboardPanelAttendanceActionEdtRendering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertDoesNotThrow(() -> {
                StudentDashboardPanel panel = new StudentDashboardPanel(studentUser);
                assertNotNull(panel);

                List<JButton> buttons = findAllComponentsOfType(panel, JButton.class);
                assertTrue(buttons.stream().anyMatch(b -> "MARK ATTENDANCE".equals(b.getText())),
                        "StudentDashboardPanel must contain prominent MARK ATTENDANCE button");
            });
        });
    }

    @Test
    @DisplayName("Verify ReportPanel on EDT: Attendance Defaulters/Eligible reports and Export to CSV button")
    void testReportPanelCsvExportEdtRendering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertDoesNotThrow(() -> {
                ReportPanel panel = new ReportPanel(adminUser);
                assertNotNull(panel);

                JComboBox<?> reportCombo = findComponentOfType(panel, JComboBox.class);
                assertNotNull(reportCombo);

                List<String> items = new ArrayList<>();
                for (int i = 0; i < reportCombo.getItemCount(); i++) {
                    items.add((String) reportCombo.getItemAt(i));
                }
                assertTrue(items.contains("Attendance Summary Report"));
                assertTrue(items.contains("Attendance Defaulters Report (< 75%)"));
                assertTrue(items.contains("Attendance Eligible Report (>= 75%)"));
                assertTrue(items.contains("Attendance Zero-Sessions Report (No Sessions Recorded)"));

                List<JButton> buttons = findAllComponentsOfType(panel, JButton.class);
                assertTrue(buttons.stream().anyMatch(b -> "Export to CSV".equals(b.getText())),
                        "ReportPanel must contain Export to CSV button");
            });
        });
    }

    // --- Component Traversal Utilities ---

    @SuppressWarnings("unchecked")
    private static <T extends Component> T findComponentOfType(Container container, Class<T> type) {
        for (Component comp : container.getComponents()) {
            if (type.isInstance(comp)) {
                return (T) comp;
            }
            if (comp instanceof Container) {
                T found = findComponentOfType((Container) comp, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Component> List<T> findAllComponentsOfType(Container container, Class<T> type) {
        List<T> list = new ArrayList<>();
        for (Component comp : container.getComponents()) {
            if (type.isInstance(comp)) {
                list.add((T) comp);
            }
            if (comp instanceof Container) {
                list.addAll(findAllComponentsOfType((Container) comp, type));
            }
        }
        return list;
    }
}

