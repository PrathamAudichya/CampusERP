package com.college.ui.panel;

import com.college.model.AcademicProgram;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.StudentCohort;
import com.college.model.StudentProgressionHistory;
import com.college.model.User;
import com.college.service.AcademicProgramService;
import com.college.service.StudentCohortService;
import com.college.service.StudentProgressionService;
import com.college.service.StudentService;
import com.college.ui.theme.CampusTheme;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Management UI for Student Cohorts, Academic Admissions linking,
 * controlled semester progression (Promote, Hold, Repeat), and audit trail inspection.
 */
public class CohortProgressionPanel extends JPanel {

    private final User currentUser;
    private final StudentCohortService cohortService;
    private final StudentProgressionService progressionService;
    private final AcademicProgramService programService;
    private final StudentService studentService;

    private JTable cohortTable;
    private DefaultTableModel cohortModel;

    private JTable studentTable;
    private DefaultTableModel studentModel;

    private JButton createCohortButton;
    private JButton promoteButton;
    private JButton holdButton;
    private JButton repeatButton;
    private JButton viewHistoryButton;
    private JButton reconcileButton;
    private JButton refreshButton;

    private Integer selectedCohortId = null;
    private Integer selectedStudentId = null;

    public CohortProgressionPanel(User currentUser) {
        this(currentUser, new StudentCohortService(), new StudentProgressionService(),
                new AcademicProgramService(), new StudentService());
    }

    public CohortProgressionPanel(User currentUser, StudentCohortService cohortService,
                                  StudentProgressionService progressionService,
                                  AcademicProgramService programService, StudentService studentService) {
        this.currentUser = currentUser;
        this.cohortService = cohortService;
        this.progressionService = progressionService;
        this.programService = programService;
        this.studentService = studentService;
        initUI();
        loadCohorts();
    }

    private void initUI() {
        setLayout(new BorderLayout(12, 12));
        setBackground(SwingUtils.BG_LIGHT);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Header Panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JLabel titleLabel = new JLabel("Academic Cohorts & Student Progression");
        titleLabel.setFont(SwingUtils.TITLE_FONT);
        titleLabel.setForeground(SwingUtils.PRIMARY_COLOR);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerButtons.setOpaque(false);

        if (currentUser.getRole() == Role.ADMIN) {
            reconcileButton = SwingUtils.createSecondaryButton("Reconcile Legacy Students");
            reconcileButton.setToolTipText("Auto-maps legacy unassigned students to their department's primary programme and cohort");
            reconcileButton.addActionListener(e -> reconcileLegacyRecords());
            headerButtons.add(reconcileButton);

            createCohortButton = SwingUtils.createPrimaryButton("Create New Cohort");
            createCohortButton.addActionListener(e -> openCreateCohortDialog());
            headerButtons.add(createCohortButton);
        }

        refreshButton = SwingUtils.createSecondaryButton("Refresh");
        refreshButton.addActionListener(e -> loadCohorts());
        headerButtons.add(refreshButton);

        headerPanel.add(headerButtons, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Center Split: Top = Cohorts Table, Bottom = Cohort Roster & Progression Controls
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.45);
        splitPane.setOpaque(false);

        // Cohort Panel
        JPanel cohortPanel = new JPanel(new BorderLayout(6, 6));
        cohortPanel.setOpaque(false);
        cohortPanel.setBorder(BorderFactory.createTitledBorder("Active Academic Cohorts (Class Groups)"));

        String[] cohortCols = {"ID", "Cohort Code", "Academic Programme", "Admission Year", "Expected Graduation", "Current Semester", "Enrolled Students", "Status"};
        cohortModel = new DefaultTableModel(cohortCols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        cohortTable = SwingUtils.createStandardTable(cohortModel);

        cohortTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && cohortTable.getSelectedRow() != -1) {
                int row = cohortTable.getSelectedRow();
                selectedCohortId = (Integer) cohortModel.getValueAt(row, 0);
                loadCohortStudents(selectedCohortId);
            }
        });

        cohortPanel.add(new JScrollPane(cohortTable), BorderLayout.CENTER);
        splitPane.setTopComponent(cohortPanel);

        // Student Roster Panel
        JPanel rosterPanel = new JPanel(new BorderLayout(6, 6));
        rosterPanel.setOpaque(false);
        rosterPanel.setBorder(BorderFactory.createTitledBorder("Cohort Student Roster & Semester Progression"));

        String[] studentCols = {"Student ID", "Roll Number", "Full Name", "Email", "Department", "Current Semester", "Status"};
        studentModel = new DefaultTableModel(studentCols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        studentTable = SwingUtils.createStandardTable(studentModel);

        studentTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && studentTable.getSelectedRow() != -1) {
                int row = studentTable.getSelectedRow();
                selectedStudentId = (Integer) studentModel.getValueAt(row, 0);
                boolean hasSelection = selectedStudentId != null;

                if (currentUser.getRole() == Role.ADMIN) {
                    promoteButton.setEnabled(hasSelection);
                    holdButton.setEnabled(hasSelection);
                    repeatButton.setEnabled(hasSelection);
                    viewHistoryButton.setEnabled(hasSelection);
                }
            }
        });

        rosterPanel.add(new JScrollPane(studentTable), BorderLayout.CENTER);

        // Action Buttons Row
        if (currentUser.getRole() == Role.ADMIN) {
            JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
            actionRow.setOpaque(false);

            viewHistoryButton = SwingUtils.createSecondaryButton("View Progression Audit");
            viewHistoryButton.setEnabled(false);
            viewHistoryButton.addActionListener(e -> showProgressionHistory());

            holdButton = SwingUtils.createSecondaryButton("Hold Promotion");
            holdButton.setEnabled(false);
            holdButton.addActionListener(e -> processHold());

            repeatButton = SwingUtils.createSecondaryButton("Repeat Semester");
            repeatButton.setEnabled(false);
            repeatButton.addActionListener(e -> processRepeat());

            promoteButton = SwingUtils.createPrimaryButton("Promote to Next Semester");
            promoteButton.setEnabled(false);
            promoteButton.addActionListener(e -> processPromotion());

            actionRow.add(viewHistoryButton);
            actionRow.add(repeatButton);
            actionRow.add(holdButton);
            actionRow.add(promoteButton);

            rosterPanel.add(actionRow, BorderLayout.SOUTH);
        }

        splitPane.setBottomComponent(rosterPanel);
        add(splitPane, BorderLayout.CENTER);
    }

    public void loadCohorts() {
        refreshButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> cohortService.getAllActiveCohorts(currentUser),
                cohorts -> {
                    cohortModel.setRowCount(0);
                    for (StudentCohort c : cohorts) {
                        cohortModel.addRow(new Object[]{
                                c.getId(),
                                c.getCohortCode(),
                                c.getProgramName() != null ? c.getProgramName() : "Program #" + c.getProgramId(),
                                c.getAdmissionYear(),
                                c.getExpectedGraduationYear(),
                                "Semester " + c.getCurrentSemester(),
                                c.getStudentCount() + " Students",
                                c.getStatus()
                        });
                    }
                    selectedCohortId = null;
                    studentModel.setRowCount(0);
                    refreshButton.setEnabled(true);
                },
                error -> {
                    refreshButton.setEnabled(true);
                    SwingUtils.showError(this, "Failed to load cohorts: " + error.getMessage());
                }
        );
    }

    private void loadCohortStudents(int cohortId) {
        SwingUtils.executeAsync(
                this,
                () -> cohortService.getCohortStudents(currentUser, cohortId),
                students -> {
                    studentModel.setRowCount(0);
                    for (Student s : students) {
                        studentModel.addRow(new Object[]{
                                s.getId(),
                                s.getStudentId(),
                                s.getFullName(),
                                s.getEmail(),
                                s.getDepartmentName() != null ? s.getDepartmentName() : "Dept #" + s.getDepartmentId(),
                                "Semester " + s.getSemester(),
                                s.isActive() ? "Active" : "Inactive"
                        });
                    }
                    selectedStudentId = null;
                    if (promoteButton != null) promoteButton.setEnabled(false);
                    if (holdButton != null) holdButton.setEnabled(false);
                    if (repeatButton != null) repeatButton.setEnabled(false);
                    if (viewHistoryButton != null) viewHistoryButton.setEnabled(false);
                },
                error -> SwingUtils.showError(this, "Failed to load students for cohort: " + error.getMessage())
        );
    }

    private void openCreateCohortDialog() {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Create Student Cohort", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(440, 320);
        dialog.setLocationRelativeTo(this);

        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(CampusTheme.BG_CARD);
        p.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        p.add(new JLabel("Programme:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0;
        JComboBox<ProgramItem> progCombo = new JComboBox<>();
        List<AcademicProgram> progs = programService.getAllActivePrograms(currentUser);
        for (AcademicProgram pr : progs) progCombo.addItem(new ProgramItem(pr));
        p.add(progCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        p.add(new JLabel("Admission Year:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1;
        JTextField admField = new JTextField("2026-2027", 12);
        p.add(admField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        p.add(new JLabel("Current Semester:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2;
        JSpinner semSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1));
        p.add(semSpinner, gbc);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        btnPanel.setOpaque(false);
        JButton cancel = SwingUtils.createSecondaryButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());
        JButton save = SwingUtils.createPrimaryButton("Create Cohort");
        save.addActionListener(e -> {
            ProgramItem pi = (ProgramItem) progCombo.getSelectedItem();
            if (pi == null) return;
            String adm = admField.getText().trim();
            if (adm.isEmpty()) {
                SwingUtils.showWarning(dialog, "Admission year is required.");
                return;
            }

            SwingUtils.executeAsync(
                    dialog,
                    () -> cohortService.createCohort(currentUser, pi.program.getId(), adm),
                    saved -> {
                        dialog.dispose();
                        loadCohorts();
                        SwingUtils.showInfo(this, "Cohort successfully created: " + saved.getCohortCode());
                    },
                    err -> SwingUtils.showError(dialog, "Creation Failed: " + err.getMessage())
            );
        });

        btnPanel.add(cancel);
        btnPanel.add(save);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        p.add(btnPanel, gbc);

        dialog.setContentPane(p);
        dialog.setVisible(true);
    }

    private void processPromotion() {
        if (selectedStudentId == null) return;
        String reason = JOptionPane.showInputDialog(this,
                "Enter mandatory promotion remarks / reason:\n(e.g. 'Passed all semester courses successfully')",
                "Promote Student", JOptionPane.QUESTION_MESSAGE);
        if (reason == null || reason.trim().isEmpty()) {
            SwingUtils.showWarning(this, "Progression cancelled: reason is mandatory.");
            return;
        }

        promoteButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> progressionService.promoteStudent(currentUser, selectedStudentId, "2026-2027", reason.trim()),
                record -> {
                    promoteButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Student promoted: " + record.toString());
                    if (selectedCohortId != null) loadCohortStudents(selectedCohortId);
                },
                error -> {
                    promoteButton.setEnabled(true);
                    SwingUtils.showError(this, "Promotion Failed: " + error.getMessage());
                }
        );
    }

    private void processHold() {
        if (selectedStudentId == null) return;
        String reason = JOptionPane.showInputDialog(this,
                "Enter reason for holding semester promotion:\n(e.g. 'Attendance shortage in Core Course CS101')",
                "Hold Promotion", JOptionPane.WARNING_MESSAGE);
        if (reason == null || reason.trim().isEmpty()) {
            SwingUtils.showWarning(this, "Action cancelled: reason is mandatory.");
            return;
        }

        holdButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> progressionService.holdPromotion(currentUser, selectedStudentId, "2026-2027", reason.trim()),
                record -> {
                    holdButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Student held in current semester: " + record.toString());
                    if (selectedCohortId != null) loadCohortStudents(selectedCohortId);
                },
                error -> {
                    holdButton.setEnabled(true);
                    SwingUtils.showError(this, "Hold Action Failed: " + error.getMessage());
                }
        );
    }

    private void processRepeat() {
        if (selectedStudentId == null) return;
        String reason = JOptionPane.showInputDialog(this,
                "Enter reason for repeating semester:\n(e.g. 'Medical leave semester break repeat')",
                "Repeat Semester", JOptionPane.WARNING_MESSAGE);
        if (reason == null || reason.trim().isEmpty()) {
            SwingUtils.showWarning(this, "Action cancelled: reason is mandatory.");
            return;
        }

        repeatButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> progressionService.repeatSemester(currentUser, selectedStudentId, "2026-2027", reason.trim()),
                record -> {
                    repeatButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Student semester repeat recorded: " + record.toString());
                    if (selectedCohortId != null) loadCohortStudents(selectedCohortId);
                },
                error -> {
                    repeatButton.setEnabled(true);
                    SwingUtils.showError(this, "Repeat Action Failed: " + error.getMessage());
                }
        );
    }

    private void showProgressionHistory() {
        if (selectedStudentId == null) return;

        SwingUtils.executeAsync(
                this,
                () -> progressionService.getStudentProgressionHistory(currentUser, selectedStudentId),
                history -> {
                    JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Student Progression Audit Trail", Dialog.ModalityType.APPLICATION_MODAL);
                    dialog.setSize(680, 400);
                    dialog.setLocationRelativeTo(this);

                    String[] cols = {"Previous Sem", "New Sem", "Action", "Academic Year", "Authorized By", "Reason", "Timestamp"};
                    DefaultTableModel m = new DefaultTableModel(cols, 0);
                    for (StudentProgressionHistory h : history) {
                        m.addRow(new Object[]{
                                "Sem " + h.getPreviousSemester(),
                                "Sem " + h.getNewSemester(),
                                h.getAction(),
                                h.getAcademicYear(),
                                h.getPerformedByName() != null ? h.getPerformedByName() : "Admin #" + h.getPerformedBy(),
                                h.getReason(),
                                h.getCreatedAt()
                        });
                    }

                    JTable t = SwingUtils.createStandardTable(m);
                    JPanel root = new JPanel(new BorderLayout(8, 8));
                    root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
                    root.add(new JScrollPane(t), BorderLayout.CENTER);

                    JButton close = SwingUtils.createSecondaryButton("Close");
                    close.addActionListener(e -> dialog.dispose());
                    JPanel bp = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                    bp.add(close);
                    root.add(bp, BorderLayout.SOUTH);

                    dialog.setContentPane(root);
                    dialog.setVisible(true);
                },
                error -> SwingUtils.showError(this, "Failed to load audit history: " + error.getMessage())
        );
    }

    private void reconcileLegacyRecords() {
        boolean confirm = SwingUtils.showConfirm(this,
                "Scan and reconcile legacy student records?\n\n" +
                "• Finds active students lacking programme or cohort mappings.\n" +
                "• Maps them to their department's primary degree programme.\n" +
                "• Creates default cohorts and updates academic profiles.\n" +
                "• All reconciliation actions are logged in the audit trail.",
                "Confirm Legacy Student Reconciliation");
        if (!confirm) return;

        reconcileButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> cohortService.reconcileLegacyStudents(currentUser),
                count -> {
                    reconcileButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Reconciliation Complete:\n" + count + " student records updated with programme and cohort mappings.");
                    loadCohorts();
                },
                error -> {
                    reconcileButton.setEnabled(true);
                    SwingUtils.showError(this, "Reconciliation Failed: " + error.getMessage());
                }
        );
    }

    private static class ProgramItem {
        final AcademicProgram program;
        ProgramItem(AcademicProgram program) { this.program = program; }
        @Override
        public String toString() { return program.getCode() + " - " + program.getName() + " (" + program.getDegree() + ")"; }
    }
}

