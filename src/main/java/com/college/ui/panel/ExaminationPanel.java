package com.college.ui.panel;

import com.college.dao.FacultyDAO;
import com.college.dao.StudentDAO;
import com.college.model.Course;
import com.college.model.CourseAssignment;
import com.college.model.Enrollment;
import com.college.model.Examination;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.User;
import com.college.service.CourseAssignmentService;
import com.college.service.CourseService;
import com.college.service.EnrollmentService;
import com.college.service.ExaminationService;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Examination scheduling and administration panel supporting Admin/Faculty exam creation
 * and Student scheduled examination viewing.
 */
public class ExaminationPanel extends JPanel {

    private final User currentUser;
    private final ExaminationService examinationService;
    private final CourseService courseService;
    private final CourseAssignmentService assignmentService;
    private final EnrollmentService enrollmentService;
    private final FacultyDAO facultyDAO;
    private final StudentDAO studentDAO;

    private JTable examTable;
    private DefaultTableModel tableModel;
    private JComboBox<CourseItem> courseFilterCombo;
    private JComboBox<CourseItem> courseFormCombo;
    private JTextField academicYearField;

    private JTextField nameField;
    private JTextField examDateField;
    private JComboBox<String> typeCombo;
    private JSpinner maxMarksSpinner;
    private JSpinner weightageSpinner;
    private JTextField examYearField;

    private JButton createButton;
    private JButton updateButton;
    private JButton deleteButton;
    private JButton refreshButton;

    private Integer selectedExamId = null;
    private final Map<Integer, String> courseNameMap = new HashMap<>();
    private final Map<Integer, Examination> examCache = new HashMap<>();

    public ExaminationPanel(User currentUser) {
        this(currentUser, new ExaminationService(), new CourseService(), new CourseAssignmentService(),
                new EnrollmentService(), new FacultyDAO(), new StudentDAO());
    }

    public ExaminationPanel(User currentUser, ExaminationService examinationService, CourseService courseService,
                            CourseAssignmentService assignmentService, EnrollmentService enrollmentService,
                            FacultyDAO facultyDAO, StudentDAO studentDAO) {
        this.currentUser = currentUser;
        this.examinationService = examinationService;
        this.courseService = courseService;
        this.assignmentService = assignmentService;
        this.enrollmentService = enrollmentService;
        this.facultyDAO = facultyDAO;
        this.studentDAO = studentDAO;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(12, 12));
        setBackground(SwingUtils.BG_LIGHT);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Top Toolbar
        JPanel topBar = new JPanel(new BorderLayout(8, 8));
        topBar.setOpaque(false);

        String title = "Examination Schedules";
        if (currentUser.getRole() == Role.ADMIN) title = "Examination Administration & Schedules";
        else if (currentUser.getRole() == Role.STUDENT) title = "My Upcoming Examinations";

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(SwingUtils.TITLE_FONT);
        titleLabel.setForeground(SwingUtils.PRIMARY_COLOR);
        topBar.add(titleLabel, BorderLayout.WEST);

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filterPanel.setOpaque(false);

        if (currentUser.getRole() != Role.STUDENT) {
            filterPanel.add(new JLabel("Course:"));
            courseFilterCombo = new JComboBox<>();
            courseFilterCombo.addItem(new CourseItem(0, "All Courses"));
            courseFilterCombo.addActionListener(e -> loadData());
            filterPanel.add(courseFilterCombo);
        }

        filterPanel.add(new JLabel("Year:"));
        academicYearField = new JTextField("2026-2027", 8);
        academicYearField.addActionListener(e -> loadData());
        filterPanel.add(academicYearField);

        refreshButton = SwingUtils.createSecondaryButton("Refresh");
        refreshButton.addActionListener(e -> {
            loadCourses();
            loadData();
        });
        filterPanel.add(refreshButton);
        topBar.add(filterPanel, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);

        // Center Table
        String[] columns = {"ID", "Course", "Exam Name", "Date", "Type", "Max Marks", "Year"};
        tableModel = new DefaultTableModel(columns, 0);
        examTable = SwingUtils.createStandardTable(tableModel);

        if (currentUser.getRole() != Role.STUDENT) {
            examTable.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting() && examTable.getSelectedRow() != -1) {
                    populateFormFromSelection();
                }
            });
        }

        JScrollPane scrollPane = new JScrollPane(examTable);
        add(scrollPane, BorderLayout.CENTER);

        // South Form (Admin and Faculty)
        if (currentUser.getRole() != Role.STUDENT) {
            JPanel formCard = SwingUtils.createCardPanel("Create or Update Examination");
            JPanel formGrid = new JPanel(new GridLayout(2, 4, 8, 8));
            formGrid.setOpaque(false);

            courseFormCombo = new JComboBox<>();
            nameField = new JTextField();
            examDateField = new JTextField("2026-11-15");
            typeCombo = new JComboBox<>(new String[]{"MIDTERM", "FINAL", "QUIZ", "ASSIGNMENT"});
            maxMarksSpinner = new JSpinner(new SpinnerNumberModel(100, 10, 500, 5));
            weightageSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 100, 5));
            examYearField = new JTextField("2026-2027");

            formGrid.add(createFieldBox("Course:", courseFormCombo));
            formGrid.add(createFieldBox("Exam Name (e.g. Midterm):", nameField));
            formGrid.add(createFieldBox("Exam Date (YYYY-MM-DD):", examDateField));
            formGrid.add(createFieldBox("Exam Type:", typeCombo));
            formGrid.add(createFieldBox("Max Marks:", maxMarksSpinner));
            formGrid.add(createFieldBox("Weightage (%):", weightageSpinner));
            formGrid.add(createFieldBox("Academic Year:", examYearField));
            formGrid.add(new JLabel("")); // spacer

            JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
            buttonRow.setOpaque(false);

            createButton = SwingUtils.createPrimaryButton("Create Exam");
            createButton.addActionListener(e -> createExamination());

            updateButton = SwingUtils.createSecondaryButton("Update Exam");
            updateButton.setEnabled(false);
            updateButton.addActionListener(e -> updateExamination());

            deleteButton = SwingUtils.createSecondaryButton("Delete Exam");
            deleteButton.setEnabled(false);
            deleteButton.addActionListener(e -> deleteExamination());

            JButton clearButton = SwingUtils.createSecondaryButton("Clear Form");
            clearButton.addActionListener(e -> clearForm());

            buttonRow.add(clearButton);
            buttonRow.add(deleteButton);
            buttonRow.add(updateButton);
            buttonRow.add(createButton);

            formCard.add(formGrid, BorderLayout.CENTER);
            formCard.add(buttonRow, BorderLayout.SOUTH);
            add(formCard, BorderLayout.SOUTH);
        }

        loadCourses();
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

    private void loadCourses() {
        String year = (academicYearField != null) ? academicYearField.getText().trim() : "2026-2027";
        SwingUtils.executeAsync(
                this,
                () -> {
                    if (currentUser.getRole() == Role.FACULTY) {
                        Faculty f = facultyDAO.findByUserId(currentUser.getId());
                        if (f == null) return List.<Course>of();
                        List<CourseAssignment> assignments = assignmentService.getActiveAssignmentsByFacultyAndYear(currentUser, f.getId(), year);
                        List<Course> list = new ArrayList<>();
                        for (CourseAssignment a : assignments) {
                            Course c = courseService.getCourseById(currentUser, a.getCourseId());
                            if (c != null && c.isActive()) list.add(c);
                        }
                        return list;
                    } else {
                        return courseService.getAllActiveCourses(currentUser);
                    }
                },
                courses -> {
                    courseNameMap.clear();
                    if (courseFormCombo != null) courseFormCombo.removeAllItems();

                    for (Course c : courses) {
                        String desc = c.getCourseCode() + " - " + c.getName();
                        courseNameMap.put(c.getId(), desc);
                        if (courseFormCombo != null) courseFormCombo.addItem(new CourseItem(c.getId(), desc));
                    }

                    if (courseFilterCombo != null) {
                        CourseItem selected = (CourseItem) courseFilterCombo.getSelectedItem();
                        int selId = selected != null ? selected.id : 0;
                        courseFilterCombo.removeAllItems();
                        courseFilterCombo.addItem(new CourseItem(0, "All Courses"));
                        for (Course c : courses) {
                            courseFilterCombo.addItem(new CourseItem(c.getId(), c.getCourseCode() + " - " + c.getName()));
                        }
                        for (int i = 0; i < courseFilterCombo.getItemCount(); i++) {
                            if (courseFilterCombo.getItemAt(i).id == selId) {
                                courseFilterCombo.setSelectedIndex(i);
                                break;
                            }
                        }
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
                    if (currentUser.getRole() == Role.STUDENT) {
                        Student s = studentDAO.findByUserId(currentUser.getId());
                        if (s == null) return List.<Examination>of();
                        List<Enrollment> enrollments = enrollmentService.getEnrollmentsByStudent(currentUser, s.getId());
                        List<Examination> list = new ArrayList<>();
                        for (Enrollment enr : enrollments) {
                            list.addAll(examinationService.getExaminationsByCourseAndYear(currentUser, enr.getCourseId(), year));
                        }
                        return list;
                    } else {
                        CourseItem filter = (courseFilterCombo != null) ? (CourseItem) courseFilterCombo.getSelectedItem() : null;
                        int filterCourseId = (filter != null) ? filter.id : 0;
                        if (filterCourseId > 0) {
                            return examinationService.getExaminationsByCourseAndYear(currentUser, filterCourseId, year);
                        } else {
                            return examinationService.getAllActiveExaminations(currentUser);
                        }
                    }
                },
                exams -> {
                    tableModel.setRowCount(0);
                    examCache.clear();
                    for (Examination ex : exams) {
                        examCache.put(ex.getId(), ex);
                        String cName = courseNameMap.getOrDefault(ex.getCourseId(), "Course #" + ex.getCourseId());
                        tableModel.addRow(new Object[]{
                                ex.getId(),
                                cName,
                                ex.getExamName(),
                                ex.getExamDate(),
                                ex.getExamType(),
                                ex.getTotalMarks(),
                                ex.getAcademicYear()
                        });
                    }
                    clearForm();
                    refreshButton.setEnabled(true);
                },
                error -> refreshButton.setEnabled(true)
        );
    }

    private void populateFormFromSelection() {
        int row = examTable.getSelectedRow();
        if (row == -1) return;

        selectedExamId = (Integer) tableModel.getValueAt(row, 0);
        Examination ex = examCache.get(selectedExamId);
        if (ex != null) {
            populateFormFromExam(ex);
        }
    }

    public void populateFormFromExam(Examination ex) {
        if (ex == null) return;
        selectedExamId = ex.getId();

        if (courseFormCombo != null) {
            for (int i = 0; i < courseFormCombo.getItemCount(); i++) {
                if (courseFormCombo.getItemAt(i).id == ex.getCourseId()) {
                    courseFormCombo.setSelectedIndex(i);
                    break;
                }
            }
        }

        if (nameField != null) {
            nameField.setText(ex.getExamName() != null ? ex.getExamName() : "");
        }
        if (examDateField != null) {
            examDateField.setText(ex.getExamDate() != null ? ex.getExamDate() : "");
        }
        if (typeCombo != null && ex.getExamType() != null) {
            typeCombo.setSelectedItem(ex.getExamType().toUpperCase());
        }
        if (maxMarksSpinner != null) {
            maxMarksSpinner.setValue(ex.getTotalMarks() > 0 ? ex.getTotalMarks() : 100);
        }
        if (weightageSpinner != null) {
            weightageSpinner.setValue(100);
        }
        if (examYearField != null) {
            examYearField.setText(ex.getAcademicYear() != null ? ex.getAcademicYear() : "");
        }

        boolean isActive = ex.isActive();
        if (updateButton != null) updateButton.setEnabled(true);
        if (deleteButton != null) deleteButton.setEnabled(isActive);
    }

    private void createExamination() {
        Examination ex = buildExamFromForm();
        if (ex == null) return;

        createButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> examinationService.createExamination(currentUser, ex),
                created -> {
                    createButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Examination created successfully.");
                    loadData();
                },
                error -> createButton.setEnabled(true)
        );
    }

    private void updateExamination() {
        if (selectedExamId == null) return;
        Examination ex = buildExamFromForm();
        if (ex == null) return;
        ex.setId(selectedExamId);
        Examination cached = examCache.get(selectedExamId);
        ex.setActive(cached != null ? cached.isActive() : true);

        updateButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> examinationService.updateExamination(currentUser, ex),
                updated -> {
                    updateButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Examination updated successfully.");
                    loadData();
                },
                error -> updateButton.setEnabled(true)
        );
    }

    private void deleteExamination() {
        if (selectedExamId == null) return;
        boolean confirm = SwingUtils.showConfirm(this,
                "Are you sure you want to delete examination ID " + selectedExamId + "?",
                "Confirm Deletion");
        if (!confirm) return;

        deleteButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> {
                    examinationService.deleteExamination(currentUser, selectedExamId);
                    return null;
                },
                unused -> {
                    deleteButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Examination deleted.");
                    loadData();
                },
                error -> deleteButton.setEnabled(true)
        );
    }

    private Examination buildExamFromForm() {
        CourseItem course = (CourseItem) courseFormCombo.getSelectedItem();
        String name = nameField.getText().trim();
        String date = examDateField.getText().trim();
        String type = (String) typeCombo.getSelectedItem();
        int max = (Integer) maxMarksSpinner.getValue();
        int weight = weightageSpinner != null ? (Integer) weightageSpinner.getValue() : 100;
        String year = (examYearField != null) ? examYearField.getText().trim() : academicYearField.getText().trim();

        if (course == null || name.isEmpty() || date.isEmpty() || year.isEmpty()) {
            SwingUtils.showWarning(this, "Please fill in all exam fields.");
            return null;
        }

        Examination ex = new Examination();
        ex.setCourseId(course.id);
        ex.setExamName(name);
        ex.setExamDate(date);
        ex.setExamType(type);
        ex.setTotalMarks(max);
        ex.setAcademicYear(year);
        return ex;
    }

    private void clearForm() {
        selectedExamId = null;
        if (nameField != null) nameField.setText("");
        if (examDateField != null) examDateField.setText("2026-11-15");
        if (maxMarksSpinner != null) maxMarksSpinner.setValue(100);
        if (weightageSpinner != null) weightageSpinner.setValue(100);
        if (examYearField != null) examYearField.setText("2026-2027");
        if (updateButton != null) updateButton.setEnabled(false);
        if (deleteButton != null) deleteButton.setEnabled(false);
        if (examTable != null) examTable.clearSelection();
    }

    public Integer getSelectedExamId() { return selectedExamId; }
    public Integer getSelectedCourseId() {
        CourseItem item = courseFormCombo != null ? (CourseItem) courseFormCombo.getSelectedItem() : null;
        return item != null ? item.id : null;
    }
    public String getExamNameText() { return nameField != null ? nameField.getText() : null; }
    public String getExamDateText() { return examDateField != null ? examDateField.getText() : null; }
    public String getExamTypeText() { return typeCombo != null ? (String) typeCombo.getSelectedItem() : null; }
    public Integer getMaxMarksValue() { return maxMarksSpinner != null ? (Integer) maxMarksSpinner.getValue() : null; }
    public Integer getWeightageValue() { return weightageSpinner != null ? (Integer) weightageSpinner.getValue() : null; }
    public String getAcademicYearText() { return examYearField != null ? examYearField.getText() : null; }
    public void setExamNameText(String name) { if (nameField != null) nameField.setText(name); }
    public void setExamDateText(String date) { if (examDateField != null) examDateField.setText(date); }
    public void setExamTypeText(String type) { if (typeCombo != null) typeCombo.setSelectedItem(type); }
    public void setMaxMarksValue(int marks) { if (maxMarksSpinner != null) maxMarksSpinner.setValue(marks); }
    public void setAcademicYearText(String year) { if (examYearField != null) examYearField.setText(year); }
    public JTable getExamTable() { return examTable; }
    public JButton getUpdateButton() { return updateButton; }
    public JButton getCreateButton() { return createButton; }

    public static class CourseItem {
        public final int id;
        public final String name;
        public CourseItem(int id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }
}
