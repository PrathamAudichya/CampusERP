package com.college.ui.dialog;

import com.college.model.AcademicProgram;
import com.college.model.Department;
import com.college.model.ProgrammeSemester;
import com.college.model.User;
import com.college.service.AcademicProgramService;
import com.college.service.DepartmentService;
import com.college.ui.components.CampusIcons;
import com.college.ui.components.PrimaryButton;
import com.college.ui.components.SecondaryButton;
import com.college.ui.theme.CampusTheme;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Modal dialog for creating or editing an Academic Programme with live duration-to-semester
 * calculation, preview generation, and research milestone configuration.
 */
public class AcademicProgramDialog extends JDialog {

    private final User currentUser;
    private final AcademicProgramService programService;
    private final DepartmentService departmentService;
    private final AcademicProgram programToEdit;
    private final Runnable onSuccess;

    private JTextField codeField;
    private JTextField nameField;
    private JComboBox<String> degreeCombo;
    private JComboBox<String> levelCombo;
    private JComboBox<DepartmentItem> deptCombo;
    private JSpinner durationSpinner;
    private JLabel calculatedSemestersLabel;
    private JTextField creditsField;
    private JTextField regField;
    private JCheckBox researchCheckBox;
    private JTextField descField;

    private JTable semesterPreviewTable;
    private DefaultTableModel previewModel;

    public AcademicProgramDialog(Window parent, User currentUser, AcademicProgramService programService,
                                 DepartmentService departmentService, AcademicProgram programToEdit, Runnable onSuccess) {
        super(parent, programToEdit == null ? "Add Academic Programme" : "Edit Academic Programme", ModalityType.APPLICATION_MODAL);
        this.currentUser = currentUser;
        this.programService = programService;
        this.departmentService = departmentService;
        this.programToEdit = programToEdit;
        this.onSuccess = onSuccess;

        setSize(780, 620);
        setLocationRelativeTo(parent);
        setResizable(true);
        initUI();
        if (programToEdit != null) {
            populateExistingData();
        } else {
            updateSemesterPreview();
        }
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(CampusTheme.BG_CARD);
        root.setBorder(new EmptyBorder(16, 20, 16, 20));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel(programToEdit == null ? "CREATE ACADEMIC PROGRAMME" : "EDIT ACADEMIC PROGRAMME");
        title.setFont(CampusTheme.FONT_TITLE);
        title.setForeground(CampusTheme.PRIMARY);
        header.add(title, BorderLayout.WEST);
        root.add(header, BorderLayout.NORTH);

        // Center Split: Left = Form Fields, Right = Semester Preview
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.55);
        splitPane.setOpaque(false);
        splitPane.setBorder(null);

        // Form Card
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Code & Name
        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Code:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0;
        codeField = new JTextField(8);
        formPanel.add(codeField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("Programme Name:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1;
        nameField = new JTextField(18);
        formPanel.add(nameField, gbc);

        // Row 2: Department
        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("Department:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2;
        deptCombo = new JComboBox<>();
        loadDepartments();
        formPanel.add(deptCombo, gbc);

        // Row 3: Qualification Level
        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(new JLabel("Level:"), gbc);
        gbc.gridx = 1; gbc.gridy = 3;
        levelCombo = new JComboBox<>(new String[]{"UNDERGRADUATE", "POSTGRADUATE", "RESEARCH", "DIPLOMA"});
        levelCombo.addActionListener(e -> {
            boolean isRes = "RESEARCH".equals(levelCombo.getSelectedItem());
            researchCheckBox.setSelected(isRes);
            updateSemesterPreview();
        });
        formPanel.add(levelCombo, gbc);

        // Row 4: Degree / Qualification
        gbc.gridx = 0; gbc.gridy = 4;
        formPanel.add(new JLabel("Degree:"), gbc);
        gbc.gridx = 1; gbc.gridy = 4;
        degreeCombo = new JComboBox<>(new String[]{
                "B.Tech", "BCA", "BBA", "B.Com", "BA", "B.Sc", "BHM", "MBBS",
                "M.Tech", "MCA", "MBA", "M.Com", "MA", "M.Sc", "Ph.D.", "Postdoctoral"
        });
        degreeCombo.setEditable(true);
        formPanel.add(degreeCombo, gbc);

        // Row 5: Duration in Years & Calculated Semesters
        gbc.gridx = 0; gbc.gridy = 5;
        formPanel.add(new JLabel("Duration (Years):"), gbc);
        gbc.gridx = 1; gbc.gridy = 5;
        durationSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 10, 1));
        durationSpinner.addChangeListener(e -> updateSemesterPreview());
        formPanel.add(durationSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 6;
        formPanel.add(new JLabel("Semesters Derived:"), gbc);
        gbc.gridx = 1; gbc.gridy = 6;
        calculatedSemestersLabel = new JLabel("8 Semesters (Auto-calculated)");
        calculatedSemestersLabel.setFont(CampusTheme.FONT_BOLD);
        calculatedSemestersLabel.setForeground(CampusTheme.PRIMARY);
        formPanel.add(calculatedSemestersLabel, gbc);

        // Row 7: Credits & Regulation
        gbc.gridx = 0; gbc.gridy = 7;
        formPanel.add(new JLabel("Credits Required:"), gbc);
        gbc.gridx = 1; gbc.gridy = 7;
        creditsField = new JTextField("160", 8);
        formPanel.add(creditsField, gbc);

        gbc.gridx = 0; gbc.gridy = 8;
        formPanel.add(new JLabel("Regulation Scheme:"), gbc);
        gbc.gridx = 1; gbc.gridy = 8;
        regField = new JTextField("R2026", 8);
        formPanel.add(regField, gbc);

        // Row 9: Research Flag
        gbc.gridx = 0; gbc.gridy = 9;
        formPanel.add(new JLabel("Research Track:"), gbc);
        gbc.gridx = 1; gbc.gridy = 9;
        researchCheckBox = new JCheckBox("Structured Doctoral / Research Term Milestones");
        researchCheckBox.setOpaque(false);
        researchCheckBox.addActionListener(e -> updateSemesterPreview());
        formPanel.add(researchCheckBox, gbc);

        // Row 10: Description
        gbc.gridx = 0; gbc.gridy = 10;
        formPanel.add(new JLabel("Description:"), gbc);
        gbc.gridx = 1; gbc.gridy = 10;
        descField = new JTextField(18);
        formPanel.add(descField, gbc);

        splitPane.setLeftComponent(formPanel);

        // Right Preview Card
        JPanel previewCard = new JPanel(new BorderLayout(6, 6));
        previewCard.setOpaque(false);
        previewCard.setBorder(BorderFactory.createTitledBorder("Generated Semester Structure Preview"));

        String[] pCols = {"#", "Term / Semester Label", "Type", "Target Credits"};
        previewModel = new DefaultTableModel(pCols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return col == 1 || col == 3; }
        };
        semesterPreviewTable = SwingUtils.createStandardTable(previewModel);
        semesterPreviewTable.getColumnModel().getColumn(0).setMaxWidth(36);
        semesterPreviewTable.getColumnModel().getColumn(2).setPreferredWidth(110);
        semesterPreviewTable.getColumnModel().getColumn(3).setMaxWidth(90);

        JScrollPane pScroll = new JScrollPane(semesterPreviewTable);
        previewCard.add(pScroll, BorderLayout.CENTER);

        JLabel previewHint = new JLabel("<html><i>Labels and target credits in the table above can be fine-tuned before confirmation.</i></html>");
        previewHint.setFont(CampusTheme.FONT_SMALL);
        previewHint.setForeground(CampusTheme.TEXT_MUTED);
        previewCard.add(previewHint, BorderLayout.SOUTH);

        splitPane.setRightComponent(previewCard);
        root.add(splitPane, BorderLayout.CENTER);

        // Footer Buttons
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        footer.setOpaque(false);

        JButton cancelBtn = new SecondaryButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        footer.add(cancelBtn);

        JButton saveBtn = new PrimaryButton(programToEdit == null ? "CONFIRM & CREATE PROGRAMME" : "SAVE CHANGES");
        saveBtn.addActionListener(e -> saveProgram());
        footer.add(saveBtn);

        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void loadDepartments() {
        List<Department> depts = departmentService.getAllActiveDepartments(currentUser);
        deptCombo.removeAllItems();
        for (Department d : depts) {
            deptCombo.addItem(new DepartmentItem(d));
        }
    }

    private void updateSemesterPreview() {
        int duration = (Integer) durationSpinner.getValue();
        boolean isRes = researchCheckBox.isSelected();
        int totalSem = programService.calculateDefaultSemesterCount(duration);

        calculatedSemestersLabel.setText(totalSem + " " + (isRes ? "Research Terms" : "Semesters") + " (1 yr = 2 sem)");

        List<ProgrammeSemester> generated = programService.generateDefaultSemesters(duration, isRes);
        previewModel.setRowCount(0);

        for (ProgrammeSemester s : generated) {
            previewModel.addRow(new Object[]{
                    s.getSemesterNumber(),
                    s.getSemesterLabel(),
                    s.getTermType(),
                    s.getCreditsTarget()
            });
        }
    }

    private void populateExistingData() {
        codeField.setText(programToEdit.getCode());
        codeField.setEnabled(false); // Code immutable during edit
        nameField.setText(programToEdit.getName());
        degreeCombo.setSelectedItem(programToEdit.getDegree());
        levelCombo.setSelectedItem(programToEdit.getLevel());
        durationSpinner.setValue(programToEdit.getDurationYears());
        creditsField.setText(String.valueOf(programToEdit.getCreditsRequired()));
        regField.setText(programToEdit.getAcademicRegulation());
        researchCheckBox.setSelected(programToEdit.isResearch());
        descField.setText(programToEdit.getDescription() != null ? programToEdit.getDescription() : "");

        for (int i = 0; i < deptCombo.getItemCount(); i++) {
            if (deptCombo.getItemAt(i).dept.getId() == programToEdit.getDepartmentId()) {
                deptCombo.setSelectedIndex(i);
                break;
            }
        }

        // Load existing semesters
        List<ProgrammeSemester> sems = programService.getSemestersByProgram(currentUser, programToEdit.getId());
        if (sems != null && !sems.isEmpty()) {
            previewModel.setRowCount(0);
            for (ProgrammeSemester s : sems) {
                previewModel.addRow(new Object[]{
                        s.getSemesterNumber(),
                        s.getSemesterLabel(),
                        s.getTermType(),
                        s.getCreditsTarget()
                });
            }
            calculatedSemestersLabel.setText(sems.size() + " Semesters (Configured)");
        } else {
            updateSemesterPreview();
        }
    }

    private void saveProgram() {
        String code = codeField.getText().trim();
        String name = nameField.getText().trim();
        String degree = (String) degreeCombo.getSelectedItem();
        String level = (String) levelCombo.getSelectedItem();
        DepartmentItem deptItem = (DepartmentItem) deptCombo.getSelectedItem();

        if (code.isEmpty() || name.isEmpty() || degree == null || degree.trim().isEmpty() || deptItem == null) {
            SwingUtils.showWarning(this, "Code, Name, Degree, and Department are strictly required.");
            return;
        }

        int duration = (Integer) durationSpinner.getValue();
        int totalSem = previewModel.getRowCount();

        int parsedCredits = 160;
        try {
            parsedCredits = Integer.parseInt(creditsField.getText().trim());
        } catch (NumberFormatException ignored) {}
        final int credits = parsedCredits;

        String reg = regField.getText().trim();
        boolean isRes = researchCheckBox.isSelected();
        String desc = descField.getText().trim();

        // Extract customized semesters from preview table
        List<ProgrammeSemester> customSems = new ArrayList<>();
        for (int i = 0; i < previewModel.getRowCount(); i++) {
            int num = (Integer) previewModel.getValueAt(i, 0);
            String label = (String) previewModel.getValueAt(i, 1);
            String type = (String) previewModel.getValueAt(i, 2);
            int cred = 20;
            try {
                cred = Integer.parseInt(previewModel.getValueAt(i, 3).toString().trim());
            } catch (Exception ignored) {}
            customSems.add(new ProgrammeSemester(0, num, label, type, cred));
        }

        SwingUtils.executeAsync(
                this,
                () -> {
                    if (programToEdit == null) {
                        AcademicProgram p = new AcademicProgram(
                                deptItem.dept.getId(), name, code, degree, level, duration, totalSem);
                        p.setCreditsRequired(credits);
                        p.setAcademicRegulation(reg);
                        p.setResearch(isRes);
                        p.setDescription(desc);
                        return programService.createProgram(currentUser, p, customSems);
                    } else {
                        programToEdit.setName(name);
                        programToEdit.setDegree(degree);
                        programToEdit.setLevel(level);
                        programToEdit.setDepartmentId(deptItem.dept.getId());
                        programToEdit.setDurationYears(duration);
                        programToEdit.setTotalSemesters(totalSem);
                        programToEdit.setCreditsRequired(credits);
                        programToEdit.setAcademicRegulation(reg);
                        programToEdit.setResearch(isRes);
                        programToEdit.setDescription(desc);
                        AcademicProgram updated = programService.updateProgram(currentUser, programToEdit);
                        programService.regenerateSemesters(currentUser, updated.getId(), customSems);
                        return updated;
                    }
                },
                saved -> {
                    JOptionPane.showMessageDialog(this,
                            "Academic Programme successfully saved:\n" +
                            "• " + saved.getCode() + " - " + saved.getName() + " (" + saved.getDegree() + ")\n" +
                            "• Semesters configured: " + totalSem,
                            "Programme Saved", JOptionPane.INFORMATION_MESSAGE);
                    if (onSuccess != null) onSuccess.run();
                    dispose();
                },
                error -> SwingUtils.showError(this, "Failed to save programme: " + error.getMessage())
        );
    }

    private static class DepartmentItem {
        final Department dept;
        DepartmentItem(Department dept) { this.dept = dept; }
        @Override
        public String toString() { return dept.getCode() + " - " + dept.getName() + " (" + dept.getSchool() + ")"; }
    }
}
