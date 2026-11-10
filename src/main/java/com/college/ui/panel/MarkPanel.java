package com.college.ui.panel;

import com.college.dao.FacultyDAO;
import com.college.dao.StudentDAO;
import com.college.model.Course;
import com.college.model.CourseAssignment;
import com.college.model.Enrollment;
import com.college.model.Examination;
import com.college.model.Faculty;
import com.college.model.Mark;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.User;
import com.college.service.CourseAssignmentService;
import com.college.service.CourseService;
import com.college.service.EnrollmentService;
import com.college.service.ExaminationService;
import com.college.service.MarkService;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Academic marks recording and grade reporting panel.
 */
public class MarkPanel extends JPanel {

    private final User currentUser;
    private final MarkService markService;
    private final ExaminationService examinationService;
    private final EnrollmentService enrollmentService;
    private final CourseService courseService;
    private final CourseAssignmentService assignmentService;
    private final StudentDAO studentDAO;
    private final FacultyDAO facultyDAO;

    private JTable markTable;
    private DefaultTableModel tableModel;
    private JComboBox<ExamItem> examFilterCombo;
    private JComboBox<ExamItem> examFormCombo;
    private JComboBox<EnrollmentItem> enrollmentFormCombo;
    private JTextField marksObtainedField;
    private JLabel gradeLabel;
    private JTextField remarksField;
    private JButton saveButton;
    private JButton refreshButton;

    private final Map<Integer, Examination> examMap = new HashMap<>();

    public MarkPanel(User currentUser) {
        this(currentUser, new MarkService(), new ExaminationService(), new EnrollmentService(),
                new CourseService(), new CourseAssignmentService(), new StudentDAO(), new FacultyDAO());
    }

    public MarkPanel(User currentUser, MarkService markService, ExaminationService examinationService,
                     EnrollmentService enrollmentService, CourseService courseService,
                     CourseAssignmentService assignmentService, StudentDAO studentDAO, FacultyDAO facultyDAO) {
        this.currentUser = currentUser;
        this.markService = markService;
        this.examinationService = examinationService;
        this.enrollmentService = enrollmentService;
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

        String title = "Academic Marks & Grades";
        if (currentUser.getRole() == Role.STUDENT) title = "My Examination Marks & Grade Report";

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(SwingUtils.TITLE_FONT);
        titleLabel.setForeground(SwingUtils.PRIMARY_COLOR);
        topBar.add(titleLabel, BorderLayout.WEST);

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filterPanel.setOpaque(false);

        if (currentUser.getRole() != Role.STUDENT) {
            filterPanel.add(new JLabel("Filter by Exam:"));
            examFilterCombo = new JComboBox<>();
            examFilterCombo.addActionListener(e -> loadData());
            filterPanel.add(examFilterCombo);
        }

        refreshButton = SwingUtils.createSecondaryButton("Refresh");
        refreshButton.addActionListener(e -> {
            loadExams();
            loadData();
        });
        filterPanel.add(refreshButton);
        topBar.add(filterPanel, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);

        // Center Table
        String[] columns = {"Mark ID", "Exam", "Enrollment / Student", "Marks", "Max Marks", "Grade", "Remarks"};
        tableModel = new DefaultTableModel(columns, 0);
        markTable = SwingUtils.createStandardTable(tableModel);

        JScrollPane scrollPane = new JScrollPane(markTable);
        add(scrollPane, BorderLayout.CENTER);

        // South Form (Admin and Faculty)
        if (currentUser.getRole() != Role.STUDENT) {
            JPanel formCard = SwingUtils.createCardPanel("Enter or Update Examination Marks");
            JPanel formGrid = new JPanel(new GridLayout(2, 4, 8, 8));
            formGrid.setOpaque(false);

            examFormCombo = new JComboBox<>();
            examFormCombo.addActionListener(e -> loadEnrollmentsForExam());

            enrollmentFormCombo = new JComboBox<>();
            marksObtainedField = new JTextField();
            remarksField = new JTextField();
            gradeLabel = new JLabel("Grade: —");
            gradeLabel.setFont(SwingUtils.BOLD_FONT);

            marksObtainedField.addKeyListener(new java.awt.event.KeyAdapter() {
                @Override
                public void keyReleased(java.awt.event.KeyEvent e) {
                    updateGradePreview();
                }
            });

            formGrid.add(createFieldBox("Examination:", examFormCombo));
            formGrid.add(createFieldBox("Student Enrollment:", enrollmentFormCombo));
            formGrid.add(createFieldBox("Marks Obtained (e.g. 85.5):", marksObtainedField));
            formGrid.add(createFieldBox("Remarks:", remarksField));

            JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            infoPanel.setOpaque(false);
            infoPanel.add(gradeLabel);
            formGrid.add(infoPanel);
            formGrid.add(new JLabel("")); // spacer
            formGrid.add(new JLabel("")); // spacer
            formGrid.add(new JLabel("")); // spacer

            JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
            buttonRow.setOpaque(false);

            saveButton = SwingUtils.createPrimaryButton("Save Mark");
            saveButton.addActionListener(e -> saveMark());

            JButton clearButton = SwingUtils.createSecondaryButton("Clear");
            clearButton.addActionListener(e -> clearForm());

            buttonRow.add(clearButton);
            buttonRow.add(saveButton);

            formCard.add(formGrid, BorderLayout.CENTER);
            formCard.add(buttonRow, BorderLayout.SOUTH);
            add(formCard, BorderLayout.SOUTH);
        }

        loadExams();
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

    private void loadExams() {
        SwingUtils.executeAsync(
                this,
                () -> {
                    if (currentUser.getRole() == Role.FACULTY) {
                        Faculty f = facultyDAO.findByUserId(currentUser.getId());
                        if (f == null) return List.<Examination>of();
                        List<CourseAssignment> assignments = assignmentService.getAssignmentsByFaculty(currentUser, f.getId());
                        List<Examination> list = new ArrayList<>();
                        for (CourseAssignment a : assignments) {
                            if (a.isActive()) {
                                list.addAll(examinationService.getExaminationsByCourse(currentUser, a.getCourseId()));
                            }
                        }
                        return list;
                    } else if (currentUser.getRole() == Role.ADMIN) {
                        return examinationService.getAllActiveExaminations(currentUser);
                    } else {
                        return List.<Examination>of();
                    }
                },
                exams -> {
                    examMap.clear();
                    if (examFormCombo != null) examFormCombo.removeAllItems();
                    if (examFilterCombo != null) {
                        examFilterCombo.removeAllItems();
                        examFilterCombo.addItem(new ExamItem(0, "All Examinations", 100));
                    }

                    for (Examination ex : exams) {
                        examMap.put(ex.getId(), ex);
                        String desc = ex.getExamName() + " (Course #" + ex.getCourseId() + " — Max: " + ex.getTotalMarks() + ")";
                        if (examFormCombo != null) examFormCombo.addItem(new ExamItem(ex.getId(), desc, ex.getTotalMarks()));
                        if (examFilterCombo != null) examFilterCombo.addItem(new ExamItem(ex.getId(), desc, ex.getTotalMarks()));
                    }

                    loadEnrollmentsForExam();
                }
        );
    }

    private void loadEnrollmentsForExam() {
        if (examFormCombo == null) return;
        ExamItem selectedExam = (ExamItem) examFormCombo.getSelectedItem();
        if (selectedExam == null || selectedExam.id <= 0) {
            if (enrollmentFormCombo != null) enrollmentFormCombo.removeAllItems();
            return;
        }

        Examination ex = examMap.get(selectedExam.id);
        if (ex == null) return;

        SwingUtils.executeAsync(
                this,
                () -> {
                    List<Enrollment> enrollments = enrollmentService.getEnrollmentsByCourseAndYear(currentUser, ex.getCourseId(), ex.getAcademicYear());
                    List<EnrollmentItem> items = new ArrayList<>();
                    for (Enrollment enr : enrollments) {
                        if ("ACTIVE".equalsIgnoreCase(enr.getStatus())) {
                            Student s = studentDAO.findById(enr.getStudentId());
                            String sDisplay = (s != null)
                                    ? s.getFullName() + " (" + s.getStudentId() + ")"
                                    : "Student #" + enr.getStudentId();
                            items.add(new EnrollmentItem(enr.getId(), sDisplay + " [Enr #" + enr.getId() + "]"));
                        }
                    }
                    return items;
                },
                items -> {
                    enrollmentFormCombo.removeAllItems();
                    for (EnrollmentItem item : items) {
                        enrollmentFormCombo.addItem(item);
                    }
                }
        );
    }

    private void updateGradePreview() {
        try {
            double marks = Double.parseDouble(marksObtainedField.getText().trim());
            ExamItem ex = (ExamItem) examFormCombo.getSelectedItem();
            int max = (ex != null) ? ex.maxMarks : 100;
            String grade = MarkService.calculateGrade(marks, max);
            gradeLabel.setText("Grade: " + grade);
            gradeLabel.setForeground(SwingUtils.PRIMARY_COLOR);
        } catch (Exception ex) {
            gradeLabel.setText("Grade: —");
        }
    }

    public void loadData() {
        refreshButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> {
                    if (currentUser.getRole() == Role.STUDENT) {
                        Student s = studentDAO.findByUserId(currentUser.getId());
                        if (s == null) return List.<Object[]>of();
                        List<Enrollment> enrollments = enrollmentService.getEnrollmentsByStudent(currentUser, s.getId());
                        List<Object[]> rows = new ArrayList<>();
                        for (Enrollment enr : enrollments) {
                            List<Mark> marks = markService.getMarksByEnrollment(currentUser, enr.getId());
                            for (Mark m : marks) {
                                Examination ex = examinationService.getExaminationById(currentUser, m.getExaminationId());
                                String exName = (ex != null) ? ex.getExamName() : "Exam #" + m.getExaminationId();
                                int max = (ex != null) ? ex.getTotalMarks() : 100;
                                rows.add(new Object[]{
                                        m.getId(),
                                        exName,
                                        "My Enrollment #" + enr.getId(),
                                        m.getMarksObtained(),
                                        max,
                                        m.getGrade(),
                                        m.getRemarks() != null ? m.getRemarks() : ""
                                });
                            }
                        }
                        return rows;
                    } else {
                        ExamItem filter = (examFilterCombo != null) ? (ExamItem) examFilterCombo.getSelectedItem() : null;
                        int filterExamId = (filter != null) ? filter.id : 0;
                        List<Object[]> rows = new ArrayList<>();

                        if (filterExamId > 0) {
                            Examination ex = examMap.get(filterExamId);
                            int max = (ex != null) ? ex.getTotalMarks() : 100;
                            List<Mark> marks = markService.getMarksByExamination(currentUser, filterExamId);
                            for (Mark m : marks) {
                                rows.add(new Object[]{
                                        m.getId(),
                                        filter.name,
                                        "Enrollment #" + m.getEnrollmentId(),
                                        m.getMarksObtained(),
                                        max,
                                        m.getGrade(),
                                        m.getRemarks() != null ? m.getRemarks() : ""
                                });
                            }
                        } else {
                            // Gather across active exams
                            for (Examination ex : examMap.values()) {
                                List<Mark> marks = markService.getMarksByExamination(currentUser, ex.getId());
                                for (Mark m : marks) {
                                    rows.add(new Object[]{
                                            m.getId(),
                                            ex.getExamName(),
                                            "Enrollment #" + m.getEnrollmentId(),
                                            m.getMarksObtained(),
                                            ex.getTotalMarks(),
                                            m.getGrade(),
                                            m.getRemarks() != null ? m.getRemarks() : ""
                                    });
                                }
                            }
                        }
                        return rows;
                    }
                },
                rows -> {
                    tableModel.setRowCount(0);
                    for (Object[] r : rows) {
                        tableModel.addRow(r);
                    }
                    refreshButton.setEnabled(true);
                },
                error -> refreshButton.setEnabled(true)
        );
    }

    private void saveMark() {
        ExamItem exam = (ExamItem) examFormCombo.getSelectedItem();
        EnrollmentItem enr = (EnrollmentItem) enrollmentFormCombo.getSelectedItem();
        String marksText = marksObtainedField.getText().trim();
        String remarks = remarksField.getText().trim();

        if (exam == null || enr == null || marksText.isEmpty()) {
            SwingUtils.showWarning(this, "Please select an examination, enrollment, and enter marks.");
            return;
        }

        double marks;
        try {
            marks = Double.parseDouble(marksText);
        } catch (NumberFormatException ex) {
            SwingUtils.showWarning(this, "Marks must be a valid number (e.g. 85.5).");
            return;
        }

        saveButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> markService.saveMark(currentUser, enr.id, exam.id, marks, remarks),
                saved -> {
                    saveButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Mark saved successfully. Grade: " + saved.getGrade());
                    clearForm();
                    loadData();
                },
                error -> saveButton.setEnabled(true)
        );
    }

    private void clearForm() {
        if (marksObtainedField != null) marksObtainedField.setText("");
        if (remarksField != null) remarksField.setText("");
        if (gradeLabel != null) gradeLabel.setText("Grade: —");
    }

    public static class ExamItem {
        public final int id;
        public final String name;
        public final int maxMarks;
        public ExamItem(int id, String name, int maxMarks) { this.id = id; this.name = name; this.maxMarks = maxMarks; }
        @Override public String toString() { return name; }
    }

    public static class EnrollmentItem {
        public final int id;
        public final String name;
        public EnrollmentItem(int id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }
}
