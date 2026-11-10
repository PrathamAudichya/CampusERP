package com.college.ui;

import com.college.TestDBHelper;
import com.college.dao.FacultyDAO;
import com.college.dao.StudentDAO;
import com.college.dao.UserDAO;
import com.college.model.Department;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.User;
import com.college.service.DepartmentService;
import com.college.service.FacultyService;
import com.college.service.StudentService;
import com.college.ui.components.FacultyDetailsDialog;
import com.college.ui.components.StudentDetailsDialog;
import com.college.ui.panel.FacultyPanel;
import com.college.ui.panel.StudentPanel;
import com.college.ui.theme.CampusTheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated UI and Responsive Layout Test Suite for Campus ERP v2.0.7 Directory Screens.
 * Validates horizontal clipping fixes, unified toolbars, cell tooltips, modal details dialogs,
 * real-time filtering, and rendering across required resolutions (1102x327, 1167x347, 1366x768, 1600x900, 1920x1080).
 */
public class DirectoryResponsiveUITest {

    private User adminUser;
    private StudentService studentService;
    private FacultyService facultyService;
    private DepartmentService departmentService;
    private StudentDAO studentDAO;
    private FacultyDAO facultyDAO;
    private UserDAO userDAO;

    @BeforeEach
    public void setUp() throws Exception {
        CampusTheme.installGlobalTheme();
        TestDBHelper.initTestDatabase();
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
            userDAO = new UserDAO();
            User admin = new User("admin", "$2a$12$e8x0RkW5v0m0u9n5s0e0u0u0u0u0u0u0u0u0u0u0u0u0u0u0u0u0u", Role.ADMIN);
            admin.setActive(true);
            adminUser = userDAO.insert(conn, admin);
        }

        studentService = new StudentService();
        facultyService = new FacultyService();
        departmentService = new DepartmentService();
        studentDAO = new StudentDAO();
        facultyDAO = new FacultyDAO();

        // Seed sample department, student, and faculty for UI table tests
        Department d = departmentService.createDepartment(adminUser, "CSE", "Computer Science and Engineering");

        Student s = new Student();
        s.setStudentId("STU2026001");
        s.setFirstName("Alice");
        s.setLastName("Johnson");
        s.setDepartmentId(d.getId());
        s.setSemester("3");
        s.setEmail("alice.johnson@campus.edu");
        s.setPhone("+91 98765 43210");
        s.setAdmissionDate("2026-08-01");
        s.setDateOfBirth("2005-03-12");
        studentService.createStudentWithAutoAccount(adminUser, s);

        Faculty f = new Faculty();
        f.setFacultyId("FAC2026001");
        f.setFirstName("Dr. Alan");
        f.setLastName("Turing");
        f.setDepartmentId(d.getId());
        f.setSpecialization("Computer Systems and Theoretical Architecture");
        f.setEmail("alan.turing@campus.edu");
        f.setPhone("+91 98765 11223");
        f.setJoinDate("2026-08-01");
        facultyService.createFaculty(adminUser, f, "faculty123");
    }

    @AfterEach
    public void tearDown() throws Exception {
        try (Connection conn = TestDBHelper.getTestConnection()) {
            TestDBHelper.cleanAllTables(conn);
        }
    }

    @Test
    @DisplayName("Student Directory: Verify table row sorter and instant filtering by ID, name, email")
    public void testStudentInstantSearchFiltering() throws Exception {
        final StudentPanel[] panelHolder = new StudentPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panelHolder[0] = new StudentPanel(adminUser, studentService, departmentService, studentDAO);
        });

        Thread.sleep(800);

        SwingUtilities.invokeAndWait(() -> {
            StudentPanel panel = panelHolder[0];
            JTable table = findComponent(panel, JTable.class);
            assertNotNull(table, "Student table must exist");
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            assertTrue(model.getRowCount() > 0, "Student directory should have loaded records");

            TableRowSorter<?> sorter = (TableRowSorter<?>) table.getRowSorter();
            assertNotNull(sorter, "Table row sorter must be installed");

            JTextField searchField = findComponent(panel, JTextField.class);
            assertNotNull(searchField, "Search textfield should exist in toolbar");

            searchField.setText("Alice");
        });

        Thread.sleep(200);

        SwingUtilities.invokeAndWait(() -> {
            StudentPanel panel = panelHolder[0];
            JTable table = findComponent(panel, JTable.class);
            assertTrue(table.getRowCount() >= 1, "Filtered row count should include matches for 'Alice'");

            JTextField searchField = findComponent(panel, JTextField.class);
            searchField.setText("");
        });

        Thread.sleep(200);

        SwingUtilities.invokeAndWait(() -> {
            StudentPanel panel = panelHolder[0];
            JTable table = findComponent(panel, JTable.class);
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            assertEquals(model.getRowCount(), table.getRowCount(), "Clearing search restores all rows");
        });
    }

    @Test
    @DisplayName("Faculty Directory: Verify table row sorter and instant filtering by specialization and name")
    public void testFacultyInstantSearchFiltering() throws Exception {
        final FacultyPanel[] panelHolder = new FacultyPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panelHolder[0] = new FacultyPanel(adminUser, facultyService, departmentService, facultyDAO);
        });

        Thread.sleep(800);

        SwingUtilities.invokeAndWait(() -> {
            FacultyPanel panel = panelHolder[0];
            JTable table = findComponent(panel, JTable.class);
            assertNotNull(table, "Faculty table must exist");
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            assertTrue(model.getRowCount() > 0, "Faculty directory should have loaded records");

            TableRowSorter<?> sorter = (TableRowSorter<?>) table.getRowSorter();
            assertNotNull(sorter, "Table row sorter must be installed on faculty table");

            JTextField searchField = findComponent(panel, JTextField.class);
            assertNotNull(searchField, "Search text field should exist in faculty toolbar");

            searchField.setText("Computer");
        });

        Thread.sleep(200);

        SwingUtilities.invokeAndWait(() -> {
            FacultyPanel panel = panelHolder[0];
            JTable table = findComponent(panel, JTable.class);
            assertTrue(table.getRowCount() >= 1, "Should filter faculty matching 'Computer'");

            JTextField searchField = findComponent(panel, JTextField.class);
            searchField.setText("");
        });

        Thread.sleep(200);

        SwingUtilities.invokeAndWait(() -> {
            FacultyPanel panel = panelHolder[0];
            JTable table = findComponent(panel, JTable.class);
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            assertEquals(model.getRowCount(), table.getRowCount(), "Clearing search restores all faculty rows");
        });
    }

    @Test
    @DisplayName("Cell Tooltip Verification: Hovering over cells reveals complete untruncated content")
    public void testCellTooltipsConfigured() throws Exception {
        final FacultyPanel[] panelHolder = new FacultyPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panelHolder[0] = new FacultyPanel(adminUser, facultyService, departmentService, facultyDAO);
        });

        Thread.sleep(800);

        SwingUtilities.invokeAndWait(() -> {
            FacultyPanel panel = panelHolder[0];
            JTable table = findComponent(panel, JTable.class);
            assertNotNull(table);
            assertTrue(table.getRowCount() > 0);

            for (int col : new int[]{1, 2, 3, 4, 5}) {
                TableCellRenderer renderer = table.getCellRenderer(0, col);
                Component comp = table.prepareRenderer(renderer, 0, col);
                assertTrue(comp instanceof JComponent, "Rendered component must be a JComponent");
                JComponent jc = (JComponent) comp;
                String tooltip = jc.getToolTipText();
                Object value = table.getValueAt(0, col);
                if (value != null && !value.toString().trim().isEmpty()) {
                    assertNotNull(tooltip, "Cell tooltip must not be null for column " + col);
                    assertEquals(value.toString(), tooltip, "Tooltip must match the exact cell text");
                }
            }
        });
    }

    @Test
    @DisplayName("Modal Details Dialog: StudentDetailsDialog renders unclipped record profile")
    public void testStudentDetailsDialogRendering() throws Exception {
        Student s = new Student();
        s.setId(101);
        s.setStudentId("STU2026099");
        s.setFirstName("Rajesh");
        s.setLastName("Venkatesh");
        s.setDepartmentId(1);
        s.setSemester("4");
        s.setEmail("rajesh.venkatesh@university.ac.in");
        s.setPhone("+91 98765 43210");
        s.setAdmissionDate("2026-08-01");
        s.setDateOfBirth("2004-05-15");
        s.setActive(true);

        Department d = new Department();
        d.setId(1);
        d.setCode("CSE");
        d.setName("Computer Science and Engineering");

        User u = new User();
        u.setId(50);
        u.setUsername("STU2026099");
        u.setRole(Role.STUDENT);
        u.setActive(true);
        u.setMustChangePassword(false);

        SwingUtilities.invokeAndWait(() -> {
            JFrame parentFrame = new JFrame();
            StudentDetailsDialog dlg = new StudentDetailsDialog(parentFrame, s, d, u);
            dlg.pack();
            assertTrue(dlg.getWidth() >= 500, "Student details dialog has comfortable width");
            assertTrue(dlg.getHeight() >= 400, "Student details dialog has comfortable height");
            dlg.dispose();
            parentFrame.dispose();
        });
    }

    @Test
    @DisplayName("Modal Details Dialog: FacultyDetailsDialog renders unclipped appointment profile")
    public void testFacultyDetailsDialogRendering() throws Exception {
        Faculty f = new Faculty();
        f.setId(201);
        f.setFacultyId("FAC2026099");
        f.setFirstName("Dr. Aris");
        f.setLastName("Theodoropoulos");
        f.setDepartmentId(1);
        f.setSpecialization("Distributed Fault-Tolerant Consensus Algorithms & Cloud Architecture");
        f.setEmail("a.theodoropoulos@university.ac.in");
        f.setPhone("+91 98765 11223");
        f.setJoinDate("2020-07-15");
        f.setActive(true);

        Department d = new Department();
        d.setId(1);
        d.setCode("CSE");
        d.setName("Computer Science and Engineering");

        SwingUtilities.invokeAndWait(() -> {
            JFrame parentFrame = new JFrame();
            FacultyDetailsDialog dlg = new FacultyDetailsDialog(parentFrame, f, d);
            dlg.pack();
            assertTrue(dlg.getWidth() >= 500, "Faculty details dialog has comfortable width");
            assertTrue(dlg.getHeight() >= 400, "Faculty details dialog has comfortable height");
            dlg.dispose();
            parentFrame.dispose();
        });
    }

    @Test
    @DisplayName("Student Directory Layout: Responsive Rendering Across 5 Standard & Constrained Resolutions")
    public void testStudentDirectoryResponsiveResolutions() throws Exception {
        int[][] resolutions = {
                {1102, 327},
                {1167, 347},
                {1366, 768},
                {1600, 900},
                {1920, 1080}
        };

        final StudentPanel[] panelHolder = new StudentPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panelHolder[0] = new StudentPanel(adminUser, studentService, departmentService, studentDAO);
        });

        Thread.sleep(700);

        for (int[] res : resolutions) {
            int width = res[0];
            int height = res[1];

            SwingUtilities.invokeAndWait(() -> {
                StudentPanel panel = panelHolder[0];
                renderOffscreen(panel, width, height);

                JLabel titleLabel = findLabelWithText(panel, "Student Directory");
                assertNotNull(titleLabel, "Title 'Student Directory' must exist at " + width + "x" + height);
                assertTrue(titleLabel.isShowing() || titleLabel.isVisible(), "Title label must be visible at " + width + "x" + height);

                JLabel countBadge = findComponent(panel, JLabel.class, l -> l.getText() != null && l.getText().contains("record"));
                assertNotNull(countBadge, "Record count badge must exist at " + width + "x" + height);

                JButton refreshBtn = findButtonWithText(panel, "Refresh");
                assertNotNull(refreshBtn, "Refresh button must exist at " + width + "x" + height);

                JButton detailsBtn = findButtonWithText(panel, "View Details");
                assertNotNull(detailsBtn, "View Details button must exist at " + width + "x" + height);

                JTable table = findComponent(panel, JTable.class);
                assertNotNull(table, "Student table must exist at " + width + "x" + height);
                JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, table);
                assertNotNull(scrollPane, "Table must be in JScrollPane at " + width + "x" + height);
                assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED, scrollPane.getHorizontalScrollBarPolicy());

                int statusColIdx = table.getColumnModel().getColumnIndex("Status");
                assertTrue(statusColIdx >= 0, "Status column must exist at " + width + "x" + height);
                assertTrue(table.getColumnModel().getColumn(statusColIdx).getMinWidth() >= 70, "Status column must have sensible minimum width");
            });
        }
    }

    @Test
    @DisplayName("Faculty Directory Layout: Responsive Rendering Across 5 Standard & Constrained Resolutions")
    public void testFacultyDirectoryResponsiveResolutions() throws Exception {
        int[][] resolutions = {
                {1102, 327},
                {1167, 347},
                {1366, 768},
                {1600, 900},
                {1920, 1080}
        };

        final FacultyPanel[] panelHolder = new FacultyPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panelHolder[0] = new FacultyPanel(adminUser, facultyService, departmentService, facultyDAO);
        });

        Thread.sleep(700);

        for (int[] res : resolutions) {
            int width = res[0];
            int height = res[1];

            SwingUtilities.invokeAndWait(() -> {
                FacultyPanel panel = panelHolder[0];
                renderOffscreen(panel, width, height);

                JLabel titleLabel = findLabelWithText(panel, "Faculty Directory");
                assertNotNull(titleLabel, "Title 'Faculty Directory' must exist at " + width + "x" + height);
                assertTrue(titleLabel.isShowing() || titleLabel.isVisible(), "Title label must be visible at " + width + "x" + height);

                JButton refreshBtn = findButtonWithText(panel, "Refresh");
                assertNotNull(refreshBtn, "Refresh button must exist at " + width + "x" + height);

                JButton detailsBtn = findButtonWithText(panel, "View Details");
                assertNotNull(detailsBtn, "View Details button must exist at " + width + "x" + height);

                JTable table = findComponent(panel, JTable.class);
                assertNotNull(table, "Faculty table must exist at " + width + "x" + height);
                JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, table);
                assertNotNull(scrollPane, "Table must be in JScrollPane at " + width + "x" + height);
                assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED, scrollPane.getHorizontalScrollBarPolicy());

                int specColIdx = table.getColumnModel().getColumnIndex("Specialization");
                int emailColIdx = table.getColumnModel().getColumnIndex("Email");
                int deptColIdx = table.getColumnModel().getColumnIndex("Department");
                assertTrue(table.getColumnModel().getColumn(specColIdx).getPreferredWidth() >= 200, "Specialization gets ample preferred width");
                assertTrue(table.getColumnModel().getColumn(emailColIdx).getPreferredWidth() >= 200, "Email gets ample preferred width");
                assertTrue(table.getColumnModel().getColumn(deptColIdx).getPreferredWidth() >= 180, "Department gets ample preferred width");
            });
        }
    }

    private void renderOffscreen(JComponent comp, int width, int height) {
        JFrame frame = new JFrame();
        frame.setUndecorated(true);
        frame.setSize(width, height);
        frame.setLayout(new BorderLayout());
        frame.add(comp, BorderLayout.CENTER);
        frame.addNotify();
        frame.validate();
        frame.doLayout();

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        comp.paint(g2);
        g2.dispose();
        frame.dispose();
    }

    @SuppressWarnings("unchecked")
    private <T extends Component> T findComponent(Container container, Class<T> clazz) {
        return findComponent(container, clazz, null);
    }

    @SuppressWarnings("unchecked")
    private <T extends Component> T findComponent(Container container, Class<T> clazz, java.util.function.Predicate<T> predicate) {
        for (Component c : container.getComponents()) {
            if (clazz.isInstance(c)) {
                T match = (T) c;
                if (predicate == null || predicate.test(match)) {
                    return match;
                }
            }
            if (c instanceof Container) {
                T nested = findComponent((Container) c, clazz, predicate);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private JLabel findLabelWithText(Container container, String prefix) {
        return findComponent(container, JLabel.class, l -> l.getText() != null && l.getText().startsWith(prefix));
    }

    private JButton findButtonWithText(Container container, String text) {
        return findComponent(container, JButton.class, b -> b.getText() != null && b.getText().contains(text));
    }
}
