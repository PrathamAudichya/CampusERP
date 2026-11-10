package com.college.ui.dialog;

import com.college.model.AttendanceCorrection;
import com.college.model.AttendanceSession;
import com.college.model.User;
import com.college.service.AttendanceService;
import com.college.service.AttendanceSessionService;
import com.college.service.AttendanceSessionService.SessionRosterEntry;
import com.college.ui.components.CampusIcons;
import com.college.ui.components.PrimaryButton;
import com.college.ui.components.SecondaryButton;
import com.college.ui.theme.CampusTheme;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Faculty review dialog allowing inspection of student check-in submissions,
 * manual corrections for legitimate exceptions with mandatory justification remarks,
 * and atomic confirmation of final present/absent ledger status.
 */
public class ReviewAttendanceDialog extends JDialog {

    private final AttendanceSession session;
    private final AttendanceSessionService sessionService;
    private final User currentUser;
    private final Runnable onFinalize;

    private JTable rosterTable;
    private DefaultTableModel rosterModel;
    private JLabel summaryLabel;
    private JButton finalizeButton;
    private List<SessionRosterEntry> currentRoster;

    public ReviewAttendanceDialog(Window parent, AttendanceSession session, AttendanceSessionService sessionService,
                                  User currentUser, Runnable onFinalize) {
        super(parent, "Review & Finalize Attendance — Session #" + session.getId(), ModalityType.APPLICATION_MODAL);
        this.session = session;
        this.sessionService = sessionService;
        this.currentUser = currentUser;
        this.onFinalize = onFinalize;

        setSize(850, 560);
        setLocationRelativeTo(parent);
        initUI();
        loadRoster();
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(CampusTheme.BG_CARD);
        root.setBorder(new EmptyBorder(16, 20, 16, 20));

        // Header Banner
        JPanel header = new JPanel(new BorderLayout(8, 4));
        header.setOpaque(false);

        JLabel title = new JLabel("REVIEW ATTENDANCE ROSTER");
        title.setFont(CampusTheme.FONT_TITLE);
        title.setForeground(CampusTheme.PRIMARY);
        header.add(title, BorderLayout.NORTH);

        JLabel sub = new JLabel(String.format(
                "Course #%d • Session #%s • Verification Code: %s • Date: %s",
                session.getCourseId(), session.getSessionNumber(), session.getSessionCode(), session.getSessionDate()
        ));
        sub.setFont(CampusTheme.FONT_SMALL);
        sub.setForeground(CampusTheme.TEXT_SECONDARY);
        header.add(sub, BorderLayout.CENTER);

        JLabel instruction = new JLabel("<html><i>Students who completed check-in are proposed as <b>PRESENT</b>. Missing students are proposed as <b>ABSENT</b>.<br/>" +
                "Any status changes require mandatory remarks/justification.</i></html>");
        instruction.setFont(CampusTheme.FONT_SMALL);
        instruction.setForeground(CampusTheme.TEXT_MUTED);
        header.add(instruction, BorderLayout.SOUTH);

        root.add(header, BorderLayout.NORTH);

        // Center Table
        String[] cols = {"Enrollment ID", "Student ID", "Roll Number", "Student Name", "Check-In Time", "Check-In Status", "Final Status", "Mandatory Remarks"};
        rosterModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return col == 6 || col == 7;
            }
        };

        rosterTable = new JTable(rosterModel);
        rosterTable.setFont(SwingUtils.REGULAR_FONT);
        rosterTable.setRowHeight(26);
        rosterTable.getTableHeader().setFont(SwingUtils.HEADER_FONT);

        // Hide Enrollment ID (col 0) and Student ID (col 1)
        rosterTable.getColumnModel().getColumn(0).setMinWidth(0);
        rosterTable.getColumnModel().getColumn(0).setMaxWidth(0);
        rosterTable.getColumnModel().getColumn(0).setWidth(0);
        rosterTable.getColumnModel().getColumn(1).setMinWidth(0);
        rosterTable.getColumnModel().getColumn(1).setMaxWidth(0);
        rosterTable.getColumnModel().getColumn(1).setWidth(0);

        // Status column editor
        JComboBox<String> statusCombo = new JComboBox<>(new String[]{"PRESENT", "ABSENT", "LATE"});
        rosterTable.getColumnModel().getColumn(6).setCellEditor(new DefaultCellEditor(statusCombo));

        // Custom renderer for Check-In Status (Col 5)
        rosterTable.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if ("CHECKED IN".equals(value)) {
                    setForeground(SwingUtils.SUCCESS_COLOR);
                    setFont(CampusTheme.FONT_BOLD);
                } else {
                    setForeground(SwingUtils.MUTED_COLOR);
                    setFont(CampusTheme.FONT_BODY);
                }
                return c;
            }
        });

        rosterModel.addTableModelListener(e -> updateSummaryMetrics());

        JScrollPane sp = new JScrollPane(rosterTable);
        sp.setBorder(BorderFactory.createTitledBorder("Enrolled Class Roster"));
        root.add(sp, BorderLayout.CENTER);

        // South Summary & Action Bar
        JPanel south = new JPanel(new BorderLayout(10, 10));
        south.setOpaque(false);

        summaryLabel = new JLabel("Calculating roster metrics...");
        summaryLabel.setFont(CampusTheme.FONT_SMALL_BOLD);
        summaryLabel.setForeground(CampusTheme.PRIMARY);
        south.add(summaryLabel, BorderLayout.WEST);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnBar.setOpaque(false);

        JButton cancelBtn = new SecondaryButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        btnBar.add(cancelBtn);

        finalizeButton = new PrimaryButton("Confirm Finalize Attendance");
        finalizeButton.setPreferredSize(new Dimension(220, 36));
        finalizeButton.addActionListener(e -> performFinalization());
        btnBar.add(finalizeButton);

        south.add(btnBar, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void loadRoster() {
        SwingUtils.executeAsync(
                this,
                () -> sessionService.getSessionRoster(currentUser, session.getId()),
                roster -> {
                    this.currentRoster = roster;
                    rosterModel.setRowCount(0);
                    for (SessionRosterEntry r : roster) {
                        rosterModel.addRow(new Object[]{
                                r.getEnrollmentId(),
                                r.getStudentId(),
                                r.getStudentRegId(),
                                r.getStudentName(),
                                r.getCheckInTime(),
                                r.getSubmissionStatus(),
                                r.getCurrentStatus(),
                                r.getRemarks()
                        });
                    }
                    updateSummaryMetrics();
                },
                error -> SwingUtils.showError(this, "Failed to load session roster: " + error.getMessage())
        );
    }

    private void updateSummaryMetrics() {
        int total = rosterModel.getRowCount();
        if (total == 0) {
            summaryLabel.setText("No students enrolled in this course.");
            return;
        }

        int present = 0;
        int absent = 0;
        int late = 0;

        for (int i = 0; i < total; i++) {
            String st = (String) rosterModel.getValueAt(i, 6);
            if ("PRESENT".equalsIgnoreCase(st)) present++;
            else if ("LATE".equalsIgnoreCase(st)) late++;
            else absent++;
        }

        double pct = AttendanceService.calculatePercentage(present, late, total);
        summaryLabel.setText(String.format(
                "Total Students: %d   |   Present: %d   |   Late (0.5x): %d   |   Absent: %d   |   Attendance Rate: %.1f%%",
                total, present, late, absent, pct
        ));
        summaryLabel.setForeground(pct >= 75.0 ? SwingUtils.SUCCESS_COLOR : SwingUtils.DANGER_COLOR);
    }

    private void performFinalization() {
        if (rosterTable.isEditing()) {
            rosterTable.getCellEditor().stopCellEditing();
        }

        int total = rosterModel.getRowCount();
        Map<Integer, AttendanceCorrection> corrections = new HashMap<>();

        for (int i = 0; i < total; i++) {
            int enrId = (Integer) rosterModel.getValueAt(i, 0);
            int stuId = (Integer) rosterModel.getValueAt(i, 1);
            String regId = (String) rosterModel.getValueAt(i, 2);
            String name = (String) rosterModel.getValueAt(i, 3);
            String subStatus = (String) rosterModel.getValueAt(i, 5);
            String finalStatus = (String) rosterModel.getValueAt(i, 6);
            String remarks = (String) rosterModel.getValueAt(i, 7);

            String proposedDefault = "CHECKED IN".equalsIgnoreCase(subStatus) ? "PRESENT" : "ABSENT";

            // If status is changed from default check-in, remarks are strictly mandatory
            if (!proposedDefault.equalsIgnoreCase(finalStatus)) {
                if (remarks == null || remarks.trim().isEmpty()) {
                    SwingUtils.showWarning(this, String.format(
                            "Mandatory remarks/reason required for %s (%s):\nStatus manually changed from %s to %s.",
                            name, regId, proposedDefault, finalStatus));
                    rosterTable.setRowSelectionInterval(i, i);
                    return;
                }
            }

            corrections.put(stuId, new AttendanceCorrection(stuId, enrId, proposedDefault, finalStatus, remarks != null ? remarks.trim() : ""));
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to finalize this attendance session?\n\n" +
                "• All records will be committed to the official academic ledger.\n" +
                "• Session will be marked FINALIZED and locked against further submissions.\n" +
                "• Changes will be logged in the system audit trail.",
                "Confirm Attendance Finalization",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        finalizeButton.setEnabled(false);

        SwingUtils.executeAsync(
                this,
                () -> sessionService.finalizeSession(currentUser, session.getId(), corrections),
                counts -> {
                    SwingUtils.showInfo(this, String.format(
                            "Attendance Session Finalized Successfully!\n\n" +
                            "Total Enrolled Students : %d\n" +
                            "Marked PRESENT          : %d\n" +
                            "Marked LATE (0.5x)      : %d\n" +
                            "Marked ABSENT           : %d",
                            counts[3], counts[0], counts[2], counts[1]
                    ));
                    if (onFinalize != null) onFinalize.run();
                    dispose();
                },
                error -> {
                    finalizeButton.setEnabled(true);
                    SwingUtils.showError(this, "Failed to finalize session: " + error.getMessage());
                }
        );
    }
}
