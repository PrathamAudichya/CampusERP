package com.college.ui.panel;

import com.college.dto.AcademicOverviewDTO;
import com.college.model.Department;
import com.college.model.User;
import com.college.service.AcademicReportService;
import com.college.service.DepartmentService;
import com.college.ui.components.StatusBadge;
import com.college.ui.theme.CampusTheme;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Institutional Academic Overview Dashboard displaying aggregate metrics across
 * Schools, Departments, Degree Programmes, Semester cohorts, and faculty allocations.
 */
public class AcademicOverviewPanel extends JPanel {

    private final User currentUser;
    private final AcademicReportService reportService;
    private final DepartmentService departmentService;

    private JLabel totalSchoolsLabel;
    private JLabel totalDeptsLabel;
    private JLabel totalProgsLabel;
    private JLabel totalStudentsLabel;

    private JComboBox<String> schoolFilterCombo;
    private JComboBox<DepartmentItem> deptFilterCombo;

    private JTable progDistTable;
    private DefaultTableModel progDistModel;

    private JTable semDistTable;
    private DefaultTableModel semDistModel;

    private JTable deptFacTable;
    private DefaultTableModel deptFacModel;

    private JTable cohortTable;
    private DefaultTableModel cohortModel;

    private JButton exportButton;
    private JButton refreshButton;

    public AcademicOverviewPanel(User currentUser) {
        this(currentUser, new AcademicReportService(), new DepartmentService());
    }

    public AcademicOverviewPanel(User currentUser, AcademicReportService reportService, DepartmentService departmentService) {
        this.currentUser = currentUser;
        this.reportService = reportService;
        this.departmentService = departmentService;
        initUI();
        loadSchoolsAndDepts();
    }

    private void initUI() {
        setLayout(new BorderLayout(12, 12));
        setBackground(SwingUtils.BG_LIGHT);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Top Header
        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        topPanel.setOpaque(false);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Institutional Academic Overview");
        title.setFont(SwingUtils.TITLE_FONT);
        title.setForeground(SwingUtils.PRIMARY_COLOR);
        header.add(title, BorderLayout.WEST);

        JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        topButtons.setOpaque(false);

        exportButton = SwingUtils.createSecondaryButton("Export Overview CSV");
        exportButton.addActionListener(e -> exportCsv());
        topButtons.add(exportButton);

        refreshButton = SwingUtils.createSecondaryButton("Refresh");
        refreshButton.addActionListener(e -> loadDashboardData());
        topButtons.add(refreshButton);

        header.add(topButtons, BorderLayout.EAST);
        topPanel.add(header, BorderLayout.NORTH);

        // Filter Bar
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        filterBar.setOpaque(false);

        filterBar.add(new JLabel("School:"));
        schoolFilterCombo = new JComboBox<>(new String[]{"ALL"});
        schoolFilterCombo.setPreferredSize(new Dimension(180, 28));
        schoolFilterCombo.addActionListener(e -> loadDashboardData());
        filterBar.add(schoolFilterCombo);

        filterBar.add(new JLabel("Department:"));
        deptFilterCombo = new JComboBox<>();
        deptFilterCombo.setPreferredSize(new Dimension(200, 28));
        deptFilterCombo.addActionListener(e -> loadDashboardData());
        filterBar.add(deptFilterCombo);

        JButton filterBtn = SwingUtils.createSecondaryButton("Apply Filter");
        filterBtn.addActionListener(e -> loadDashboardData());
        filterBar.add(filterBtn);

        JButton resetBtn = SwingUtils.createSecondaryButton("Reset");
        resetBtn.addActionListener(e -> {
            schoolFilterCombo.setSelectedIndex(0);
            deptFilterCombo.setSelectedIndex(0);
            loadDashboardData();
        });
        filterBar.add(resetBtn);

        topPanel.add(filterBar, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        // Center: KPI Metric Cards + Tabular Distributions
        JPanel centerPanel = new JPanel(new BorderLayout(12, 12));
        centerPanel.setOpaque(false);

        // Metric Cards Grid
        JPanel kpiGrid = new JPanel(new GridLayout(1, 4, 12, 0));
        kpiGrid.setOpaque(false);

        totalSchoolsLabel = new JLabel("—", SwingConstants.CENTER);
        totalDeptsLabel = new JLabel("—", SwingConstants.CENTER);
        totalProgsLabel = new JLabel("—", SwingConstants.CENTER);
        totalStudentsLabel = new JLabel("—", SwingConstants.CENTER);

        kpiGrid.add(createKpiCard("FACULTIES & SCHOOLS", totalSchoolsLabel, CampusTheme.PRIMARY));
        kpiGrid.add(createKpiCard("ACADEMIC DEPARTMENTS", totalDeptsLabel, CampusTheme.INFO));
        kpiGrid.add(createKpiCard("DEGREE PROGRAMMES", totalProgsLabel, CampusTheme.TERRACOTTA));
        kpiGrid.add(createKpiCard("ENROLLED STUDENTS", totalStudentsLabel, CampusTheme.SUCCESS));

        centerPanel.add(kpiGrid, BorderLayout.NORTH);

        // 4-Quadrant Distribution Tables
        JPanel gridPanel = new JPanel(new GridLayout(2, 2, 12, 12));
        gridPanel.setOpaque(false);

        // 1. Programme Distribution
        progDistModel = new DefaultTableModel(new String[]{"Academic Programme", "Enrolled Students"}, 0);
        progDistTable = SwingUtils.createStandardTable(progDistModel);
        gridPanel.add(wrapTableInCard("Programme Student Distribution", progDistTable));

        // 2. Semester Distribution
        semDistModel = new DefaultTableModel(new String[]{"Semester Level", "Enrolled Students"}, 0);
        semDistTable = SwingUtils.createStandardTable(semDistModel);
        gridPanel.add(wrapTableInCard("Semester-wise Student Distribution", semDistTable));

        // 3. Faculty Distribution
        deptFacModel = new DefaultTableModel(new String[]{"Academic Department", "Faculty Count"}, 0);
        deptFacTable = SwingUtils.createStandardTable(deptFacModel);
        gridPanel.add(wrapTableInCard("Department Faculty Distribution", deptFacTable));

        // 4. Cohorts Overview
        cohortModel = new DefaultTableModel(new String[]{"Academic Cohort Code", "Cohort Enrolled Size"}, 0);
        cohortTable = SwingUtils.createStandardTable(cohortModel);
        gridPanel.add(wrapTableInCard("Programme Cohorts Overview", cohortTable));

        centerPanel.add(gridPanel, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);
    }

    private JPanel createKpiCard(String title, JLabel valueLabel, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(6, 6));
        card.setBackground(CampusTheme.BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(CampusTheme.FONT_SMALL);
        titleLbl.setForeground(CampusTheme.TEXT_SECONDARY);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        valueLabel.setForeground(accentColor);

        card.add(titleLbl, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel wrapTableInCard(String title, JTable table) {
        JPanel card = new JPanel(new BorderLayout(6, 6));
        card.setBackground(CampusTheme.BG_CARD);
        card.setBorder(BorderFactory.createTitledBorder(title));
        card.add(new JScrollPane(table), BorderLayout.CENTER);
        return card;
    }

    private void loadSchoolsAndDepts() {
        SwingUtils.executeAsync(
                this,
                () -> {
                    List<String> schools = departmentService.getAllSchools(currentUser);
                    List<Department> depts = departmentService.getAllActiveDepartments(currentUser);
                    return new Object[]{schools, depts};
                },
                result -> {
                    @SuppressWarnings("unchecked")
                    List<String> schools = (List<String>) result[0];
                    @SuppressWarnings("unchecked")
                    List<Department> depts = (List<Department>) result[1];

                    schoolFilterCombo.removeAllItems();
                    schoolFilterCombo.addItem("ALL");
                    for (String s : schools) {
                        if (s != null && !s.trim().isEmpty()) schoolFilterCombo.addItem(s.trim());
                    }

                    deptFilterCombo.removeAllItems();
                    deptFilterCombo.addItem(new DepartmentItem(null)); // ALL
                    for (Department d : depts) {
                        deptFilterCombo.addItem(new DepartmentItem(d));
                    }

                    loadDashboardData();
                },
                error -> SwingUtils.showError(this, "Failed to load filters: " + error.getMessage())
        );
    }

    public void loadDashboardData() {
        String school = (String) schoolFilterCombo.getSelectedItem();
        DepartmentItem di = (DepartmentItem) deptFilterCombo.getSelectedItem();
        Integer deptId = (di != null && di.dept != null) ? di.dept.getId() : null;

        refreshButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> reportService.getAcademicOverview(currentUser, school, deptId, null),
                dto -> {
                    totalSchoolsLabel.setText(String.valueOf(dto.getTotalSchools()));
                    totalDeptsLabel.setText(dto.getActiveDepartments() + " (" + dto.getTotalDepartments() + " Total)");
                    totalProgsLabel.setText(dto.getActivePrograms() + " (" + dto.getTotalPrograms() + " Total)");
                    totalStudentsLabel.setText(String.valueOf(dto.getTotalStudents()));

                    // 1. Programme Distribution
                    progDistModel.setRowCount(0);
                    for (Map.Entry<String, Integer> e : dto.getProgramStudentDistribution().entrySet()) {
                        progDistModel.addRow(new Object[]{e.getKey(), e.getValue() + " Students"});
                    }

                    // 2. Semester Distribution
                    semDistModel.setRowCount(0);
                    for (Map.Entry<String, Integer> e : dto.getSemesterStudentDistribution().entrySet()) {
                        semDistModel.addRow(new Object[]{e.getKey(), e.getValue() + " Students"});
                    }

                    // 3. Faculty Distribution
                    deptFacModel.setRowCount(0);
                    for (Map.Entry<String, Integer> e : dto.getDepartmentFacultyDistribution().entrySet()) {
                        deptFacModel.addRow(new Object[]{e.getKey(), e.getValue() + " Faculty"});
                    }

                    // 4. Cohorts Overview
                    cohortModel.setRowCount(0);
                    for (Map.Entry<String, Integer> e : dto.getCohortStudentDistribution().entrySet()) {
                        cohortModel.addRow(new Object[]{e.getKey(), e.getValue() + " Students"});
                    }

                    refreshButton.setEnabled(true);
                },
                error -> {
                    refreshButton.setEnabled(true);
                    SwingUtils.showError(this, "Failed to load dashboard data: " + error.getMessage());
                }
        );
    }

    private void exportCsv() {
        String school = (String) schoolFilterCombo.getSelectedItem();
        DepartmentItem di = (DepartmentItem) deptFilterCombo.getSelectedItem();
        Integer deptId = (di != null && di.dept != null) ? di.dept.getId() : null;

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File("Academic_Overview_Report.csv"));
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            SwingUtils.executeAsync(
                    this,
                    () -> reportService.exportAcademicOverviewCsv(currentUser, school, deptId, null),
                    csv -> {
                        try (FileWriter writer = new FileWriter(file)) {
                            writer.write(csv);
                            SwingUtils.showInfo(this, "Academic Overview CSV successfully exported to:\n" + file.getAbsolutePath());
                        } catch (IOException ex) {
                            SwingUtils.showError(this, "Failed to write CSV file: " + ex.getMessage());
                        }
                    },
                    error -> SwingUtils.showError(this, "Export Failed: " + error.getMessage())
            );
        }
    }

    private static class DepartmentItem {
        final Department dept;
        DepartmentItem(Department dept) { this.dept = dept; }
        @Override
        public String toString() {
            if (dept == null) return "— ALL DEPARTMENTS —";
            return dept.getCode() + " - " + dept.getName();
        }
    }
}
