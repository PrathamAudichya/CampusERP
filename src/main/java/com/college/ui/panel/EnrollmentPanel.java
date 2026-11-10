package com.college.ui.panel;

import com.college.dao.FacultyDAO;
import com.college.dao.StudentDAO;
import com.college.model.Course;
import com.college.model.CourseAssignment;
import com.college.model.Enrollment;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.User;
import com.college.service.CourseAssignmentService;
import com.college.service.CourseService;
import com.college.service.EnrollmentService;
import com.college.service.EnrollmentService.CourseCatalogItem;
import com.college.service.StudentService;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Course enrollment management panel supporting Admin mutations, Faculty roster viewing,
 * and Student enrolled course viewing.
 */
public class EnrollmentPanel extends JPanel {

    private final User currentUser;
    private final EnrollmentService enrollmentService;
    private final StudentService studentService;
    private final CourseService courseService;
    private final CourseAssignmentService assignmentService;
    private final StudentDAO studentDAO;
    private final FacultyDAO facultyDAO;

    private JTable enrollmentTable;
    private DefaultTableModel tableModel;
    private JTable catalogueTable;
    private DefaultTableModel catalogueModel;
    private JComboBox<CourseItem> courseFilterCombo;
    private JComboBox<CourseItem> courseFormCombo;
    private JComboBox<StudentItem> studentFormCombo;
    private JLabel duplicateValidationLabel;
    private JTextField academicYearField;

    private JButton enrollButton;
    private JButton dropButton;
    private JButton reactivateButton;
    private JButton refreshButton;
    private JButton registerButton;

    private Integer selectedEnrollmentId = null;
    private final Map<Integer, String> courseNameMap = new HashMap<>();
    private final Map<Integer, String> studentNameMap = new HashMap<>();

    public EnrollmentPanel(User currentUser) {
        this(currentUser, new EnrollmentService(), new StudentService(), new CourseService(),
                new CourseAssignmentService(), new StudentDAO(), new FacultyDAO());
    }

    public EnrollmentPanel(User currentUser, EnrollmentService enrollmentService, StudentService studentService,
                           CourseService courseService, CourseAssignmentService assignmentService,
                           StudentDAO studentDAO, FacultyDAO facultyDAO) {
        this.currentUser = currentUser;
        this.enrollmentService = enrollmentService;
        this.studentService = studentService;
        this.courseService = courseService;
        this.assignmentService = assignmentService;
        this.studentDAO = studentDAO;
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

        String title = "Course Enrollments";
        if (currentUser.getRole() == Role.FACULTY) title = "Course Rosters (Enrolled Students)";
        else if (currentUser.getRole() == Role.STUDENT) title = "My Course Enrollments";

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
            loadCatalogs();
            loadData();
        });
        filterPanel.add(refreshButton);
        topBar.add(filterPanel, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);

        // Center Table
        String[] columns = {"Enrollment ID", "Student Name & ID", "Course", "Academic Year", "Enrollment Date", "Status"};
        tableModel = new DefaultTableModel(columns, 0);
        enrollmentTable = SwingUtils.createStandardTable(tableModel);

        if (currentUser.getRole() == Role.ADMIN) {
            enrollmentTable.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting() && enrollmentTable.getSelectedRow() != -1) {
                    int row = enrollmentTable.getSelectedRow();
                    selectedEnrollmentId = (Integer) tableModel.getValueAt(row, 0);
                    String status = (String) tableModel.getValueAt(row, 5);
                    dropButton.setEnabled("ACTIVE".equalsIgnoreCase(status));
                    reactivateButton.setEnabled("DROPPED".equalsIgnoreCase(status));
                }
            });
        }

        if (currentUser.getRole() == Role.STUDENT) {
            JPanel topCard = SwingUtils.createCardPanel("My Registered Courses");
            topCard.add(new JScrollPane(enrollmentTable), BorderLayout.CENTER);

            JPanel botCard = SwingUtils.createCardPanel("Available Course Catalogue & Self-Registration");
            String[] catCols = {"ID", "Course Code", "Course Name", "Department", "Semester", "Credits", "Capacity", "Available Seats", "Status"};
            catalogueModel = new DefaultTableModel(catCols, 0) {
                @Override
                public boolean isCellEditable(int row, int col) {
                    return false;
                }
            };
            catalogueTable = SwingUtils.createStandardTable(catalogueModel);
            catalogueTable.getColumnModel().getColumn(0).setMinWidth(0);
            catalogueTable.getColumnModel().getColumn(0).setMaxWidth(0);
            catalogueTable.getColumnModel().getColumn(0).setWidth(0);

            catalogueTable.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    int r = catalogueTable.getSelectedRow();
                    if (r != -1) {
                        String st = (String) catalogueModel.getValueAt(r, 8);
                        registerButton.setEnabled("Available".equalsIgnoreCase(st));
                    } else {
                        registerButton.setEnabled(false);
                    }
                }
            });

            botCard.add(new JScrollPane(catalogueTable), BorderLayout.CENTER);

            JPanel botButtonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
            botButtonRow.setOpaque(false);
            registerButton = SwingUtils.createPrimaryButton("Register for Selected Course");
            registerButton.setEnabled(false);
            registerButton.addActionListener(e -> registerSelectedCourse());
            botButtonRow.add(registerButton);
            botCard.add(botButtonRow, BorderLayout.SOUTH);

            JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topCard, botCard);
            splitPane.setResizeWeight(0.45);
            splitPane.setContinuousLayout(true);
            splitPane.setDividerSize(6);
            add(splitPane, BorderLayout.CENTER);
        } else {
            JScrollPane scrollPane = new JScrollPane(enrollmentTable);
            add(scrollPane, BorderLayout.CENTER);
        }

        // South Form (Admin Only Mutation)
        if (currentUser.getRole() == Role.ADMIN) {
            JPanel formCard = SwingUtils.createCardPanel("Enroll Student in Course");
            JPanel formGrid = new JPanel(new GridLayout(1, 3, 8, 8));
            formGrid.setOpaque(false);

            studentFormCombo = new JComboBox<>();
            studentFormCombo.addActionListener(e -> checkDuplicateEnrollment());

            courseFormCombo = new JComboBox<>();
            courseFormCombo.addActionListener(e -> checkDuplicateEnrollment());

            duplicateValidationLabel = new JLabel("Status: Ready to enroll");
            duplicateValidationLabel.setFont(SwingUtils.SMALL_FONT);
            duplicateValidationLabel.setForeground(SwingUtils.SUCCESS_COLOR);

            formGrid.add(createFieldBox("Student:", studentFormCombo));
            formGrid.add(createFieldBox("Course:", courseFormCombo));
            formGrid.add(createFieldBox("Validation Status:", duplicateValidationLabel));

            JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
            buttonRow.setOpaque(false);

            enrollButton = SwingUtils.createPrimaryButton("Enroll Student");
            enrollButton.addActionListener(e -> enrollStudent());

            dropButton = SwingUtils.createSecondaryButton("Drop Enrollment");
            dropButton.setEnabled(false);
            dropButton.addActionListener(e -> dropEnrollment());

            reactivateButton = SwingUtils.createSecondaryButton("Reactivate Dropped");
            reactivateButton.setEnabled(false);
            reactivateButton.addActionListener(e -> reactivateEnrollment());

            buttonRow.add(reactivateButton);
            buttonRow.add(dropButton);
            buttonRow.add(enrollButton);

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
                    List<Course> courses;
                    String currentYear = (academicYearField != null) ? academicYearField.getText().trim() : "2026-2027";
                    if (currentUser.getRole() == Role.FACULTY) {
                        Faculty f = facultyDAO.findByUserId(currentUser.getId());
                        if (f != null && f.isActive()) {
                            List<CourseAssignment> assignments = assignmentService.getActiveAssignmentsByFacultyAndYear(currentUser, f.getId(), currentYear);
                            courses = new ArrayList<>();
                            for (CourseAssignment ca : assignments) {
                                Course c = courseService.getCourseById(currentUser, ca.getCourseId());
                                if (c != null && c.isActive()) courses.add(c);
                            }
                        } else {
                            courses = List.of();
                        }
                    } else {
                        courses = courseService.getAllActiveCourses(currentUser);
                    }
                    List<Student> students = (currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.FACULTY)
                            ? studentService.getAllActiveStudents(currentUser) : List.of();
                    return new Object[]{courses, students};
                },
                result -> {
                    @SuppressWarnings("unchecked")
                    List<Course> courses = (List<Course>) result[0];
                    @SuppressWarnings("unchecked")
                    List<Student> students = (List<Student>) result[1];

                    courseNameMap.clear();
                    studentNameMap.clear();

                    if (courseFormCombo != null) courseFormCombo.removeAllItems();
                    if (studentFormCombo != null) studentFormCombo.removeAllItems();

                    for (Course c : courses) {
                        String desc = c.getCourseCode() + " - " + c.getName();
                        courseNameMap.put(c.getId(), desc);
                        if (courseFormCombo != null) courseFormCombo.addItem(new CourseItem(c.getId(), desc));
                    }

                    for (Student s : students) {
                        String desc = s.getFirstName() + " " + s.getLastName() + " | " + s.getStudentId();
                        studentNameMap.put(s.getId(), desc);
                        if (studentFormCombo != null) studentFormCombo.addItem(new StudentItem(s.getId(), desc));
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
                    checkDuplicateEnrollment();
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
                        if (s == null) {
                            return new Object[]{List.<Enrollment>of(), List.<CourseCatalogItem>of(), null};
                        }
                        List<Enrollment> enrollments = enrollmentService.getEnrollmentsByStudent(currentUser, s.getId());
                        List<CourseCatalogItem> catalog = enrollmentService.getCourseCatalogForStudent(currentUser, year);
                        return new Object[]{enrollments, catalog, s};
                    } else if (currentUser.getRole() == Role.FACULTY) {
                        CourseItem filter = (courseFilterCombo != null) ? (CourseItem) courseFilterCombo.getSelectedItem() : null;
                        int filterCourseId = (filter != null) ? filter.id : 0;

                        if (filterCourseId > 0) {
                            return enrollmentService.getEnrollmentsByCourseAndYear(currentUser, filterCourseId, year);
                        } else {
                            Faculty f = facultyDAO.findByUserId(currentUser.getId());
                            List<CourseAssignment> assignments = (f != null && f.isActive())
                                    ? assignmentService.getActiveAssignmentsByFacultyAndYear(currentUser, f.getId(), year)
                                    : List.of();
                            List<Enrollment> all = new ArrayList<>();
                            for (CourseAssignment ca : assignments) {
                                all.addAll(enrollmentService.getEnrollmentsByCourseAndYear(currentUser, ca.getCourseId(), year));
                            }
                            return all;
                        }
                    } else {
                        CourseItem filter = (courseFilterCombo != null) ? (CourseItem) courseFilterCombo.getSelectedItem() : null;
                        int filterCourseId = (filter != null) ? filter.id : 0;

                        if (filterCourseId > 0) {
                            return enrollmentService.getEnrollmentsByCourseAndYear(currentUser, filterCourseId, year);
                        } else {
                            List<Course> courses = courseService.getAllActiveCourses(currentUser);
                            List<Enrollment> all = new ArrayList<>();
                            for (Course c : courses) {
                                all.addAll(enrollmentService.getEnrollmentsByCourseAndYear(currentUser, c.getId(), year));
                            }
                            return all;
                        }
                    }
                },
                data -> {
                    tableModel.setRowCount(0);
                    if (currentUser.getRole() == Role.STUDENT) {
                        Object[] parts = (Object[]) data;
                        @SuppressWarnings("unchecked")
                        List<Enrollment> enrollments = (List<Enrollment>) parts[0];
                        @SuppressWarnings("unchecked")
                        List<CourseCatalogItem> catalog = (List<CourseCatalogItem>) parts[1];

                        Student currentStudent = (parts.length > 2) ? (Student) parts[2] : null;
                        String studentIdentity = (currentStudent != null)
                                ? currentStudent.getFirstName() + " " + currentStudent.getLastName() + " | " + currentStudent.getStudentId()
                                : "Enrolled Student";

                        for (Enrollment e : enrollments) {
                            String cName = courseNameMap.getOrDefault(e.getCourseId(), "Course #" + e.getCourseId());
                            tableModel.addRow(new Object[]{
                                    e.getId(),
                                    studentIdentity,
                                    cName,
                                    e.getAcademicYear(),
                                    e.getEnrolledDate(),
                                    e.getStatus()
                            });
                        }

                        if (catalogueModel != null) {
                            catalogueModel.setRowCount(0);
                            for (CourseCatalogItem item : catalog) {
                                String status = item.isAlreadyEnrolled() ? "Already Registered"
                                        : (item.getMaxCapacity() > 0 && item.getAvailableSeats() <= 0) ? "Full" : "Available";
                                catalogueModel.addRow(new Object[]{
                                        item.getCourseId(),
                                        item.getCourseCode(),
                                        item.getCourseName(),
                                        item.getDepartmentName(),
                                        item.getSemester(),
                                        item.getCredits(),
                                        item.getMaxCapacity() > 0 ? item.getMaxCapacity() : "Open",
                                        item.getMaxCapacity() > 0 ? item.getAvailableSeats() : "Open",
                                        status
                                });
                            }
                        }
                        if (registerButton != null) registerButton.setEnabled(false);
                    } else {
                        @SuppressWarnings("unchecked")
                        List<Enrollment> enrollments = (List<Enrollment>) data;
                        for (Enrollment e : enrollments) {
                            String cName = courseNameMap.getOrDefault(e.getCourseId(), "Course #" + e.getCourseId());
                            String sName = studentNameMap.getOrDefault(e.getStudentId(), "Student #" + e.getStudentId());
                            tableModel.addRow(new Object[]{
                                    e.getId(),
                                    sName,
                                    cName,
                                    e.getAcademicYear(),
                                    e.getEnrolledDate(),
                                    e.getStatus()
                            });
                        }
                        if (dropButton != null) dropButton.setEnabled(false);
                        if (reactivateButton != null) reactivateButton.setEnabled(false);
                    }
                    refreshButton.setEnabled(true);
                },
                error -> refreshButton.setEnabled(true)
        );
    }

    private void registerSelectedCourse() {
        if (catalogueTable == null || registerButton == null) return;
        int row = catalogueTable.getSelectedRow();
        if (row == -1) return;
        int courseId = (Integer) catalogueModel.getValueAt(row, 0);
        String code = (String) catalogueModel.getValueAt(row, 1);
        String name = (String) catalogueModel.getValueAt(row, 2);
        String year = academicYearField.getText().trim();

        boolean confirm = SwingUtils.showConfirm(this,
                "Are you sure you want to register for " + code + " - " + name + " (" + year + ")?",
                "Confirm Course Registration");
        if (!confirm) return;

        registerButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> enrollmentService.selfEnroll(currentUser, courseId, year),
                result -> {
                    registerButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Successfully registered for " + code + " - " + name + "!");
                    loadData();
                },
                error -> {
                    registerButton.setEnabled(true);
                    SwingUtils.showError(this, error.getMessage());
                }
        );
    }

    private void checkDuplicateEnrollment() {
        if (studentFormCombo == null || courseFormCombo == null || enrollButton == null) return;
        StudentItem s = (StudentItem) studentFormCombo.getSelectedItem();
        CourseItem c = (CourseItem) courseFormCombo.getSelectedItem();
        String year = academicYearField != null ? academicYearField.getText().trim() : "2026-2027";
        if (s == null || c == null) {
            if (duplicateValidationLabel != null) {
                duplicateValidationLabel.setText("Status: Ready to enroll");
                duplicateValidationLabel.setForeground(SwingUtils.MUTED_COLOR);
            }
            return;
        }

        SwingUtils.executeAsync(
                this,
                () -> {
                    List<Enrollment> list = enrollmentService.getEnrollmentsByStudent(currentUser, s.id);
                    for (Enrollment enr : list) {
                        if (enr.getCourseId() == c.id && year.equalsIgnoreCase(enr.getAcademicYear()) && "ACTIVE".equalsIgnoreCase(enr.getStatus())) {
                            return true;
                        }
                    }
                    return false;
                },
                isDuplicate -> {
                    if (Boolean.TRUE.equals(isDuplicate)) {
                        duplicateValidationLabel.setText("Warning: Already enrolled (" + year + ")");
                        duplicateValidationLabel.setForeground(SwingUtils.DANGER_COLOR);
                        enrollButton.setEnabled(false);
                    } else {
                        duplicateValidationLabel.setText("Status: Available to enroll (" + year + ")");
                        duplicateValidationLabel.setForeground(SwingUtils.SUCCESS_COLOR);
                        enrollButton.setEnabled(true);
                    }
                }
        );
    }

    private void enrollStudent() {
        StudentItem student = (StudentItem) studentFormCombo.getSelectedItem();
        CourseItem course = (CourseItem) courseFormCombo.getSelectedItem();
        String year = academicYearField.getText().trim();

        if (student == null || course == null || year.isEmpty()) {
            SwingUtils.showWarning(this, "Please select both a student and a course.");
            return;
        }

        enrollButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> enrollmentService.enroll(currentUser, student.id, course.id, year),
                result -> {
                    enrollButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Student enrolled successfully.");
                    loadData();
                },
                error -> enrollButton.setEnabled(true)
        );
    }

    private void dropEnrollment() {
        if (selectedEnrollmentId == null) return;
        boolean confirm = SwingUtils.showConfirm(this,
                "Are you sure you want to drop enrollment ID " + selectedEnrollmentId + "?",
                "Confirm Drop");
        if (!confirm) return;

        dropButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> {
                    enrollmentService.dropEnrollment(currentUser, selectedEnrollmentId);
                    return null;
                },
                unused -> {
                    dropButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Enrollment dropped.");
                    loadData();
                },
                error -> dropButton.setEnabled(true)
        );
    }

    private void reactivateEnrollment() {
        if (selectedEnrollmentId == null) return;
        reactivateButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> enrollmentService.reactivateEnrollment(currentUser, selectedEnrollmentId),
                result -> {
                    reactivateButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Enrollment reactivated.");
                    loadData();
                },
                error -> reactivateButton.setEnabled(true)
        );
    }

    public static class CourseItem {
        public final int id;
        public final String name;
        public CourseItem(int id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }

    public static class StudentItem {
        public final int id;
        public final String name;
        public StudentItem(int id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }
}
