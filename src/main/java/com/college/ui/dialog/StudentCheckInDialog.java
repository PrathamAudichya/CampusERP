package com.college.ui.dialog;

import com.college.model.AttendanceSession;
import com.college.model.AttendanceSubmission;
import com.college.model.User;
import com.college.service.AttendanceSessionService;
import com.college.service.AttendanceSessionService.ActiveSessionInfo;
import com.college.ui.components.CampusIcons;
import com.college.ui.components.PrimaryButton;
import com.college.ui.components.SecondaryButton;
import com.college.ui.theme.CampusTheme;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Modal dialog enabling students to perform self-service attendance check-in
 * by selecting an active session and entering the instructor's 6-digit verification code.
 */
public class StudentCheckInDialog extends JDialog {

    private final User currentUser;
    private final AttendanceSessionService sessionService;
    private final Runnable onCheckInSuccess;

    private JComboBox<SessionItem> sessionCombo;
    private JLabel sessionDetailsLabel;
    private JTextField codeField;
    private JLabel statusFeedbackLabel;
    private JButton submitButton;
    private List<ActiveSessionInfo> activeSessions;

    public StudentCheckInDialog(Window parent, User currentUser, AttendanceSessionService sessionService, Runnable onCheckInSuccess) {
        super(parent, "Student Attendance Check-In", ModalityType.APPLICATION_MODAL);
        this.currentUser = currentUser;
        this.sessionService = sessionService;
        this.onCheckInSuccess = onCheckInSuccess;

        setSize(520, 420);
        setLocationRelativeTo(parent);
        setResizable(false);
        initUI();
        loadActiveSessions();
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBackground(CampusTheme.BG_CARD);
        root.setBorder(new EmptyBorder(20, 24, 20, 24));

        // Header
        JPanel header = new JPanel(new BorderLayout(6, 6));
        header.setOpaque(false);

        JLabel title = new JLabel("STUDENT ATTENDANCE CHECK-IN");
        title.setFont(CampusTheme.FONT_TITLE);
        title.setForeground(CampusTheme.PRIMARY);
        header.add(title, BorderLayout.NORTH);

        JLabel sub = new JLabel("Select an active class session and enter the 6-digit verification code.");
        sub.setFont(CampusTheme.FONT_SMALL);
        sub.setForeground(CampusTheme.TEXT_SECONDARY);
        header.add(sub, BorderLayout.SOUTH);
        root.add(header, BorderLayout.NORTH);

        // Center Content
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);

        // Session Selector Row
        JPanel comboRow = new JPanel(new BorderLayout(8, 4));
        comboRow.setOpaque(false);
        JLabel selLbl = new JLabel("Active Class Session:");
        selLbl.setFont(CampusTheme.FONT_BOLD);
        comboRow.add(selLbl, BorderLayout.NORTH);

        sessionCombo = new JComboBox<>();
        sessionCombo.setFont(CampusTheme.FONT_BODY);
        sessionCombo.addActionListener(e -> updateSelectedSessionDetails());
        comboRow.add(sessionCombo, BorderLayout.CENTER);
        center.add(comboRow);

        center.add(Box.createVerticalStrut(10));

        // Session Detail Card
        JPanel detailCard = new JPanel(new BorderLayout());
        detailCard.setBackground(CampusTheme.BG_MAIN);
        detailCard.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(CampusTheme.BORDER, 1),
                new EmptyBorder(10, 12, 10, 12)
        ));
        sessionDetailsLabel = new JLabel("Loading active sessions...");
        sessionDetailsLabel.setFont(CampusTheme.FONT_SMALL);
        sessionDetailsLabel.setForeground(CampusTheme.TEXT_PRIMARY);
        detailCard.add(sessionDetailsLabel, BorderLayout.CENTER);
        center.add(detailCard);

        center.add(Box.createVerticalStrut(14));

        // Code Entry Panel
        JPanel codePanel = new JPanel();
        codePanel.setLayout(new BoxLayout(codePanel, BoxLayout.Y_AXIS));
        codePanel.setOpaque(false);

        JLabel enterCodeLbl = new JLabel("Enter 6-Digit Verification Code:");
        enterCodeLbl.setFont(CampusTheme.FONT_BOLD);
        enterCodeLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        codePanel.add(enterCodeLbl);
        codePanel.add(Box.createVerticalStrut(8));

        codeField = new JTextField(8);
        codeField.setFont(new Font("Consolas", Font.BOLD, 28));
        codeField.setHorizontalAlignment(JTextField.CENTER);
        codeField.setMaximumSize(new Dimension(200, 48));
        codeField.setAlignmentX(Component.CENTER_ALIGNMENT);
        codePanel.add(codeField);

        center.add(codePanel);

        center.add(Box.createVerticalStrut(10));

        // Status Feedback
        statusFeedbackLabel = new JLabel(" ", SwingConstants.CENTER);
        statusFeedbackLabel.setFont(CampusTheme.FONT_SMALL_BOLD);
        statusFeedbackLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(statusFeedbackLabel);

        root.add(center, BorderLayout.CENTER);

        // Footer Buttons
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setOpaque(false);

        JButton cancelBtn = new SecondaryButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        footer.add(cancelBtn);

        submitButton = new PrimaryButton("SUBMIT ATTENDANCE");
        submitButton.setPreferredSize(new Dimension(180, 36));
        submitButton.addActionListener(e -> performCheckIn());
        footer.add(submitButton);

        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void loadActiveSessions() {
        SwingUtils.executeAsync(
                this,
                () -> sessionService.getActiveSessionsForStudent(currentUser),
                sessions -> {
                    this.activeSessions = sessions;
                    sessionCombo.removeAllItems();
                    if (sessions.isEmpty()) {
                        sessionDetailsLabel.setText("No active attendance sessions currently open for your enrolled courses.");
                        sessionDetailsLabel.setForeground(CampusTheme.TEXT_MUTED);
                        codeField.setEnabled(false);
                        submitButton.setEnabled(false);
                    } else {
                        codeField.setEnabled(true);
                        submitButton.setEnabled(true);
                        for (ActiveSessionInfo info : sessions) {
                            sessionCombo.addItem(new SessionItem(info));
                        }
                        updateSelectedSessionDetails();
                    }
                },
                error -> {
                    sessionDetailsLabel.setText("Error loading active sessions: " + error.getMessage());
                    sessionDetailsLabel.setForeground(SwingUtils.DANGER_COLOR);
                }
        );
    }

    private void updateSelectedSessionDetails() {
        SessionItem item = (SessionItem) sessionCombo.getSelectedItem();
        if (item == null || item.info == null) {
            sessionDetailsLabel.setText("No session selected.");
            return;
        }
        ActiveSessionInfo info = item.info;
        long sec = info.getRemainingSeconds();
        long m = sec / 60;
        long s = sec % 60;

        String submittedNotice = info.isAlreadySubmitted() ? " [ALREADY CHECKED IN]" : "";
        sessionDetailsLabel.setText(String.format(
                "<html><b>Course:</b> %s (%s)<br/><b>Faculty:</b> %s • <b>Session #</b>%s<br/><b>Window:</b> %02d:%02d remaining%s</html>",
                info.getCourseName(), info.getCourseCode(), info.getFacultyName(),
                info.getSession().getSessionNumber(), m, s, submittedNotice
        ));
        sessionDetailsLabel.setForeground(info.isAlreadySubmitted() ? SwingUtils.SUCCESS_COLOR : CampusTheme.TEXT_PRIMARY);
    }

    private void performCheckIn() {
        SessionItem item = (SessionItem) sessionCombo.getSelectedItem();
        String candidateCode = codeField.getText().trim();

        if (candidateCode.isEmpty()) {
            setStatusFeedback("INVALID CODE: Please enter the 6-digit numeric verification code.", SwingUtils.DANGER_COLOR);
            return;
        }

        submitButton.setEnabled(false);
        setStatusFeedback("Verifying check-in...", CampusTheme.PRIMARY);

        SwingUtils.executeAsync(
                this,
                () -> {
                    if (item != null && item.info != null) {
                        return sessionService.checkInToSession(currentUser, item.info.getSession().getId(), candidateCode);
                    } else {
                        return sessionService.checkIn(currentUser, candidateCode);
                    }
                },
                submission -> {
                    submitButton.setEnabled(true);
                    setStatusFeedback("CHECK-IN SUCCESSFUL!", SwingUtils.SUCCESS_COLOR);
                    JOptionPane.showMessageDialog(this,
                            "✓ CHECK-IN SUCCESSFUL!\n\n" +
                            "Session Code: " + candidateCode + "\n" +
                            "Submission Time: " + submission.getSubmissionTime() + "\n" +
                            "Status: CHECKED IN (Provisional)\n\n" +
                            "Your presence has been recorded and will be confirmed when your instructor finalizes the session.",
                            "Attendance Check-In Confirmed",
                            JOptionPane.INFORMATION_MESSAGE);
                    if (onCheckInSuccess != null) onCheckInSuccess.run();
                    dispose();
                },
                error -> {
                    submitButton.setEnabled(true);
                    String msg = error.getMessage();
                    setStatusFeedback(msg, SwingUtils.DANGER_COLOR);
                    JOptionPane.showMessageDialog(this,
                            msg,
                            "Check-In Failed",
                            JOptionPane.WARNING_MESSAGE);
                }
        );
    }

    private void setStatusFeedback(String text, Color color) {
        statusFeedbackLabel.setText(text);
        statusFeedbackLabel.setForeground(color);
    }

    public static class SessionItem {
        public final ActiveSessionInfo info;
        public SessionItem(ActiveSessionInfo info) { this.info = info; }
        @Override
        public String toString() {
            if (info == null) return "None";
            return info.getCourseCode() + " - " + info.getCourseName() + " (Session #" + info.getSession().getSessionNumber() + ")";
        }
    }
}
