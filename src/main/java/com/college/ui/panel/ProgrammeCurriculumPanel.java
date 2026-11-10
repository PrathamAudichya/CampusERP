package com.college.ui.panel;

import com.college.model.AcademicProgram;
import com.college.model.Course;
import com.college.model.Department;
import com.college.model.ProgrammeCurriculum;
import com.college.model.Role;
import com.college.model.User;
import com.college.service.AcademicProgramService;
import com.college.service.CourseService;
import com.college.service.DepartmentService;
import com.college.service.ProgrammeCurriculumService;
import com.college.ui.theme.CampusTheme;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Curriculum management panel allowing administrators to select an Academic Programme,
 * semester, and regulation scheme, map courses with categories and prerequisites, and export curricula.
 */
public class ProgrammeCurriculumPanel extends JPanel {

    private final User currentUser;
    private final ProgrammeCurriculumService curriculumService;
    private final AcademicProgramService programService;
    private final CourseService courseService;
    private final DepartmentService departmentService;

    private JComboBox<ProgramItem> programCombo;
    private JComboBox<String> semesterCombo;
    private JComboBox<String> regulationCombo;

    private JTable curriculumTable;
    private DefaultTableModel tableModel;

    private JButton addMappingButton;
    private JButton removeMappingButton;
    private JButton exportButton;
    private JButton refreshButton;

    private Integer selectedMappingId = null;

    public ProgrammeCurriculumPanel(User currentUser) {
        this(currentUser, new ProgrammeCurriculumService(), new AcademicProgramService(), new CourseService(), new DepartmentService());
    }

    public ProgrammeCurriculumPanel(User currentUser, ProgrammeCurriculumService curriculumService,
                                   AcademicProgramService programService, CourseService courseService,
                                   DepartmentService departmentService) {
        this.currentUser = currentUser;
        this.curriculumService = curriculumService;
        this.programService = programService;
        this.courseService = courseService;
        this.departmentService = departmentService;
        initUI();
        loadPrograms();
    }

    private void initUI() {
        setLayout(new BorderLayout(12, 12));
        setBackground(SwingUtils.BG_LIGHT);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Top Filter Bar
        JPanel topContainer = new JPanel(new BorderLayout(8, 8));
        topContainer.setOpaque(false);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JLabel titleLabel = new JLabel("Programme Curriculum & Course Mapping");
        titleLabel.setFont(SwingUtils.TITLE_FONT);
        titleLabel.setForeground(SwingUtils.PRIMARY_COLOR);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        topButtons.setOpaque(false);

        exportButton = SwingUtils.createSecondaryButton("Export Curriculum CSV");
        exportButton.addActionListener(e -> exportCurriculum());
        topButtons.add(exportButton);

        refreshButton = SwingUtils.createSecondaryButton("Refresh");
        refreshButton.addActionListener(e -> loadCurriculum());
        topButtons.add(refreshButton);

        headerPanel.add(topButtons, BorderLayout.EAST);
        topContainer.add(headerPanel, BorderLayout.NORTH);

        // Selector Bar
        JPanel selectorBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        selectorBar.setOpaque(false);

        selectorBar.add(new JLabel("Academic Programme:"));
        programCombo = new JComboBox<>();
        programCombo.setPreferredSize(new Dimension(280, 30));
        programCombo.addActionListener(e -> onProgramSelected());
        selectorBar.add(programCombo);

        selectorBar.add(new JLabel("Semester:"));
        semesterCombo = new JComboBox<>(new String[]{"ALL"});
        semesterCombo.setPreferredSize(new Dimension(100, 30));
        semesterCombo.addActionListener(e -> loadCurriculum());
        selectorBar.add(semesterCombo);

        selectorBar.add(new JLabel("Scheme / Regulation:"));
        regulationCombo = new JComboBox<>(new String[]{"R2026", "R2025", "R2024"});
        regulationCombo.setPreferredSize(new Dimension(100, 30));
        regulationCombo.addActionListener(e -> loadCurriculum());
        selectorBar.add(regulationCombo);

        topContainer.add(selectorBar, BorderLayout.SOUTH);
        add(topContainer, BorderLayout.NORTH);

        // Center Table
        String[] cols = {"ID", "Semester", "Course Code", "Course Title", "Credits", "Category", "Mandatory / Elective", "Prerequisite", "Scheme", "Status"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        curriculumTable = SwingUtils.createStandardTable(tableModel);

        curriculumTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && curriculumTable.getSelectedRow() != -1) {
                int row = curriculumTable.getSelectedRow();
                selectedMappingId = (Integer) tableModel.getValueAt(row, 0);
                if (currentUser.getRole() == Role.ADMIN) {
                    removeMappingButton.setEnabled(true);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(curriculumTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 224, 230)));
        add(scrollPane, BorderLayout.CENTER);

        // Footer Actions
        if (currentUser.getRole() == Role.ADMIN) {
            JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
            footer.setOpaque(false);

            removeMappingButton = SwingUtils.createSecondaryButton("Remove Course Mapping");
            removeMappingButton.setEnabled(false);
            removeMappingButton.addActionListener(e -> removeMapping());

            addMappingButton = SwingUtils.createPrimaryButton("Assign Course to Curriculum");
            addMappingButton.addActionListener(e -> openAddCourseDialog());

            footer.add(removeMappingButton);
            footer.add(addMappingButton);
            add(footer, BorderLayout.SOUTH);
        }
    }

    private void loadPrograms() {
        SwingUtils.executeAsync(
                this,
                () -> programService.getAllActivePrograms(currentUser),
                progs -> {
                    programCombo.removeAllItems();
                    for (AcademicProgram p : progs) {
                        programCombo.addItem(new ProgramItem(p));
                    }
                    onProgramSelected();
                },
                error -> SwingUtils.showError(this, "Failed to load programmes: " + error.getMessage())
        );
    }

    private void onProgramSelected() {
        ProgramItem item = (ProgramItem) programCombo.getSelectedItem();
        if (item == null) return;

        semesterCombo.removeAllItems();
        semesterCombo.addItem("ALL");
        for (int i = 1; i <= item.program.getTotalSemesters(); i++) {
            semesterCombo.addItem(String.valueOf(i));
        }
        loadCurriculum();
    }

    private void loadCurriculum() {
        ProgramItem item = (ProgramItem) programCombo.getSelectedItem();
        if (item == null) return;

        String semSel = (String) semesterCombo.getSelectedItem();

        refreshButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> {
                    if (semSel == null || "ALL".equalsIgnoreCase(semSel)) {
                        return curriculumService.getFullProgramCurriculum(currentUser, item.program.getId());
                    } else {
                        int semNum = Integer.parseInt(semSel);
                        return curriculumService.getCurriculumByProgramAndSemester(currentUser, item.program.getId(), semNum);
                    }
                },
                list -> {
                    tableModel.setRowCount(0);
                    for (ProgrammeCurriculum pc : list) {
                        tableModel.addRow(new Object[]{
                                pc.getId(),
                                "Sem " + pc.getSemesterNumber(),
                                pc.getCourseCode(),
                                pc.getCourseName(),
                                pc.getCredits(),
                                pc.getCourseCategory(),
                                pc.isMandatory() ? "MANDATORY" : "ELECTIVE",
                                pc.getPrerequisiteCode() != null ? pc.getPrerequisiteCode() + " (" + pc.getPrerequisiteName() + ")" : "None",
                                pc.getAcademicRegulation(),
                                pc.isActive() ? "Active" : "Inactive"
                        });
                    }
                    selectedMappingId = null;
                    if (removeMappingButton != null) removeMappingButton.setEnabled(false);
                    refreshButton.setEnabled(true);
                },
                error -> {
                    refreshButton.setEnabled(true);
                    SwingUtils.showError(this, "Failed to load curriculum: " + error.getMessage());
                }
        );
    }

    private void openAddCourseDialog() {
        ProgramItem item = (ProgramItem) programCombo.getSelectedItem();
        if (item == null) return;

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Assign Course to Curriculum", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(480, 420);
        dialog.setLocationRelativeTo(this);

        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(CampusTheme.BG_CARD);
        p.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Course Selector
        gbc.gridx = 0; gbc.gridy = 0;
        p.add(new JLabel("Select Course:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0;
        JComboBox<CourseItem> courseCombo = new JComboBox<>();
        List<Course> courses = courseService.getAllActiveCourses(currentUser);
        for (Course c : courses) courseCombo.addItem(new CourseItem(c));
        p.add(courseCombo, gbc);

        // Semester Selector
        gbc.gridx = 0; gbc.gridy = 1;
        p.add(new JLabel("Semester:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1;
        JComboBox<Integer> semBox = new JComboBox<>();
        for (int i = 1; i <= item.program.getTotalSemesters(); i++) semBox.addItem(i);
        p.add(semBox, gbc);

        // Category Selector
        gbc.gridx = 0; gbc.gridy = 2;
        p.add(new JLabel("Category:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2;
        JComboBox<String> catBox = new JComboBox<>(new String[]{
                "CORE", "ELECTIVE", "OPEN_ELECTIVE", "LABORATORY", "PROJECT", "INTERNSHIP", "SEMINAR", "RESEARCH"
        });
        p.add(catBox, gbc);

        // Mandatory Flag
        gbc.gridx = 0; gbc.gridy = 3;
        p.add(new JLabel("Requirement:"), gbc);
        gbc.gridx = 1; gbc.gridy = 3;
        JCheckBox mandCheck = new JCheckBox("Mandatory Core Course", true);
        mandCheck.setOpaque(false);
        p.add(mandCheck, gbc);

        // Prerequisite Course
        gbc.gridx = 0; gbc.gridy = 4;
        p.add(new JLabel("Prerequisite:"), gbc);
        gbc.gridx = 1; gbc.gridy = 4;
        JComboBox<CourseItem> prereqCombo = new JComboBox<>();
        prereqCombo.addItem(new CourseItem(null)); // None
        for (Course c : courses) prereqCombo.addItem(new CourseItem(c));
        p.add(prereqCombo, gbc);

        // Scheme
        gbc.gridx = 0; gbc.gridy = 5;
        p.add(new JLabel("Regulation Scheme:"), gbc);
        gbc.gridx = 1; gbc.gridy = 5;
        JTextField schemeField = new JTextField((String) regulationCombo.getSelectedItem());
        p.add(schemeField, gbc);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        btnPanel.setOpaque(false);
        JButton cancel = SwingUtils.createSecondaryButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());
        JButton save = SwingUtils.createPrimaryButton("Assign Course");
        save.addActionListener(e -> {
            CourseItem ci = (CourseItem) courseCombo.getSelectedItem();
            if (ci == null || ci.course == null) return;
            CourseItem pi = (CourseItem) prereqCombo.getSelectedItem();

            ProgrammeCurriculum pc = new ProgrammeCurriculum();
            pc.setProgramId(item.program.getId());
            pc.setSemesterNumber((Integer) semBox.getSelectedItem());
            pc.setCourseId(ci.course.getId());
            pc.setCourseCategory((String) catBox.getSelectedItem());
            pc.setMandatory(mandCheck.isSelected());
            pc.setAcademicRegulation(schemeField.getText().trim());
            if (pi != null && pi.course != null) {
                pc.setPrerequisiteCourseId(pi.course.getId());
            }

            SwingUtils.executeAsync(
                    dialog,
                    () -> curriculumService.mapCourseToCurriculum(currentUser, pc),
                    saved -> {
                        dialog.dispose();
                        loadCurriculum();
                        SwingUtils.showInfo(this, "Course mapped to curriculum: " + ci.course.getCourseCode());
                    },
                    err -> SwingUtils.showError(dialog, "Mapping Failed: " + err.getMessage())
            );
        });

        btnPanel.add(cancel);
        btnPanel.add(save);

        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        p.add(btnPanel, gbc);

        dialog.setContentPane(p);
        dialog.setVisible(true);
    }

    private void removeMapping() {
        if (selectedMappingId == null) return;
        boolean confirm = SwingUtils.showConfirm(this,
                "Remove this course from the curriculum?\n" +
                "(Note: Historical student enrollments and marks will not be affected).",
                "Confirm Removal");
        if (!confirm) return;

        removeMappingButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> {
                    curriculumService.removeCurriculumMapping(currentUser, selectedMappingId);
                    return null;
                },
                unused -> {
                    removeMappingButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Curriculum mapping removed.");
                    loadCurriculum();
                },
                error -> {
                    removeMappingButton.setEnabled(true);
                    SwingUtils.showError(this, "Removal Failed: " + error.getMessage());
                }
        );
    }

    private void exportCurriculum() {
        ProgramItem item = (ProgramItem) programCombo.getSelectedItem();
        if (item == null) return;

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File(item.program.getCode() + "_Curriculum.csv"));
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            SwingUtils.executeAsync(
                    this,
                    () -> curriculumService.exportCurriculumCsv(currentUser, item.program.getId()),
                    csv -> {
                        try (FileWriter writer = new FileWriter(file)) {
                            writer.write(csv);
                            SwingUtils.showInfo(this, "Curriculum exported successfully to:\n" + file.getAbsolutePath());
                        } catch (IOException ex) {
                            SwingUtils.showError(this, "Failed to write CSV file: " + ex.getMessage());
                        }
                    },
                    error -> SwingUtils.showError(this, "Export Failed: " + error.getMessage())
            );
        }
    }

    private static class ProgramItem {
        final AcademicProgram program;
        ProgramItem(AcademicProgram program) { this.program = program; }
        @Override
        public String toString() { return program.getCode() + " - " + program.getName() + " (" + program.getDegree() + ")"; }
    }

    private static class CourseItem {
        final Course course;
        CourseItem(Course course) { this.course = course; }
        @Override
        public String toString() {
            if (course == null) return "— None —";
            return course.getCourseCode() + " - " + course.getName() + " (" + course.getCredits() + " cr)";
        }
    }
}

