package com.college.ui.panel;

import com.college.model.Department;
import com.college.model.Role;
import com.college.model.User;
import com.college.service.DepartmentService;
import com.college.service.ReportService;
import com.college.ui.components.CampusIcons;
import com.college.ui.theme.CampusTheme;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Analytical reports panel supporting dynamic tabular rendering with strict role-based data isolation.
 */
public class ReportPanel extends JPanel {

    private final User currentUser;
    private final ReportService reportService;
    private final DepartmentService departmentService;

    private JComboBox<String> reportTypeCombo;
    private JTextField academicYearField;
    private JComboBox<DepartmentItem> deptFilterCombo;
    private JButton generateButton;
    private JTable reportTable;
    private DefaultTableModel tableModel;
    private JLabel summaryLabel;

    public ReportPanel(User currentUser) {
        this(currentUser, new ReportService(), new DepartmentService());
    }

    public ReportPanel(User currentUser, ReportService reportService, DepartmentService departmentService) {
        this.currentUser = currentUser;
        this.reportService = reportService;
        this.departmentService = departmentService;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(12, 12));
        setBackground(SwingUtils.BG_LIGHT);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Top Toolbar
        JPanel topBar = new JPanel(new BorderLayout(8, 8));
        topBar.setOpaque(false);

        JLabel titleLabel = new JLabel("System Reports & Analytics");
        titleLabel.setFont(SwingUtils.TITLE_FONT);
        titleLabel.setForeground(SwingUtils.PRIMARY_COLOR);
        topBar.add(titleLabel, BorderLayout.WEST);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);

        controls.add(new JLabel("Report:"));
        reportTypeCombo = new JComboBox<>();
        populateReportOptions();
        controls.add(reportTypeCombo);

        controls.add(new JLabel("Year:"));
        academicYearField = new JTextField("2026-2027", 8);
        controls.add(academicYearField);

        if (currentUser.getRole() == Role.ADMIN) {
            controls.add(new JLabel("Department:"));
            deptFilterCombo = new JComboBox<>();
            deptFilterCombo.addItem(new DepartmentItem(0, "All Departments"));
            controls.add(deptFilterCombo);
            loadDepartments();
        }

        generateButton = SwingUtils.createPrimaryButton("Generate Report");
        generateButton.addActionListener(e -> generateReport());
        controls.add(generateButton);

        topBar.add(controls, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // Center Table
        tableModel = new DefaultTableModel();
        reportTable = SwingUtils.createStandardTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(reportTable);
        add(scrollPane, BorderLayout.CENTER);

        // South Summary
        JPanel southBar = new JPanel(new BorderLayout(10, 4));
        southBar.setOpaque(false);
        summaryLabel = new JLabel("Select report parameters and click Generate Report.");
        summaryLabel.setFont(SwingUtils.BOLD_FONT);
        southBar.add(summaryLabel, BorderLayout.WEST);

        JButton exportCsvBtn = SwingUtils.createSecondaryButton("Export to CSV");
        exportCsvBtn.setIcon(CampusIcons.get(CampusIcons.IconType.REPORTS, 14, CampusTheme.TEXT_SECONDARY));
        exportCsvBtn.addActionListener(e -> exportToCsv());
        southBar.add(exportCsvBtn, BorderLayout.EAST);

        add(southBar, BorderLayout.SOUTH);

        // Auto-run first report
        generateReport();
    }

    private void populateReportOptions() {
        reportTypeCombo.removeAllItems();
        if (currentUser.getRole() == Role.ADMIN) {
            reportTypeCombo.addItem("Student Performance Report");
            reportTypeCombo.addItem("Attendance Summary Report");
            reportTypeCombo.addItem("Attendance Defaulters Report (< 75%)");
            reportTypeCombo.addItem("Attendance Eligible Report (>= 75%)");
            reportTypeCombo.addItem("Attendance Zero-Sessions Report (No Sessions Recorded)");
            reportTypeCombo.addItem("Fee Collection Report");
            reportTypeCombo.addItem("Student Fee Dues Report");
            reportTypeCombo.addItem("Course Enrollment Capacity Report");
            reportTypeCombo.addItem("Department Summary Report");
        } else if (currentUser.getRole() == Role.FACULTY) {
            reportTypeCombo.addItem("Student Performance Report (Assigned Courses)");
            reportTypeCombo.addItem("Attendance Summary Report (Assigned Courses)");
            reportTypeCombo.addItem("Attendance Defaulters Report (< 75% Assigned Courses)");
            reportTypeCombo.addItem("Attendance Eligible Report (>= 75% Assigned Courses)");
        } else if (currentUser.getRole() == Role.STUDENT) {
            reportTypeCombo.addItem("My Academic Performance Report");
        }
    }

    private void loadDepartments() {
        SwingUtils.executeAsync(
                this,
                () -> departmentService.getAllActiveDepartments(currentUser),
                departments -> {
                    if (deptFilterCombo != null) {
                        deptFilterCombo.removeAllItems();
                        deptFilterCombo.addItem(new DepartmentItem(0, "All Departments"));
                        for (Department d : departments) {
                            deptFilterCombo.addItem(new DepartmentItem(d.getId(), d.getCode() + " - " + d.getName()));
                        }
                    }
                }
        );
    }

    private void generateReport() {
        String reportType = (String) reportTypeCombo.getSelectedItem();
        String year = academicYearField.getText().trim();
        DepartmentItem dept = (deptFilterCombo != null) ? (DepartmentItem) deptFilterCombo.getSelectedItem() : null;
        Integer deptId = (dept != null && dept.id > 0) ? dept.id : null;

        if (reportType == null) return;

        generateButton.setEnabled(false);
        summaryLabel.setText("Querying analytical dataset...");

        SwingUtils.executeAsync(
                this,
                () -> {
                    if (reportType.contains("Student Performance") || reportType.contains("My Academic Performance")) {
                        return reportService.getStudentPerformanceReport(currentUser, year, deptId, null);
                    } else if (reportType.contains("Attendance Defaulters")) {
                        return reportService.getAttendanceDefaultersReport(currentUser, year, null, 75.0);
                    } else if (reportType.contains("Attendance Eligible")) {
                        return reportService.getAttendanceEligibleReport(currentUser, year, null, 75.0);
                    } else if (reportType.contains("Attendance Zero-Sessions")) {
                        return reportService.getAttendanceZeroSessionsReport(currentUser, year, null);
                    } else if (reportType.contains("Attendance Summary")) {
                        return reportService.getAttendanceSummaryReport(currentUser, year, deptId, null);
                    } else if (reportType.contains("Fee Collection")) {
                        return reportService.getFeeCollectionReport(currentUser, year, deptId);
                    } else if (reportType.contains("Student Fee Dues")) {
                        return reportService.getStudentFeeDuesReport(currentUser, year, deptId);
                    } else if (reportType.contains("Course Enrollment Capacity")) {
                        return reportService.getCourseEnrollmentCapacityReport(currentUser, year, deptId);
                    } else if (reportType.contains("Department Summary")) {
                        return reportService.getDepartmentSummaryReport(currentUser);
                    }
                    return List.<Map<String, Object>>of();
                },
                rows -> {
                    generateButton.setEnabled(true);
                    renderReportTable(rows);
                    summaryLabel.setText("Total Records Retrieved: " + rows.size());
                },
                error -> {
                    generateButton.setEnabled(true);
                    summaryLabel.setText("Error generating report: " + error.getMessage());
                }
        );
    }

    private void exportToCsv() {
        if (tableModel.getRowCount() == 0 || tableModel.getColumnCount() == 0) {
            SwingUtils.showWarning(this, "No data available to export.");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Report to CSV");
        String reportName = ((String) reportTypeCombo.getSelectedItem()).replaceAll("[^a-zA-Z0-9_-]", "_");
        fileChooser.setSelectedFile(new java.io.File(reportName + ".csv"));

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            java.io.File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getName().toLowerCase().endsWith(".csv")) {
                fileToSave = new java.io.File(fileToSave.getAbsolutePath() + ".csv");
            }

            try (java.io.PrintWriter writer = new java.io.PrintWriter(
                    new java.io.OutputStreamWriter(new java.io.FileOutputStream(fileToSave), java.nio.charset.StandardCharsets.UTF_8))) {

                int colCount = tableModel.getColumnCount();
                int rowCount = tableModel.getRowCount();

                // Header row
                for (int i = 0; i < colCount; i++) {
                    writer.print(escapeCsv(tableModel.getColumnName(i)));
                    if (i < colCount - 1) writer.print(",");
                }
                writer.println();

                // Data rows
                for (int row = 0; row < rowCount; row++) {
                    for (int col = 0; col < colCount; col++) {
                        Object val = tableModel.getValueAt(row, col);
                        writer.print(escapeCsv(val != null ? val.toString() : ""));
                        if (col < colCount - 1) writer.print(",");
                    }
                    writer.println();
                }

                SwingUtils.showInfo(this, "Report exported successfully to:\n" + fileToSave.getAbsolutePath());
            } catch (Exception ex) {
                SwingUtils.showError(this, "Failed to export CSV: " + ex.getMessage());
            }
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void renderReportTable(List<Map<String, Object>> rows) {
        tableModel.setRowCount(0);
        tableModel.setColumnCount(0);

        if (rows == null || rows.isEmpty()) {
            tableModel.addColumn("Result");
            tableModel.addRow(new Object[]{"No records found matching criteria."});
            return;
        }

        // Extract columns from first map keys
        Map<String, Object> first = rows.get(0);
        List<String> colKeys = new ArrayList<>(first.keySet());

        for (String k : colKeys) {
            String title = formatHeaderName(k);
            tableModel.addColumn(title);
        }

        for (Map<String, Object> row : rows) {
            Object[] values = new Object[colKeys.size()];
            for (int i = 0; i < colKeys.size(); i++) {
                Object val = row.get(colKeys.get(i));
                String key = colKeys.get(i).toLowerCase();
                if (val instanceof Number && (key.contains("paise") || key.contains("amount") || key.contains("fee") || key.contains("dues") || key.contains("paid"))) {
                    values[i] = SwingUtils.formatPaise(((Number) val).longValue());
                } else if (val != null) {
                    values[i] = val.toString();
                } else {
                    values[i] = "—";
                }
            }
            tableModel.addRow(values);
        }
    }

    private String formatHeaderName(String raw) {
        StringBuilder sb = new StringBuilder();
        for (String part : raw.split("_")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return sb.toString().trim();
    }

    public static class DepartmentItem {
        public final int id;
        public final String name;
        public DepartmentItem(int id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }
}
