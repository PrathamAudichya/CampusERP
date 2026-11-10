package com.college.ui.panel;

import com.college.model.AcademicProgram;
import com.college.model.Department;
import com.college.model.Role;
import com.college.model.User;
import com.college.service.AcademicProgramService;
import com.college.service.DepartmentService;
import com.college.ui.dialog.AcademicProgramDialog;
import com.college.ui.theme.CampusTheme;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Management UI for Academic Programmes, degrees, qualification levels,
 * semester structures, and curriculum mappings.
 */
public class AcademicProgramPanel extends JPanel {

    private final User currentUser;
    private final AcademicProgramService programService;
    private final DepartmentService departmentService;

    private JTable programTable;
    private DefaultTableModel tableModel;

    private JComboBox<String> levelFilterCombo;
    private JComboBox<String> schoolFilterCombo;
    private JTextField searchField;

    private JButton addButton;
    private JButton editButton;
    private JButton toggleActiveButton;
    private JButton refreshButton;

    private List<AcademicProgram> allPrograms = new ArrayList<>();
    private Integer selectedProgramId = null;

    public AcademicProgramPanel(User currentUser) {
        this(currentUser, new AcademicProgramService(), new DepartmentService());
    }

    public AcademicProgramPanel(User currentUser, AcademicProgramService programService, DepartmentService departmentService) {
        this.currentUser = currentUser;
        this.programService = programService;
        this.departmentService = departmentService;
        initUI();
        loadData();
    }

    private void initUI() {
        setLayout(new BorderLayout(12, 12));
        setBackground(SwingUtils.BG_LIGHT);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Header and Filters
        JPanel topContainer = new JPanel(new BorderLayout(8, 8));
        topContainer.setOpaque(false);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JLabel titleLabel = new JLabel("Academic Programmes & Degrees");
        titleLabel.setFont(SwingUtils.TITLE_FONT);
        titleLabel.setForeground(SwingUtils.PRIMARY_COLOR);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        refreshButton = SwingUtils.createSecondaryButton("Refresh");
        refreshButton.addActionListener(e -> loadData());
        headerPanel.add(refreshButton, BorderLayout.EAST);
        topContainer.add(headerPanel, BorderLayout.NORTH);

        // Filter Bar
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        filterBar.setOpaque(false);

        filterBar.add(new JLabel("Level:"));
        levelFilterCombo = new JComboBox<>(new String[]{"ALL", "UNDERGRADUATE", "POSTGRADUATE", "RESEARCH", "DIPLOMA"});
        levelFilterCombo.addActionListener(e -> applyFilter());
        filterBar.add(levelFilterCombo);

        filterBar.add(new JLabel("School:"));
        schoolFilterCombo = new JComboBox<>(new String[]{"ALL"});
        schoolFilterCombo.setPreferredSize(new Dimension(180, 28));
        schoolFilterCombo.addActionListener(e -> applyFilter());
        filterBar.add(schoolFilterCombo);

        filterBar.add(new JLabel("Search:"));
        searchField = new JTextField(14);
        searchField.addActionListener(e -> applyFilter());
        filterBar.add(searchField);

        JButton searchBtn = SwingUtils.createSecondaryButton("Filter");
        searchBtn.addActionListener(e -> applyFilter());
        filterBar.add(searchBtn);

        JButton resetBtn = SwingUtils.createSecondaryButton("Reset");
        resetBtn.addActionListener(e -> {
            levelFilterCombo.setSelectedIndex(0);
            schoolFilterCombo.setSelectedIndex(0);
            searchField.setText("");
            applyFilter();
        });
        filterBar.add(resetBtn);

        topContainer.add(filterBar, BorderLayout.SOUTH);
        add(topContainer, BorderLayout.NORTH);

        // Table
        String[] cols = {"ID", "Code", "Programme Name", "Degree", "Level", "Department", "School", "Years", "Semesters", "Credits", "Status"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        programTable = SwingUtils.createStandardTable(tableModel);

        programTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && programTable.getSelectedRow() != -1) {
                int row = programTable.getSelectedRow();
                selectedProgramId = (Integer) tableModel.getValueAt(row, 0);
                boolean isActive = "Active".equals(tableModel.getValueAt(row, 10));

                if (currentUser.getRole() == Role.ADMIN) {
                    editButton.setEnabled(true);
                    toggleActiveButton.setEnabled(true);
                    toggleActiveButton.setText(isActive ? "Deactivate Programme" : "Activate Programme");
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(programTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 224, 230)));
        add(scrollPane, BorderLayout.CENTER);

        // Footer Actions
        if (currentUser.getRole() == Role.ADMIN) {
            JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
            footer.setOpaque(false);

            addButton = SwingUtils.createPrimaryButton("Add Programme");
            addButton.addActionListener(e -> openAddDialog());

            editButton = SwingUtils.createSecondaryButton("Edit Programme");
            editButton.setEnabled(false);
            editButton.addActionListener(e -> openEditDialog());

            toggleActiveButton = SwingUtils.createSecondaryButton("Toggle Active");
            toggleActiveButton.setEnabled(false);
            toggleActiveButton.addActionListener(e -> toggleActiveStatus());

            footer.add(toggleActiveButton);
            footer.add(editButton);
            footer.add(addButton);

            add(footer, BorderLayout.SOUTH);
        }
    }

    public void loadData() {
        refreshButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> {
                    List<AcademicProgram> progs = (currentUser.getRole() == Role.ADMIN)
                            ? programService.getAllPrograms(currentUser)
                            : programService.getAllActivePrograms(currentUser);
                    List<String> schools = departmentService.getAllSchools(currentUser);
                    return new Object[]{progs, schools};
                },
                result -> {
                    @SuppressWarnings("unchecked")
                    List<AcademicProgram> progs = (List<AcademicProgram>) result[0];
                    @SuppressWarnings("unchecked")
                    List<String> schools = (List<String>) result[1];

                    allPrograms = progs;

                    // Update school combo
                    String curSchool = (String) schoolFilterCombo.getSelectedItem();
                    schoolFilterCombo.removeAllItems();
                    schoolFilterCombo.addItem("ALL");
                    for (String sch : schools) {
                        if (sch != null && !sch.trim().isEmpty()) {
                            schoolFilterCombo.addItem(sch.trim());
                        }
                    }
                    if (curSchool != null) schoolFilterCombo.setSelectedItem(curSchool);

                    applyFilter();
                    refreshButton.setEnabled(true);
                },
                error -> {
                    refreshButton.setEnabled(true);
                    SwingUtils.showError(this, "Failed to load academic programmes: " + error.getMessage());
                }
        );
    }

    private void applyFilter() {
        tableModel.setRowCount(0);
        String selectedLevel = (String) levelFilterCombo.getSelectedItem();
        String selectedSchool = (String) schoolFilterCombo.getSelectedItem();
        String query = searchField.getText().trim().toLowerCase();

        for (AcademicProgram p : allPrograms) {
            boolean levelMatch = (selectedLevel == null || "ALL".equalsIgnoreCase(selectedLevel) ||
                    (p.getLevel() != null && p.getLevel().equalsIgnoreCase(selectedLevel)));

            boolean schoolMatch = (selectedSchool == null || "ALL".equalsIgnoreCase(selectedSchool) ||
                    (p.getSchoolName() != null && p.getSchoolName().equalsIgnoreCase(selectedSchool)));

            boolean queryMatch = query.isEmpty() ||
                    (p.getCode() != null && p.getCode().toLowerCase().contains(query)) ||
                    (p.getName() != null && p.getName().toLowerCase().contains(query)) ||
                    (p.getDegree() != null && p.getDegree().toLowerCase().contains(query)) ||
                    (p.getDepartmentName() != null && p.getDepartmentName().toLowerCase().contains(query));

            if (levelMatch && schoolMatch && queryMatch) {
                tableModel.addRow(new Object[]{
                        p.getId(),
                        p.getCode(),
                        p.getName(),
                        p.getDegree(),
                        p.getLevel(),
                        p.getDepartmentName() != null ? p.getDepartmentName() : "Dept #" + p.getDepartmentId(),
                        p.getSchoolName() != null ? p.getSchoolName() : "—",
                        p.getDurationYears() + " Yrs",
                        p.getTotalSemesters() + (p.isResearch() ? " Terms" : " Sems"),
                        p.getCreditsRequired(),
                        p.isActive() ? "Active" : "Inactive"
                });
            }
        }

        selectedProgramId = null;
        if (editButton != null) editButton.setEnabled(false);
        if (toggleActiveButton != null) toggleActiveButton.setEnabled(false);
    }

    private void openAddDialog() {
        Window parent = SwingUtilities.getWindowAncestor(this);
        AcademicProgramDialog dialog = new AcademicProgramDialog(parent, currentUser, programService, departmentService, null, this::loadData);
        dialog.setVisible(true);
    }

    private void openEditDialog() {
        if (selectedProgramId == null) return;
        AcademicProgram program = null;
        for (AcademicProgram p : allPrograms) {
            if (p.getId() == selectedProgramId) {
                program = p;
                break;
            }
        }
        if (program == null) return;

        Window parent = SwingUtilities.getWindowAncestor(this);
        AcademicProgramDialog dialog = new AcademicProgramDialog(parent, currentUser, programService, departmentService, program, this::loadData);
        dialog.setVisible(true);
    }

    private void toggleActiveStatus() {
        if (selectedProgramId == null) return;

        AcademicProgram selected = null;
        for (AcademicProgram p : allPrograms) {
            if (p.getId() == selectedProgramId) {
                selected = p;
                break;
            }
        }
        if (selected == null) return;

        boolean newActive = !selected.isActive();
        final String progCode = selected.getCode();
        String action = newActive ? "activate" : "deactivate";

        boolean confirm = SwingUtils.showConfirm(this,
                "Are you sure you want to " + action + " programme: " + progCode + "?",
                "Confirm Programme Status Change");
        if (!confirm) return;

        toggleActiveButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> {
                    programService.toggleActive(currentUser, selectedProgramId, newActive);
                    return null;
                },
                unused -> {
                    toggleActiveButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Programme " + progCode + " successfully " + (newActive ? "activated" : "deactivated") + ".");
                    loadData();
                },
                error -> {
                    toggleActiveButton.setEnabled(true);
                    SwingUtils.showWarning(this, "Status Change Blocked:\n" + error.getMessage());
                }
        );
    }
}
