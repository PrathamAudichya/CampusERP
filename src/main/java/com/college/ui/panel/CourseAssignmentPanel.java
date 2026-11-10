package com.college.ui.panel;

import com.college.dao.FacultyDAO;
import com.college.model.Course;
import com.college.model.CourseAssignment;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.User;
import com.college.service.CourseAssignmentService;
import com.college.service.CourseService;
import com.college.service.FacultyService;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Course faculty assignment management panel supporting Admin assignments and Faculty assignment inspection.
 */
public class CourseAssignmentPanel extends JPanel {

    private final User currentUser;
    private final CourseAssignmentService assignmentService;
    private final CourseService courseService;
    private final FacultyService facultyService;
    private final FacultyDAO facultyDAO;

    private JTable assignmentTable;
    private DefaultTableModel tableModel;
    private JComboBox<CourseItem> courseCombo;
    private JComboBox<FacultyItem> facultyCombo;
    private JTextField academicYearField;
    private JButton assignButton;
    private JButton deactivateButton;
    private JButton refreshButton;

    private Integer selectedAssignmentId = null;
    private final Map<Integer, String> courseNameMap = new HashMap<>();
    private final Map<Integer, String> facultyNameMap = new HashMap<>();

    public CourseAssignmentPanel(User currentUser) {
        this(currentUser, new CourseAssignmentService(), new CourseService(), new FacultyService(), new FacultyDAO());
    }

    public CourseAssignmentPanel(User currentUser, CourseAssignmentService assignmentService,
                                 CourseService courseService, FacultyService facultyService,
                                 FacultyDAO facultyDAO) {
        this.currentUser = currentUser;
        this.assignmentService = assignmentService;
        this.courseService = courseService;
        this.facultyService = facultyService;
        this.facultyDAO = facultyDAO;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(12, 12));
        setBackground(SwingUtils.BG_LIGHT);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Top Toolbar
        JPanel topBar = new JPanel(new BorderLayout(8, 8));
        topBar.setOpaque(false);

        JLabel titleLabel = new JLabel(currentUser.getRole() == Role.ADMIN ? "Course Faculty Assignments" : "My Teaching Assignments");
        titleLabel.setFont(SwingUtils.TITLE_FONT);
        titleLabel.setForeground(SwingUtils.PRIMARY_COLOR);
        topBar.add(titleLabel, BorderLayout.WEST);

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filterPanel.setOpaque(false);
        filterPanel.add(new JLabel("Academic Year:"));
        academicYearField = new JTextField("2026-2027", 9);
        academicYearField.addActionListener(e -> loadData());
        filterPanel.add(academicYearField);

        refreshButton = SwingUtils.createSecondaryButton("Refresh");
        refreshButton.addActionListener(e -> {
            loadCatalogs();
            loadData();
        });
        filterPanel.add(refreshButton);
        topBar.add(filterPanel, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);

        // Center Table
        String[] columns = {"ID", "Course Code & Title", "Assigned Faculty", "Academic Year", "Status"};
        tableModel = new DefaultTableModel(columns, 0);
        assignmentTable = SwingUtils.createStandardTable(tableModel);

        if (currentUser.getRole() == Role.ADMIN) {
            assignmentTable.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting() && assignmentTable.getSelectedRow() != -1) {
                    int row = assignmentTable.getSelectedRow();
                    selectedAssignmentId = (Integer) tableModel.getValueAt(row, 0);
                    boolean isActive = "Active".equals(tableModel.getValueAt(row, 4));
                    deactivateButton.setEnabled(isActive);
                }
            });
        }

        JScrollPane scrollPane = new JScrollPane(assignmentTable);
        add(scrollPane, BorderLayout.CENTER);

        // South Assignment Form (Admin Only)
        if (currentUser.getRole() == Role.ADMIN) {
            JPanel formCard = SwingUtils.createCardPanel("Assign Faculty to Course");
            JPanel formGrid = new JPanel(new GridLayout(1, 3, 8, 8));
            formGrid.setOpaque(false);

            courseCombo = new JComboBox<>();
            facultyCombo = new JComboBox<>();

            formGrid.add(createFieldBox("Course:", courseCombo));
            formGrid.add(createFieldBox("Faculty Member:", facultyCombo));

            JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
            buttonRow.setOpaque(false);

            assignButton = SwingUtils.createPrimaryButton("Assign Faculty");
            assignButton.addActionListener(e -> assignFaculty());

            deactivateButton = SwingUtils.createSecondaryButton("Deactivate Assignment");
            deactivateButton.setEnabled(false);
            deactivateButton.addActionListener(e -> deactivateAssignment());

            buttonRow.add(deactivateButton);
            buttonRow.add(assignButton);

            formCard.add(formGrid, BorderLayout.CENTER);
            formCard.add(buttonRow, BorderLayout.SOUTH);
            add(formCard, BorderLayout.SOUTH);
        }

        loadCatalogs();
        loadData();
    }

    private JPanel createFieldBox(String labelText, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(2, 2));
        p.setOpaque(false);
        JLabel l = new JLabel(labelText);
        l.setFont(SwingUtils.SMALL_FONT);
        p.add(l, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    private void loadCatalogs() {
        SwingUtils.executeAsync(
                this,
                () -> {
                    List<Course> courses = courseService.getAllActiveCourses(currentUser);
                    List<Faculty> facultyList = (currentUser.getRole() == Role.ADMIN) ? facultyService.getAllActiveFaculty(currentUser) : List.of();
                    return new Object[]{courses, facultyList};
                },
                result -> {
                    @SuppressWarnings("unchecked")
                    List<Course> courses = (List<Course>) result[0];
                    @SuppressWarnings("unchecked")
                    List<Faculty> facultyList = (List<Faculty>) result[1];

                    courseNameMap.clear();
                    facultyNameMap.clear();
                    if (courseCombo != null) courseCombo.removeAllItems();
                    if (facultyCombo != null) facultyCombo.removeAllItems();

                    for (Course c : courses) {
                        String desc = c.getCourseCode() + " - " + c.getName();
                        courseNameMap.put(c.getId(), desc);
                        if (courseCombo != null) courseCombo.addItem(new CourseItem(c.getId(), desc));
                    }

                    for (Faculty f : facultyList) {
                        String desc = f.getFirstName() + " " + f.getLastName() + " (" + f.getFacultyId() + ")";
                        facultyNameMap.put(f.getId(), desc);
                        if (facultyCombo != null) facultyCombo.addItem(new FacultyItem(f.getId(), desc));
                    }
                }
        );
    }

    public void loadData() {
        String year = academicYearField.getText().trim();
        refreshButton.setEnabled(false);

        SwingUtils.executeAsync(
                this,
                () -> {
                    if (currentUser.getRole() == Role.FACULTY) {
                        Faculty f = facultyDAO.findByUserId(currentUser.getId());
                        if (f == null) return List.<CourseAssignment>of();
                        return assignmentService.getActiveAssignmentsByFacultyAndYear(currentUser, f.getId(), year);
                    } else {
                        // Admin: get all assignments across all courses
                        List<Course> courses = courseService.getAllActiveCourses(currentUser);
                        List<CourseAssignment> all = new java.util.ArrayList<>();
                        for (Course c : courses) {
                            all.addAll(assignmentService.getAssignmentsByCourse(currentUser, c.getId()));
                        }
                        return all;
                    }
                },
                assignments -> {
                    tableModel.setRowCount(0);
                    for (CourseAssignment a : assignments) {
                        String cName = courseNameMap.getOrDefault(a.getCourseId(), "Course #" + a.getCourseId());
                        String fName = facultyNameMap.getOrDefault(a.getFacultyId(), "Faculty #" + a.getFacultyId());
                        tableModel.addRow(new Object[]{
                                a.getId(),
                                cName,
                                fName,
                                a.getAcademicYear(),
                                a.isActive() ? "Active" : "Inactive"
                        });
                    }
                    if (deactivateButton != null) deactivateButton.setEnabled(false);
                    refreshButton.setEnabled(true);
                },
                error -> refreshButton.setEnabled(true)
        );
    }

    private void assignFaculty() {
        CourseItem course = (CourseItem) courseCombo.getSelectedItem();
        FacultyItem faculty = (FacultyItem) facultyCombo.getSelectedItem();
        String year = academicYearField.getText().trim();

        if (course == null || faculty == null || year.isEmpty()) {
            SwingUtils.showWarning(this, "Please select both a course and a faculty member.");
            return;
        }

        assignButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> assignmentService.assignFaculty(currentUser, course.id, faculty.id, year),
                result -> {
                    assignButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Faculty assigned successfully.");
                    loadData();
                },
                error -> assignButton.setEnabled(true)
        );
    }

    private void deactivateAssignment() {
        if (selectedAssignmentId == null) return;
        boolean confirm = SwingUtils.showConfirm(this,
                "Deactivate course assignment ID " + selectedAssignmentId + "?",
                "Confirm Deactivation");
        if (!confirm) return;

        deactivateButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> {
                    assignmentService.deactivateAssignment(currentUser, selectedAssignmentId);
                    return null;
                },
                unused -> {
                    deactivateButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Assignment deactivated.");
                    loadData();
                },
                error -> deactivateButton.setEnabled(true)
        );
    }

    public static class CourseItem {
        public final int id;
        public final String name;
        public CourseItem(int id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }

    public static class FacultyItem {
        public final int id;
        public final String name;
        public FacultyItem(int id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }
}
