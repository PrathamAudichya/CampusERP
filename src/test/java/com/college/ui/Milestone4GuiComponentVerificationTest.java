package com.college.ui;

import com.college.config.DatabaseInitializer;
import com.college.dao.DepartmentDAO;
import com.college.dao.UserDAO;
import com.college.model.Department;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.User;
import com.college.service.AcademicProgramService;
import com.college.service.DepartmentService;
import com.college.ui.components.StudentDetailsDialog;
import com.college.ui.dialog.AcademicProgramDialog;
import com.college.ui.panel.AcademicOverviewPanel;
import com.college.ui.panel.AcademicProgramPanel;
import com.college.ui.panel.CohortProgressionPanel;
import com.college.ui.panel.DepartmentPanel;
import com.college.ui.panel.ProgrammeCurriculumPanel;
import org.junit.jupiter.api.AfterAll;
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
 * Headless Swing Event Dispatch Thread (EDT) component smoke/verification test for Milestone 4.
 * Note: These automated tests verify Swing component instantiation, layouts, and data bindings
 * on the EDT. They do not substitute for actual end-user interactive GUI acceptance testing
 * performed on the desktop using run-campus-erp.bat.
 */
public class Milestone4GuiComponentVerificationTest {

    private static final Path DEMO_DB_PATH = Paths.get("target", "test-dbs-m4", "gui-verification-m4.db");
    private static User adminUser;
    private static String savedDbProperty;

    @BeforeAll
    public static void setUpDemoDatabase() throws Exception {
        savedDbProperty = System.getProperty("college.db.path");
        Files.createDirectories(DEMO_DB_PATH.getParent());
        Files.deleteIfExists(DEMO_DB_PATH);

        System.setProperty("college.db.path", DEMO_DB_PATH.toAbsolutePath().toString());

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DEMO_DB_PATH.toAbsolutePath().toString())) {
            DatabaseInitializer.initializeWithConnection(conn);
        }

        UserDAO userDAO = new UserDAO();
        adminUser = userDAO.findByUsername("admin");
        if (adminUser == null) {
            adminUser = new User("admin", "hash", Role.ADMIN);
            adminUser.setId(1);
            adminUser.setActive(true);
        }
    }

    @Test
    @DisplayName("Verify DepartmentPanel on EDT: Table, Filters, Form Inputs, Action Buttons")
    void testDepartmentPanelEdtRendering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertDoesNotThrow(() -> {
                DepartmentPanel panel = new DepartmentPanel(adminUser);
                assertNotNull(panel);

                JTable table = findComponentOfType(panel, JTable.class);
                assertNotNull(table, "DepartmentPanel must contain a department table");

                List<JButton> buttons = findAllComponentsOfType(panel, JButton.class);
                assertTrue(buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Create")),
                        "Must contain Create Department button");
                assertTrue(buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Refresh")),
                        "Must contain Refresh button");

                List<JComboBox> combos = findAllComponentsOfType(panel, JComboBox.class);
                assertFalse(combos.isEmpty(), "Must contain School selector combo box");
            });
        });
    }

    @Test
    @DisplayName("Verify AcademicProgramPanel on EDT: Program Table, Degree Filters, Action Controls")
    void testAcademicProgramPanelEdtRendering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertDoesNotThrow(() -> {
                AcademicProgramPanel panel = new AcademicProgramPanel(adminUser);
                assertNotNull(panel);

                JTable table = findComponentOfType(panel, JTable.class);
                assertNotNull(table, "AcademicProgramPanel must contain a program table");

                List<JButton> buttons = findAllComponentsOfType(panel, JButton.class);
                assertTrue(buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Add Programme")),
                        "Must contain Add Programme button");
                assertTrue(buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Edit Programme")),
                        "Must contain Edit Programme button");

                List<JComboBox> combos = findAllComponentsOfType(panel, JComboBox.class);
                assertTrue(combos.size() >= 2, "Must contain Level and School filter combos");
            });
        });
    }

    @Test
    @DisplayName("Verify AcademicProgramDialog on EDT: Form inputs, spinners, duration calculation")
    void testAcademicProgramDialogEdtRendering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertDoesNotThrow(() -> {
                JFrame frame = new JFrame();
                AcademicProgramDialog dialog = new AcademicProgramDialog(
                        frame,
                        adminUser,
                        new AcademicProgramService(),
                        new DepartmentService(),
                        null,
                        () -> {}
                );
                assertNotNull(dialog);
                assertEquals("Add Academic Programme", dialog.getTitle());

                List<JTextField> textFields = findAllComponentsOfType(dialog, JTextField.class);
                assertFalse(textFields.isEmpty(), "Dialog must contain text fields for code, name, regulation, etc.");

                List<JComboBox> combos = findAllComponentsOfType(dialog, JComboBox.class);
                assertTrue(combos.size() >= 2, "Dialog must contain combos for level and department");

                List<JSpinner> spinners = findAllComponentsOfType(dialog, JSpinner.class);
                assertFalse(spinners.isEmpty(), "Dialog must contain duration spinner");

                dialog.dispose();
                frame.dispose();
            });
        });
    }

    @Test
    @DisplayName("Verify ProgrammeCurriculumPanel on EDT: Programme selectors, semester tabs, mapping table")
    void testProgrammeCurriculumPanelEdtRendering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertDoesNotThrow(() -> {
                ProgrammeCurriculumPanel panel = new ProgrammeCurriculumPanel(adminUser);
                assertNotNull(panel);

                JTable table = findComponentOfType(panel, JTable.class);
                assertNotNull(table, "Curriculum panel must contain curriculum mapping table");

                List<JComboBox> combos = findAllComponentsOfType(panel, JComboBox.class);
                assertTrue(combos.size() >= 2, "Must contain Programme, Semester, or Regulation combos");

                List<JButton> buttons = findAllComponentsOfType(panel, JButton.class);
                assertTrue(buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Assign Course to Curriculum")),
                        "Must contain Assign Course to Curriculum mapping button");
            });
        });
    }

    @Test
    @DisplayName("Verify CohortProgressionPanel on EDT: Cohort & Student Tables, Progression Action Buttons")
    void testCohortProgressionPanelEdtRendering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertDoesNotThrow(() -> {
                CohortProgressionPanel panel = new CohortProgressionPanel(adminUser);
                assertNotNull(panel);

                List<JTable> tables = findAllComponentsOfType(panel, JTable.class);
                assertTrue(tables.size() >= 2, "Must contain both Cohort table and Student roster table");

                List<JButton> buttons = findAllComponentsOfType(panel, JButton.class);
                assertTrue(buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Create New Cohort")),
                        "Must contain Create New Cohort button");
                assertTrue(buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Promote to Next Semester")),
                        "Must contain Promote to Next Semester button");
                assertTrue(buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Hold Promotion")),
                        "Must contain Hold Promotion button");
                assertTrue(buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Repeat Semester")),
                        "Must contain Repeat Semester button");
            });
        });
    }

    @Test
    @DisplayName("Verify AcademicOverviewPanel on EDT: Metric cards, breakdown tables, filters")
    void testAcademicOverviewPanelEdtRendering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertDoesNotThrow(() -> {
                AcademicOverviewPanel panel = new AcademicOverviewPanel(adminUser);
                assertNotNull(panel);

                List<JTable> tables = findAllComponentsOfType(panel, JTable.class);
                assertTrue(tables.size() >= 2, "Must contain distribution tables (programmes, semesters, cohorts)");

                List<JComboBox> combos = findAllComponentsOfType(panel, JComboBox.class);
                assertFalse(combos.isEmpty(), "Must contain School/Department filter combos");
            });
        });
    }

    @Test
    @DisplayName("Verify StudentDetailsDialog on EDT: Displays Academic Programme, School, Cohort, Graduation Year")
    void testStudentDetailsDialogEdtRendering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertDoesNotThrow(() -> {
                JFrame frame = new JFrame();
                Department dept = new Department("CS", "Computer Science", "School of Computing & Data Science", "CS Dept", "HOD", "cs@test.test", "123");
                dept.setId(1);

                Student student = new Student();
                student.setId(1);
                student.setStudentId("STU2026M4001");
                student.setFirstName("Ananya");
                student.setLastName("Sharma");
                student.setEmail("ananya@test.test");
                student.setSemester("3");
                student.setProgramName("B.Tech in Artificial Intelligence");
                student.setDegree("B.Tech");
                student.setSchoolName("School of Computing & Data Science");
                student.setCohortCode("BTECH-AI-2025");
                student.setExpectedGraduationYear("2029");
                student.setActive(true);

                StudentDetailsDialog dialog = new StudentDetailsDialog(frame, student, dept, adminUser);
                assertNotNull(dialog);
                assertTrue(dialog.getTitle().contains("STU2026M4001"));

                dialog.dispose();
                frame.dispose();
            });
        });
    }

    @Test
    @DisplayName("Verify MainFrame on EDT: Contains Academic Overview, Programmes, Curriculum, Cohorts views")
    void testMainFrameMilestone4ViewsRegistered() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertDoesNotThrow(() -> {
                MainFrame mainFrame = new MainFrame(adminUser);
                assertNotNull(mainFrame);

                // Check sidebar buttons
                List<JButton> buttons = findAllComponentsOfType(mainFrame, JButton.class);
                assertTrue(buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Academic Overview")),
                        "MainFrame sidebar must include Academic Overview");
                assertTrue(buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Programmes")),
                        "MainFrame sidebar must include Programmes");
                assertTrue(buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Curriculum")),
                        "MainFrame sidebar must include Curriculum");
                assertTrue(buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Cohorts & Progression")),
                        "MainFrame sidebar must include Cohorts & Progression");

                mainFrame.dispose();
            });
        });
    }

    // Swing hierarchy reflection helpers
    private static <T extends Component> T findComponentOfType(Container container, Class<T> clazz) {
        for (Component comp : container.getComponents()) {
            if (clazz.isInstance(comp)) {
                return clazz.cast(comp);
            }
            if (comp instanceof Container) {
                T found = findComponentOfType((Container) comp, clazz);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T extends Component> List<T> findAllComponentsOfType(Container container, Class<T> clazz) {
        List<T> result = new ArrayList<>();
        findAllComponentsOfTypeRecursive(container, clazz, result);
        return result;
    }

    private static <T extends Component> void findAllComponentsOfTypeRecursive(Container container, Class<T> clazz, List<T> result) {
        for (Component comp : container.getComponents()) {
            if (clazz.isInstance(comp)) {
                result.add(clazz.cast(comp));
            }
            if (comp instanceof Container) {
                findAllComponentsOfTypeRecursive((Container) comp, clazz, result);
            }
        }
    }

    @AfterAll
    public static void tearDown() {
        if (savedDbProperty != null) {
            System.setProperty("college.db.path", savedDbProperty);
        } else {
            System.clearProperty("college.db.path");
        }
    }
}
