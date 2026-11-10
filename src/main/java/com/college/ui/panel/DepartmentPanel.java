package com.college.ui.panel;

import com.college.model.Department;
import com.college.model.Role;
import com.college.model.User;
import com.college.service.DepartmentService;
import com.college.ui.theme.CampusTheme;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Management UI for Academic Departments with School filtering, search,
 * institutional metadata editing, associated record inspection, and safe deactivation rules.
 */
public class DepartmentPanel extends JPanel {

    private final User currentUser;
    private final DepartmentService departmentService;

    private JTable departmentTable;
    private DefaultTableModel tableModel;

    private JComboBox<String> schoolFilterCombo;
    private JTextField searchField;

    private JTextField codeField;
    private JTextField nameField;
    private JComboBox<String> schoolCombo;
    private JTextField hodField;
    private JTextField emailField;
    private JTextField phoneField;
    private JTextField descField;

    private JButton createButton;
    private JButton updateButton;
    private JButton deleteButton;
    private JButton reactivateButton;
    private JButton viewStatsButton;
    private JButton refreshButton;

    private Integer selectedDepartmentId = null;
    private List<Department> allLoadedDepartments = new ArrayList<>();

    public DepartmentPanel(User currentUser) {
        this(currentUser, new DepartmentService());
    }

    public DepartmentPanel(User currentUser, DepartmentService departmentService) {
        this.currentUser = currentUser;
        this.departmentService = departmentService;
        initUI();
        loadData();
    }

    private void initUI() {
        setLayout(new BorderLayout(12, 12));
        setBackground(SwingUtils.BG_LIGHT);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Top Header and Filters
        JPanel topContainer = new JPanel(new BorderLayout(8, 8));
        topContainer.setOpaque(false);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JLabel titleLabel = new JLabel("Academic Departments & Schools");
        titleLabel.setFont(SwingUtils.TITLE_FONT);
        titleLabel.setForeground(SwingUtils.PRIMARY_COLOR);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        refreshButton = SwingUtils.createSecondaryButton("Refresh");
        refreshButton.addActionListener(e -> loadData());
        headerPanel.add(refreshButton, BorderLayout.EAST);
        topContainer.add(headerPanel, BorderLayout.NORTH);

        // Filter / Search Bar
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        filterBar.setOpaque(false);

        filterBar.add(new JLabel("School:"));
        schoolFilterCombo = new JComboBox<>(new String[]{"ALL"});
        schoolFilterCombo.setPreferredSize(new Dimension(200, 28));
        schoolFilterCombo.addActionListener(e -> applyFilterAndSearch());
        filterBar.add(schoolFilterCombo);

        filterBar.add(new JLabel("Search:"));
        searchField = new JTextField(16);
        searchField.setPreferredSize(new Dimension(160, 28));
        searchField.addActionListener(e -> applyFilterAndSearch());
        filterBar.add(searchField);

        JButton filterBtn = SwingUtils.createSecondaryButton("Search");
        filterBtn.addActionListener(e -> applyFilterAndSearch());
        filterBar.add(filterBtn);

        JButton resetBtn = SwingUtils.createSecondaryButton("Reset");
        resetBtn.addActionListener(e -> {
            schoolFilterCombo.setSelectedItem("ALL");
            searchField.setText("");
            applyFilterAndSearch();
        });
        filterBar.add(resetBtn);

        topContainer.add(filterBar, BorderLayout.SOUTH);
        add(topContainer, BorderLayout.NORTH);

        // Center Table
        String[] columns = {"ID", "Code", "Department Name", "School / Faculty", "Head of Dept", "Contact Email", "Phone", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        departmentTable = SwingUtils.createStandardTable(tableModel);

        departmentTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && departmentTable.getSelectedRow() != -1) {
                int row = departmentTable.getSelectedRow();
                selectedDepartmentId = (Integer) tableModel.getValueAt(row, 0);
                codeField.setText((String) tableModel.getValueAt(row, 1));
                nameField.setText((String) tableModel.getValueAt(row, 2));

                String school = (String) tableModel.getValueAt(row, 3);
                if (school != null) schoolCombo.setSelectedItem(school);

                hodField.setText(tableModel.getValueAt(row, 4) != null ? (String) tableModel.getValueAt(row, 4) : "");
                emailField.setText(tableModel.getValueAt(row, 5) != null ? (String) tableModel.getValueAt(row, 5) : "");
                phoneField.setText(tableModel.getValueAt(row, 6) != null ? (String) tableModel.getValueAt(row, 6) : "");

                // Retrieve description from cache
                for (Department d : allLoadedDepartments) {
                    if (d.getId() == selectedDepartmentId) {
                        descField.setText(d.getDescription() != null ? d.getDescription() : "");
                        break;
                    }
                }

                boolean isActive = "Active".equals(tableModel.getValueAt(row, 7));
                if (currentUser.getRole() == Role.ADMIN) {
                    updateButton.setEnabled(true);
                    deleteButton.setEnabled(isActive);
                    reactivateButton.setEnabled(!isActive);
                    viewStatsButton.setEnabled(true);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(departmentTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 224, 230)));
        add(scrollPane, BorderLayout.CENTER);

        // South Form (Admin Only Actions)
        if (currentUser.getRole() == Role.ADMIN) {
            JPanel formCard = SwingUtils.createCardPanel("Department Details & Academic Settings");
            JPanel formGrid = new JPanel(new GridBagLayout());
            formGrid.setOpaque(false);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 6, 4, 6);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            // Row 0: Code, Name, School
            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0;
            formGrid.add(new JLabel("Code:"), gbc);
            gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.15;
            codeField = new JTextField(6);
            formGrid.add(codeField, gbc);

            gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0.0;
            formGrid.add(new JLabel("Department Name:"), gbc);
            gbc.gridx = 3; gbc.gridy = 0; gbc.weightx = 0.45;
            nameField = new JTextField(20);
            formGrid.add(nameField, gbc);

            gbc.gridx = 4; gbc.gridy = 0; gbc.weightx = 0.0;
            formGrid.add(new JLabel("School:"), gbc);
            gbc.gridx = 5; gbc.gridy = 0; gbc.weightx = 0.4;
            schoolCombo = new JComboBox<>(new String[]{
                    "School of Engineering",
                    "School of Computer Applications",
                    "School of Management & Commerce",
                    "School of Arts & Humanities",
                    "School of Hospitality",
                    "School of Health Sciences"
            });
            schoolCombo.setEditable(true);
            formGrid.add(schoolCombo, gbc);

            // Row 1: Head of Department, Contact Email, Contact Phone
            gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
            formGrid.add(new JLabel("HOD:"), gbc);
            gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0.15;
            hodField = new JTextField(12);
            formGrid.add(hodField, gbc);

            gbc.gridx = 2; gbc.gridy = 1; gbc.weightx = 0.0;
            formGrid.add(new JLabel("Email:"), gbc);
            gbc.gridx = 3; gbc.gridy = 1; gbc.weightx = 0.45;
            emailField = new JTextField(16);
            formGrid.add(emailField, gbc);

            gbc.gridx = 4; gbc.gridy = 1; gbc.weightx = 0.0;
            formGrid.add(new JLabel("Phone:"), gbc);
            gbc.gridx = 5; gbc.gridy = 1; gbc.weightx = 0.4;
            phoneField = new JTextField(12);
            formGrid.add(phoneField, gbc);

            // Row 2: Description
            gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0;
            formGrid.add(new JLabel("Description:"), gbc);
            gbc.gridx = 1; gbc.gridy = 2; gbc.gridwidth = 5; gbc.weightx = 1.0;
            descField = new JTextField();
            formGrid.add(descField, gbc);

            JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
            buttonRow.setOpaque(false);

            viewStatsButton = SwingUtils.createSecondaryButton("View Associated Records");
            viewStatsButton.setEnabled(false);
            viewStatsButton.addActionListener(e -> showDepartmentStatsDialog());

            createButton = SwingUtils.createPrimaryButton("Create / Reactivate");
            createButton.addActionListener(e -> createDepartment());

            updateButton = SwingUtils.createSecondaryButton("Update Details");
            updateButton.setEnabled(false);
            updateButton.addActionListener(e -> updateDepartment());

            deleteButton = SwingUtils.createSecondaryButton("Deactivate");
            deleteButton.setEnabled(false);
            deleteButton.addActionListener(e -> deleteDepartment());

            reactivateButton = SwingUtils.createSecondaryButton("Reactivate");
            reactivateButton.setEnabled(false);
            reactivateButton.addActionListener(e -> reactivateDepartment());

            JButton clearButton = SwingUtils.createSecondaryButton("Clear");
            clearButton.addActionListener(e -> clearForm());

            buttonRow.add(viewStatsButton);
            buttonRow.add(clearButton);
            buttonRow.add(reactivateButton);
            buttonRow.add(deleteButton);
            buttonRow.add(updateButton);
            buttonRow.add(createButton);

            formCard.add(formGrid, BorderLayout.CENTER);
            formCard.add(buttonRow, BorderLayout.SOUTH);
            add(formCard, BorderLayout.SOUTH);
        }
    }

    public void loadData() {
        refreshButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> {
                    List<Department> list = (currentUser.getRole() == Role.ADMIN)
                            ? departmentService.getAllDepartments(currentUser)
                            : departmentService.getAllActiveDepartments(currentUser);
                    List<String> schools = departmentService.getAllSchools(currentUser);
                    return new Object[]{list, schools};
                },
                result -> {
                    @SuppressWarnings("unchecked")
                    List<Department> list = (List<Department>) result[0];
                    @SuppressWarnings("unchecked")
                    List<String> schools = (List<String>) result[1];

                    allLoadedDepartments = list;

                    // Update school filter
                    String currentFilter = (String) schoolFilterCombo.getSelectedItem();
                    schoolFilterCombo.removeAllItems();
                    schoolFilterCombo.addItem("ALL");
                    for (String sch : schools) {
                        if (sch != null && !sch.trim().isEmpty()) {
                            schoolFilterCombo.addItem(sch.trim());
                        }
                    }
                    if (currentFilter != null) {
                        schoolFilterCombo.setSelectedItem(currentFilter);
                    }

                    applyFilterAndSearch();
                    clearForm();
                    refreshButton.setEnabled(true);
                },
                error -> {
                    refreshButton.setEnabled(true);
                    SwingUtils.showError(this, "Failed to load departments: " + error.getMessage());
                }
        );
    }

    private void applyFilterAndSearch() {
        tableModel.setRowCount(0);
        String selectedSchool = (String) schoolFilterCombo.getSelectedItem();
        String query = searchField.getText().trim().toLowerCase();

        for (Department d : allLoadedDepartments) {
            boolean schoolMatch = (selectedSchool == null || "ALL".equalsIgnoreCase(selectedSchool) ||
                    (d.getSchool() != null && d.getSchool().equalsIgnoreCase(selectedSchool)));

            boolean queryMatch = query.isEmpty() ||
                    (d.getCode() != null && d.getCode().toLowerCase().contains(query)) ||
                    (d.getName() != null && d.getName().toLowerCase().contains(query)) ||
                    (d.getHeadOfDepartment() != null && d.getHeadOfDepartment().toLowerCase().contains(query));

            if (schoolMatch && queryMatch) {
                tableModel.addRow(new Object[]{
                        d.getId(),
                        d.getCode(),
                        d.getName(),
                        d.getSchool(),
                        d.getHeadOfDepartment() != null ? d.getHeadOfDepartment() : "—",
                        d.getContactEmail() != null ? d.getContactEmail() : "—",
                        d.getContactPhone() != null ? d.getContactPhone() : "—",
                        d.isActive() ? "Active" : "Inactive"
                });
            }
        }
    }

    private void createDepartment() {
        String code = codeField.getText().trim();
        String name = nameField.getText().trim();
        String school = (String) schoolCombo.getSelectedItem();
        String hod = hodField.getText().trim();
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();
        String desc = descField.getText().trim();

        if (code.isEmpty() || name.isEmpty()) {
            SwingUtils.showWarning(this, "Department Code and Name are required.");
            return;
        }

        createButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> departmentService.createDepartment(currentUser, code, name, school, desc, hod, email, phone),
                result -> {
                    createButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Department successfully saved: " + result.getCode());
                    loadData();
                },
                error -> {
                    createButton.setEnabled(true);
                    SwingUtils.showError(this, "Failed to create department: " + error.getMessage());
                }
        );
    }

    private void updateDepartment() {
        if (selectedDepartmentId == null) return;
        String code = codeField.getText().trim();
        String name = nameField.getText().trim();
        String school = (String) schoolCombo.getSelectedItem();
        String hod = hodField.getText().trim();
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();
        String desc = descField.getText().trim();

        if (name.isEmpty() || code.isEmpty()) {
            SwingUtils.showWarning(this, "Department Code and Name cannot be empty.");
            return;
        }

        Department dept = new Department(selectedDepartmentId, code, name, school, desc, hod, email, phone, true, null);

        updateButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> departmentService.updateDepartment(currentUser, dept),
                result -> {
                    updateButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Department updated successfully.");
                    loadData();
                },
                error -> {
                    updateButton.setEnabled(true);
                    SwingUtils.showError(this, "Failed to update department: " + error.getMessage());
                }
        );
    }

    private void deleteDepartment() {
        if (selectedDepartmentId == null) return;
        boolean confirm = SwingUtils.showConfirm(this,
                "Are you sure you want to deactivate department ID " + selectedDepartmentId + "?",
                "Confirm Deactivation");
        if (!confirm) return;

        deleteButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> {
                    departmentService.deleteDepartment(currentUser, selectedDepartmentId);
                    return null;
                },
                unused -> {
                    deleteButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Department deactivated.");
                    loadData();
                },
                error -> {
                    deleteButton.setEnabled(true);
                    SwingUtils.showWarning(this, "Deactivation Blocked:\n" + error.getMessage());
                }
        );
    }

    private void reactivateDepartment() {
        if (selectedDepartmentId == null) return;
        reactivateButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> departmentService.reactivateDepartment(currentUser, selectedDepartmentId),
                result -> {
                    reactivateButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Department reactivated: " + result.getCode());
                    loadData();
                },
                error -> {
                    reactivateButton.setEnabled(true);
                    SwingUtils.showError(this, "Failed to reactivate: " + error.getMessage());
                }
        );
    }

    private void showDepartmentStatsDialog() {
        if (selectedDepartmentId == null) return;
        viewStatsButton.setEnabled(false);

        SwingUtils.executeAsync(
                this,
                () -> departmentService.getDepartmentDetailsWithStats(currentUser, selectedDepartmentId),
                dept -> {
                    viewStatsButton.setEnabled(true);
                    boolean canDeact = (dept.getProgramCount() == 0 && dept.getFacultyCount() == 0 &&
                            dept.getStudentCount() == 0 && dept.getCourseCount() == 0);

                    String msg = String.format(
                            "DEPARTMENT DETAILS & ASSOCIATIONS\n\n" +
                            "Code: %s\n" +
                            "Name: %s\n" +
                            "School: %s\n" +
                            "Head of Department: %s\n" +
                            "Email: %s | Phone: %s\n" +
                            "Status: %s\n\n" +
                            "ASSOCIATED RECORDS:\n" +
                            "• Academic Programmes: %d\n" +
                            "• Faculty Members: %d\n" +
                            "• Enrolled Students: %d\n" +
                            "• Courses Offered: %d\n\n" +
                            "Deactivation Eligibility: %s\n" +
                            (canDeact ? "(No active records will be orphaned)" : "(Active records must be reassigned prior to deactivation)"),
                            dept.getCode(), dept.getName(), dept.getSchool(),
                            dept.getHeadOfDepartment() != null ? dept.getHeadOfDepartment() : "—",
                            dept.getContactEmail() != null ? dept.getContactEmail() : "—",
                            dept.getContactPhone() != null ? dept.getContactPhone() : "—",
                            dept.isActive() ? "ACTIVE" : "INACTIVE",
                            dept.getProgramCount(), dept.getFacultyCount(),
                            dept.getStudentCount(), dept.getCourseCount(),
                            canDeact ? "ELIGIBLE" : "BLOCKED BY BUSINESS RULES"
                    );

                    JOptionPane.showMessageDialog(this, msg, "Department Institutional Profile",
                            canDeact ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                },
                error -> {
                    viewStatsButton.setEnabled(true);
                    SwingUtils.showError(this, "Failed to load statistics: " + error.getMessage());
                }
        );
    }

    private void clearForm() {
        selectedDepartmentId = null;
        codeField.setText("");
        nameField.setText("");
        if (schoolCombo != null && schoolCombo.getItemCount() > 0) schoolCombo.setSelectedIndex(0);
        if (hodField != null) hodField.setText("");
        if (emailField != null) emailField.setText("");
        if (phoneField != null) phoneField.setText("");
        if (descField != null) descField.setText("");

        departmentTable.clearSelection();
        if (currentUser.getRole() == Role.ADMIN) {
            updateButton.setEnabled(false);
            deleteButton.setEnabled(false);
            reactivateButton.setEnabled(false);
            if (viewStatsButton != null) viewStatsButton.setEnabled(false);
        }
    }
}
