package com.college.ui.panel;

import com.college.dao.FacultyDAO;
import com.college.model.Course;
import com.college.model.CourseAssignment;
import com.college.model.Department;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.User;
import com.college.service.CourseAssignmentService;
import com.college.service.CourseService;
import com.college.service.DepartmentService;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Course catalog and administration panel supporting Admin CRUD and Faculty/Student catalog browsing.
 */
public class CoursePanel extends JPanel {

    private final User currentUser;
    private final CourseService courseService;
    private final DepartmentService departmentService;
    private final FacultyDAO facultyDAO;
    private final CourseAssignmentService assignmentService;

    private JTable courseTable;
    private DefaultTableModel tableModel;
    private JComboBox<DepartmentItem> deptFilterCombo;
    private JComboBox<DepartmentItem> deptFormCombo;

    private JTextField codeField;
    private JTextField titleField;
    private JSpinner creditsSpinner;
    private JSpinner semesterSpinner;
    private JSpinner capacitySpinner;

    private JButton createButton;
    private JButton updateButton;
    private JButton deleteButton;
    private JButton reactivateButton;
    private JButton refreshButton;

    private Integer selectedCourseId = null;
    private final Map<Integer, String> departmentMap = new HashMap<>();
    private final Map<Integer, Course> courseCache = new HashMap<>();

    public CoursePanel(User currentUser) {
        this(currentUser, new CourseService(), new DepartmentService(), new FacultyDAO(), new CourseAssignmentService());
    }

    public CoursePanel(User currentUser, CourseService courseService, DepartmentService departmentService) {
        this(currentUser, courseService, departmentService, new FacultyDAO(), new CourseAssignmentService());
    }

    public CoursePanel(User currentUser, CourseService courseService, DepartmentService departmentService,
                       FacultyDAO facultyDAO, CourseAssignmentService assignmentService) {
        this.currentUser = currentUser;
        this.courseService = courseService;
        this.departmentService = departmentService;
        this.facultyDAO = facultyDAO;
        this.assignmentService = assignmentService;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(12, 12));
        setBackground(SwingUtils.BG_LIGHT);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Top Toolbar
        JPanel topBar = new JPanel(new BorderLayout(8, 8));
        topBar.setOpaque(false);

        String titleText = "Course Catalog";
        if (currentUser.getRole() == Role.ADMIN) titleText = "Course Administration";
        else if (currentUser.getRole() == Role.FACULTY) titleText = "My Assigned Teaching Courses";

        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setFont(SwingUtils.TITLE_FONT);
        titleLabel.setForeground(SwingUtils.PRIMARY_COLOR);
        topBar.add(titleLabel, BorderLayout.WEST);

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filterPanel.setOpaque(false);

        filterPanel.add(new JLabel("Department:"));
        deptFilterCombo = new JComboBox<>();
        deptFilterCombo.addItem(new DepartmentItem(0, "All Departments"));
        deptFilterCombo.addActionListener(e -> loadData());
        filterPanel.add(deptFilterCombo);

        refreshButton = SwingUtils.createSecondaryButton("Refresh");
        refreshButton.addActionListener(e -> {
            loadDepartments();
            loadData();
        });
        filterPanel.add(refreshButton);
        topBar.add(filterPanel, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);

        // Center Table
        String[] columns = {"ID", "Course Code", "Title", "Credits", "Department", "Semester", "Capacity", "Status"};
        tableModel = new DefaultTableModel(columns, 0);
        courseTable = SwingUtils.createStandardTable(tableModel);

        if (currentUser.getRole() == Role.ADMIN) {
            courseTable.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting() && courseTable.getSelectedRow() != -1) {
                    populateFormFromSelection();
                }
            });
        }

        JScrollPane scrollPane = new JScrollPane(courseTable);
        add(scrollPane, BorderLayout.CENTER);

        // South Form (Admin only)
        if (currentUser.getRole() == Role.ADMIN) {
            JPanel formCard = SwingUtils.createCardPanel("Course Details & Modification");
            JPanel formGrid = new JPanel(new GridLayout(2, 4, 8, 8));
            formGrid.setOpaque(false);

            codeField = new JTextField();
            titleField = new JTextField();
            creditsSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 10, 1));
            semesterSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 8, 1));
            capacitySpinner = new JSpinner(new SpinnerNumberModel(60, 1, 500, 5));
            deptFormCombo = new JComboBox<>();

            formGrid.add(createFieldBox("Course Code (e.g. CS101):", codeField));
            formGrid.add(createFieldBox("Course Title:", titleField));
            formGrid.add(createFieldBox("Credits (1-10):", creditsSpinner));
            formGrid.add(createFieldBox("Semester (1-8):", semesterSpinner));

            formGrid.add(createFieldBox("Department:", deptFormCombo));
            formGrid.add(createFieldBox("Max Capacity:", capacitySpinner));
            formGrid.add(new JLabel("")); // spacer
            formGrid.add(new JLabel("")); // spacer

            JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
            buttonRow.setOpaque(false);

            createButton = SwingUtils.createPrimaryButton("Create Course");
            createButton.addActionListener(e -> createCourse());

            updateButton = SwingUtils.createSecondaryButton("Update Course");
            updateButton.setEnabled(false);
            updateButton.addActionListener(e -> updateCourse());

            deleteButton = SwingUtils.createSecondaryButton("Deactivate");
            deleteButton.setEnabled(false);
            deleteButton.addActionListener(e -> deleteCourse());

            reactivateButton = SwingUtils.createSecondaryButton("Reactivate");
            reactivateButton.setEnabled(false);
            reactivateButton.addActionListener(e -> reactivateCourse());

            JButton clearButton = SwingUtils.createSecondaryButton("Clear Form");
            clearButton.addActionListener(e -> clearForm());

            buttonRow.add(clearButton);
            buttonRow.add(reactivateButton);
            buttonRow.add(deleteButton);
            buttonRow.add(updateButton);
            buttonRow.add(createButton);

            formCard.add(formGrid, BorderLayout.CENTER);
            formCard.add(buttonRow, BorderLayout.SOUTH);
            add(formCard, BorderLayout.SOUTH);
        }

        loadDepartments();
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

    private void loadDepartments() {
        SwingUtils.executeAsync(
                this,
                () -> departmentService.getAllActiveDepartments(currentUser),
                departments -> {
                    departmentMap.clear();
                    if (deptFormCombo != null) deptFormCombo.removeAllItems();

                    for (Department d : departments) {
                        departmentMap.put(d.getId(), d.getCode() + " - " + d.getName());
                        if (deptFormCombo != null) {
                            deptFormCombo.addItem(new DepartmentItem(d.getId(), d.getCode() + " - " + d.getName()));
                        }
                    }

                    if (deptFilterCombo != null) {
                        DepartmentItem selected = (DepartmentItem) deptFilterCombo.getSelectedItem();
                        int selId = selected != null ? selected.id : 0;
                        deptFilterCombo.removeAllItems();
                        deptFilterCombo.addItem(new DepartmentItem(0, "All Departments"));
                        for (Department d : departments) {
                            deptFilterCombo.addItem(new DepartmentItem(d.getId(), d.getCode() + " - " + d.getName()));
                        }
                        for (int i = 0; i < deptFilterCombo.getItemCount(); i++) {
                            if (deptFilterCombo.getItemAt(i).id == selId) {
                                deptFilterCombo.setSelectedIndex(i);
                                break;
                            }
                        }
                    }
                }
        );
    }

    public void loadData() {
        DepartmentItem filter = (deptFilterCombo != null) ? (DepartmentItem) deptFilterCombo.getSelectedItem() : null;
        int filterDeptId = (filter != null) ? filter.id : 0;

        refreshButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> {
                    if (currentUser.getRole() == Role.FACULTY) {
                        Faculty f = facultyDAO.findByUserId(currentUser.getId());
                        if (f == null) return List.<Course>of();
                        List<CourseAssignment> assignments = assignmentService.getActiveAssignmentsByFacultyAndYear(currentUser, f.getId(), "2026-2027");
                        List<Course> myCourses = new ArrayList<>();
                        for (CourseAssignment ca : assignments) {
                            Course c = courseService.getCourseById(currentUser, ca.getCourseId());
                            if (c != null && (filterDeptId == 0 || c.getDepartmentId() == filterDeptId)) {
                                myCourses.add(c);
                            }
                        }
                        return myCourses;
                    } else if (filterDeptId > 0) {
                        return courseService.getCoursesByDepartment(currentUser, filterDeptId);
                    } else {
                        return courseService.getAllActiveCourses(currentUser);
                    }
                },
                courses -> {
                    tableModel.setRowCount(0);
                    courseCache.clear();
                    for (Course c : courses) {
                        courseCache.put(c.getId(), c);
                        String deptName = departmentMap.getOrDefault(c.getDepartmentId(), "Dept #" + c.getDepartmentId());
                        tableModel.addRow(new Object[]{
                                c.getId(),
                                c.getCourseCode(),
                                c.getName(),
                                c.getCredits(),
                                deptName,
                                c.getSemester(),
                                c.getMaxCapacity() != null ? c.getMaxCapacity() : "Unlimited",
                                c.isActive() ? "Active" : "Inactive"
                        });
                    }
                    clearForm();
                    refreshButton.setEnabled(true);
                },
                error -> refreshButton.setEnabled(true)
        );
    }

    private void populateFormFromSelection() {
        int row = courseTable.getSelectedRow();
        if (row == -1) return;

        selectedCourseId = (Integer) tableModel.getValueAt(row, 0);
        Course cached = courseCache.get(selectedCourseId);
        if (cached != null) {
            populateFormFromCourse(cached);
        } else {
            SwingUtils.executeAsync(
                    this,
                    () -> courseService.getCourseById(currentUser, selectedCourseId),
                    c -> {
                        if (c != null) {
                            courseCache.put(c.getId(), c);
                            populateFormFromCourse(c);
                        }
                    }
            );
        }
    }

    public void populateFormFromCourse(Course c) {
        if (c == null) return;
        selectedCourseId = c.getId();
        if (codeField != null) codeField.setText(c.getCourseCode());
        if (titleField != null) titleField.setText(c.getName());
        if (creditsSpinner != null) creditsSpinner.setValue(c.getCredits());

        if (semesterSpinner != null) {
            try {
                int sem = Integer.parseInt(c.getSemester() != null ? c.getSemester().trim() : "1");
                semesterSpinner.setValue(Math.max(1, Math.min(8, sem)));
            } catch (Exception ex) {
                semesterSpinner.setValue(1);
            }
        }

        if (capacitySpinner != null) {
            capacitySpinner.setValue(c.getMaxCapacity() != null ? c.getMaxCapacity() : 60);
        }

        if (deptFormCombo != null) {
            for (int i = 0; i < deptFormCombo.getItemCount(); i++) {
                if (deptFormCombo.getItemAt(i).id == c.getDepartmentId()) {
                    deptFormCombo.setSelectedIndex(i);
                    break;
                }
            }
        }

        if (updateButton != null) updateButton.setEnabled(true);
        if (deleteButton != null) deleteButton.setEnabled(c.isActive());
        if (reactivateButton != null) reactivateButton.setEnabled(!c.isActive());
    }

    private void createCourse() {
        Course c = buildCourseFromForm();
        if (c == null) return;

        createButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> courseService.createCourse(currentUser, c),
                created -> {
                    createButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Course created successfully: " + created.getCourseCode());
                    loadData();
                },
                error -> createButton.setEnabled(true)
        );
    }

    private void updateCourse() {
        if (selectedCourseId == null) return;
        Course c = buildCourseFromForm();
        if (c == null) return;
        c.setId(selectedCourseId);

        Course existing = courseCache.get(selectedCourseId);
        if (existing != null) {
            c.setActive(existing.isActive());
            if (capacitySpinner == null && existing.getMaxCapacity() != null) {
                c.setMaxCapacity(existing.getMaxCapacity());
            }
        } else {
            c.setActive(true);
        }

        updateButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> courseService.updateCourse(currentUser, c),
                updated -> {
                    updateButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Course updated successfully.");
                    loadData();
                },
                error -> updateButton.setEnabled(true)
        );
    }

    private void deleteCourse() {
        if (selectedCourseId == null) return;
        boolean confirm = SwingUtils.showConfirm(this,
                "Are you sure you want to deactivate course ID " + selectedCourseId + "?",
                "Confirm Deactivation");
        if (!confirm) return;

        deleteButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> {
                    courseService.deleteCourse(currentUser, selectedCourseId);
                    return null;
                },
                unused -> {
                    deleteButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Course deactivated.");
                    loadData();
                },
                error -> deleteButton.setEnabled(true)
        );
    }

    private void reactivateCourse() {
        if (selectedCourseId == null) return;
        reactivateButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> courseService.reactivateCourse(currentUser, selectedCourseId),
                reactivated -> {
                    reactivateButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Course reactivated.");
                    loadData();
                },
                error -> reactivateButton.setEnabled(true)
        );
    }

    private Course buildCourseFromForm() {
        String code = codeField.getText().trim();
        String title = titleField.getText().trim();
        int credits = (Integer) creditsSpinner.getValue();
        int semester = (Integer) semesterSpinner.getValue();
        int capacity = (capacitySpinner != null) ? (Integer) capacitySpinner.getValue() : 60;

        DepartmentItem dept = (DepartmentItem) deptFormCombo.getSelectedItem();
        if (dept == null || dept.id <= 0) {
            SwingUtils.showWarning(this, "Please select a valid department.");
            return null;
        }

        Course c = new Course();
        c.setCourseCode(code);
        c.setName(title);
        c.setCredits(credits);
        c.setSemester(String.valueOf(semester));
        c.setMaxCapacity(capacity);
        c.setDepartmentId(dept.id);
        return c;
    }

    private void clearForm() {
        selectedCourseId = null;
        if (codeField != null) codeField.setText("");
        if (titleField != null) titleField.setText("");
        if (creditsSpinner != null) creditsSpinner.setValue(4);
        if (semesterSpinner != null) semesterSpinner.setValue(1);
        if (capacitySpinner != null) capacitySpinner.setValue(60);
        if (updateButton != null) updateButton.setEnabled(false);
        if (deleteButton != null) deleteButton.setEnabled(false);
        if (reactivateButton != null) reactivateButton.setEnabled(false);
        if (courseTable != null) courseTable.clearSelection();
    }

    // =========================================================================
    // Testing & Form Inspection Accessors
    // =========================================================================
    public Integer getSelectedCourseId() { return selectedCourseId; }
    public String getCourseCodeText() { return codeField != null ? codeField.getText() : ""; }
    public String getTitleText() { return titleField != null ? titleField.getText() : ""; }
    public int getCreditsValue() { return creditsSpinner != null ? (Integer) creditsSpinner.getValue() : 4; }
    public int getSemesterValue() { return semesterSpinner != null ? (Integer) semesterSpinner.getValue() : 1; }
    public int getCapacityValue() { return capacitySpinner != null ? (Integer) capacitySpinner.getValue() : 60; }
    public int getSelectedDepartmentId() {
        if (deptFormCombo == null) return 0;
        DepartmentItem item = (DepartmentItem) deptFormCombo.getSelectedItem();
        return item != null ? item.id : 0;
    }
    public void setTitleText(String t) { if (titleField != null) titleField.setText(t); }
    public void setCreditsValue(int c) { if (creditsSpinner != null) creditsSpinner.setValue(c); }
    public void setCapacityValue(int cap) { if (capacitySpinner != null) capacitySpinner.setValue(cap); }
    public Course getCourseFromForm() { return buildCourseFromForm(); }

    public static class DepartmentItem {
        public final int id;
        public final String name;

        public DepartmentItem(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
