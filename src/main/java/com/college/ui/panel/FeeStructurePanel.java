package com.college.ui.panel;

import com.college.model.Department;
import com.college.model.FeeStructure;
import com.college.model.Role;
import com.college.model.User;
import com.college.service.DepartmentService;
import com.college.service.FeeStructureService;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Management and viewing panel for academic fee structures with exact integer paise financial integrity.
 */
public class FeeStructurePanel extends JPanel {

    private final User currentUser;
    private final FeeStructureService feeStructureService;
    private final DepartmentService departmentService;

    private JTable feeTable;
    private DefaultTableModel tableModel;
    private JComboBox<DepartmentItem> deptFilterCombo;
    private JComboBox<DepartmentItem> deptFormCombo;

    private JSpinner semesterSpinner;
    private JTextField academicYearField;
    private JTextField feeTypeField;
    private JTextField amountField;
    private JTextField dueDateField;

    private JButton createButton;
    private JButton updateButton;
    private JButton deleteButton;
    private JButton reactivateButton;
    private JButton refreshButton;

    private Integer selectedStructureId = null;
    private final Map<Integer, String> departmentMap = new HashMap<>();
    private final Map<Integer, FeeStructure> structureCache = new HashMap<>();

    public FeeStructurePanel(User currentUser) {
        this(currentUser, new FeeStructureService(), new DepartmentService());
    }

    public FeeStructurePanel(User currentUser, FeeStructureService feeStructureService, DepartmentService departmentService) {
        this.currentUser = currentUser;
        this.feeStructureService = feeStructureService;
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

        JLabel titleLabel = new JLabel(currentUser.getRole() == Role.ADMIN ? "Fee Structure Administration" : "Institutional Fee Structures");
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
        String[] columns = {"ID", "Department", "Semester", "Academic Year", "Fee Type", "Amount (INR)", "Due Date", "Status"};
        tableModel = new DefaultTableModel(columns, 0);
        feeTable = SwingUtils.createStandardTable(tableModel);

        if (currentUser.getRole() == Role.ADMIN) {
            feeTable.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting() && feeTable.getSelectedRow() != -1) {
                    populateFormFromSelection();
                }
            });
        }

        JScrollPane scrollPane = new JScrollPane(feeTable);
        add(scrollPane, BorderLayout.CENTER);

        // South Form (Admin Only)
        if (currentUser.getRole() == Role.ADMIN) {
            JPanel formCard = SwingUtils.createCardPanel("Fee Structure Details & Actions");
            JPanel formGrid = new JPanel(new GridLayout(2, 4, 8, 8));
            formGrid.setOpaque(false);

            deptFormCombo = new JComboBox<>();
            semesterSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 8, 1));
            academicYearField = new JTextField("2026-2027");
            feeTypeField = new JTextField("TUITION");
            amountField = new JTextField("50000.00");
            dueDateField = new JTextField("2026-09-30");

            formGrid.add(createFieldBox("Department:", deptFormCombo));
            formGrid.add(createFieldBox("Semester (1-8):", semesterSpinner));
            formGrid.add(createFieldBox("Academic Year:", academicYearField));
            formGrid.add(createFieldBox("Fee Type (e.g. TUITION):", feeTypeField));

            formGrid.add(createFieldBox("Amount in INR (e.g. 50000.00):", amountField));
            formGrid.add(createFieldBox("Due Date (YYYY-MM-DD):", dueDateField));
            formGrid.add(new JLabel("")); // spacer
            formGrid.add(new JLabel("")); // spacer

            JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
            buttonRow.setOpaque(false);

            createButton = SwingUtils.createPrimaryButton("Create Fee Structure");
            createButton.addActionListener(e -> createFeeStructure());

            updateButton = SwingUtils.createSecondaryButton("Update");
            updateButton.setEnabled(false);
            updateButton.addActionListener(e -> updateFeeStructure());

            deleteButton = SwingUtils.createSecondaryButton("Deactivate");
            deleteButton.setEnabled(false);
            deleteButton.addActionListener(e -> deleteFeeStructure());

            reactivateButton = SwingUtils.createSecondaryButton("Reactivate");
            reactivateButton.setEnabled(false);
            reactivateButton.addActionListener(e -> reactivateFeeStructure());

            JButton clearButton = SwingUtils.createSecondaryButton("Clear");
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
                    if (currentUser.getRole() == Role.ADMIN) {
                        return feeStructureService.getAllFeeStructures(currentUser);
                    } else {
                        return feeStructureService.getAllActiveFeeStructures(currentUser);
                    }
                },
                structures -> {
                    tableModel.setRowCount(0);
                    structureCache.clear();
                    for (FeeStructure fs : structures) {
                        structureCache.put(fs.getId(), fs);
                        if (filterDeptId > 0 && fs.getDepartmentId() != filterDeptId) {
                            continue;
                        }
                        String dName = departmentMap.getOrDefault(fs.getDepartmentId(), "Dept #" + fs.getDepartmentId());
                        tableModel.addRow(new Object[]{
                                fs.getId(),
                                dName,
                                fs.getSemester(),
                                fs.getAcademicYear(),
                                fs.getFeeType(),
                                SwingUtils.formatPaise(fs.getAmountPaise()),
                                fs.getDueDate(),
                                fs.isActive() ? "Active" : "Inactive"
                        });
                    }
                    clearForm();
                    refreshButton.setEnabled(true);
                },
                error -> refreshButton.setEnabled(true)
        );
    }

    private void populateFormFromSelection() {
        int row = feeTable.getSelectedRow();
        if (row == -1) return;

        selectedStructureId = (Integer) tableModel.getValueAt(row, 0);
        FeeStructure fs = structureCache.get(selectedStructureId);
        if (fs != null) {
            populateFormFromStructure(fs);
        }
    }

    public void populateFormFromStructure(FeeStructure fs) {
        if (fs == null) return;
        selectedStructureId = fs.getId();

        if (deptFormCombo != null) {
            for (int i = 0; i < deptFormCombo.getItemCount(); i++) {
                if (deptFormCombo.getItemAt(i).id == fs.getDepartmentId()) {
                    deptFormCombo.setSelectedIndex(i);
                    break;
                }
            }
        }

        if (semesterSpinner != null && fs.getSemester() != null) {
            try {
                semesterSpinner.setValue(Integer.parseInt(fs.getSemester().trim()));
            } catch (NumberFormatException e) {
                // Default fallback to semester 1 if format is non-numeric
                semesterSpinner.setValue(1);
            }
        }

        if (academicYearField != null) {
            academicYearField.setText(fs.getAcademicYear() != null ? fs.getAcademicYear() : "");
        }

        if (feeTypeField != null) {
            feeTypeField.setText(fs.getFeeType() != null ? fs.getFeeType() : "");
        }

        if (amountField != null) {
            BigDecimal amountInr = BigDecimal.valueOf(fs.getAmountPaise()).divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY);
            amountField.setText(amountInr.toPlainString());
        }

        if (dueDateField != null) {
            dueDateField.setText(fs.getDueDate() != null ? fs.getDueDate() : "");
        }

        boolean isActive = fs.isActive();
        if (updateButton != null) updateButton.setEnabled(true);
        if (deleteButton != null) deleteButton.setEnabled(isActive);
        if (reactivateButton != null) reactivateButton.setEnabled(!isActive);
    }

    private void createFeeStructure() {
        FeeStructure fs = buildStructureFromForm();
        if (fs == null) return;

        createButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> feeStructureService.createFeeStructure(currentUser, fs),
                created -> {
                    createButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Fee structure created successfully.");
                    loadData();
                },
                error -> createButton.setEnabled(true)
        );
    }

    private void updateFeeStructure() {
        if (selectedStructureId == null) return;
        FeeStructure fs = buildStructureFromForm();
        if (fs == null) return;
        fs.setId(selectedStructureId);
        FeeStructure cached = structureCache.get(selectedStructureId);
        fs.setActive(cached != null ? cached.isActive() : true);

        updateButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> feeStructureService.updateFeeStructure(currentUser, fs),
                updated -> {
                    updateButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Fee structure updated successfully.");
                    loadData();
                },
                error -> updateButton.setEnabled(true)
        );
    }

    private void deleteFeeStructure() {
        if (selectedStructureId == null) return;
        boolean confirm = SwingUtils.showConfirm(this,
                "Deactivate fee structure ID " + selectedStructureId + "?",
                "Confirm Deactivation");
        if (!confirm) return;

        deleteButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> {
                    feeStructureService.deleteFeeStructure(currentUser, selectedStructureId);
                    return null;
                },
                unused -> {
                    deleteButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Fee structure deactivated.");
                    loadData();
                },
                error -> deleteButton.setEnabled(true)
        );
    }

    private void reactivateFeeStructure() {
        if (selectedStructureId == null) return;
        reactivateButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> feeStructureService.reactivateFeeStructure(currentUser, selectedStructureId),
                reactivated -> {
                    reactivateButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Fee structure reactivated.");
                    loadData();
                },
                error -> reactivateButton.setEnabled(true)
        );
    }

    private FeeStructure buildStructureFromForm() {
        DepartmentItem dept = (DepartmentItem) deptFormCombo.getSelectedItem();
        int sem = semesterSpinner != null ? (Integer) semesterSpinner.getValue() : 1;
        String year = academicYearField.getText().trim();
        String type = feeTypeField.getText().trim();
        String due = dueDateField.getText().trim();
        String amtStr = amountField.getText().trim();

        if (dept == null || year.isEmpty() || type.isEmpty() || due.isEmpty() || amtStr.isEmpty()) {
            SwingUtils.showWarning(this, "All fields are required.");
            return null;
        }

        long paise;
        try {
            paise = SwingUtils.parsePaise(amtStr);
        } catch (Exception ex) {
            SwingUtils.showWarning(this, ex.getMessage());
            return null;
        }

        FeeStructure fs = new FeeStructure();
        fs.setDepartmentId(dept.id);
        fs.setSemester(String.valueOf(sem));
        fs.setAcademicYear(year);
        fs.setFeeType(type);
        fs.setAmountPaise(paise);
        fs.setDueDate(due);
        return fs;
    }

    private void clearForm() {
        selectedStructureId = null;
        if (semesterSpinner != null) semesterSpinner.setValue(1);
        if (academicYearField != null) academicYearField.setText("2026-2027");
        if (feeTypeField != null) feeTypeField.setText("TUITION");
        if (amountField != null) amountField.setText("50000.00");
        if (dueDateField != null) dueDateField.setText("2026-09-30");
        if (updateButton != null) updateButton.setEnabled(false);
        if (deleteButton != null) deleteButton.setEnabled(false);
        if (reactivateButton != null) reactivateButton.setEnabled(false);
        if (feeTable != null) feeTable.clearSelection();
    }

    public Integer getSelectedStructureId() { return selectedStructureId; }
    public Integer getSelectedDepartmentId() {
        DepartmentItem item = deptFormCombo != null ? (DepartmentItem) deptFormCombo.getSelectedItem() : null;
        return item != null ? item.id : null;
    }
    public Integer getSemesterValue() { return semesterSpinner != null ? (Integer) semesterSpinner.getValue() : null; }
    public String getAcademicYearText() { return academicYearField != null ? academicYearField.getText() : null; }
    public String getFeeTypeText() { return feeTypeField != null ? feeTypeField.getText() : null; }
    public String getAmountText() { return amountField != null ? amountField.getText() : null; }
    public String getDueDateText() { return dueDateField != null ? dueDateField.getText() : null; }
    public void setAmountText(String text) { if (amountField != null) amountField.setText(text); }
    public void setDueDateText(String text) { if (dueDateField != null) dueDateField.setText(text); }
    public void setSemesterValue(int sem) { if (semesterSpinner != null) semesterSpinner.setValue(sem); }
    public void setAcademicYearText(String year) { if (academicYearField != null) academicYearField.setText(year); }
    public JTable getFeeTable() { return feeTable; }
    public JButton getUpdateButton() { return updateButton; }
    public JButton getCreateButton() { return createButton; }

    public static class DepartmentItem {
        public final int id;
        public final String name;
        public DepartmentItem(int id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }
}
