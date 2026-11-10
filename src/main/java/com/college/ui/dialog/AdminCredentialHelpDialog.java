package com.college.ui.dialog;

import com.college.ui.components.CampusIcons;
import com.college.ui.components.PrimaryButton;
import com.college.ui.theme.CampusTheme;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * Institutional administrator guide dialog explaining credential issuance, identity conventions,
 * temporary password lifecycle, and secure password reset workflows.
 */
public class AdminCredentialHelpDialog extends JDialog {

    public AdminCredentialHelpDialog(Window parent) {
        super(parent, "Administrator Guide — Institutional Credentials & Authentication", ModalityType.APPLICATION_MODAL);
        setSize(680, 560);
        setLocationRelativeTo(parent);
        setResizable(false);
        setLayout(new BorderLayout());
        getContentPane().setBackground(CampusTheme.BG_MAIN);

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout(12, 12));
        headerPanel.setBackground(CampusTheme.BG_CARD);
        headerPanel.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, CampusTheme.BORDER),
                new EmptyBorder(18, 24, 18, 24)
        ));

        JLabel titleLabel = new JLabel("How Login Credentials Work in Campus ERP");
        titleLabel.setFont(CampusTheme.FONT_TITLE);
        titleLabel.setForeground(CampusTheme.TEXT_PRIMARY);

        JLabel subtitleLabel = new JLabel("Official policy and reference guide for institutional identity and credential distribution.");
        subtitleLabel.setFont(CampusTheme.FONT_SMALL);
        subtitleLabel.setForeground(CampusTheme.TEXT_SECONDARY);

        JPanel titlesBox = new JPanel(new GridLayout(2, 1, 4, 4));
        titlesBox.setOpaque(false);
        titlesBox.add(titleLabel);
        titlesBox.add(subtitleLabel);

        headerPanel.add(titlesBox, BorderLayout.CENTER);
        headerPanel.add(new JLabel(CampusIcons.get(CampusIcons.IconType.PASSWORD, 32, CampusTheme.PRIMARY)), BorderLayout.WEST);
        add(headerPanel, BorderLayout.NORTH);

        // Content Area (Scrollable)
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);
        contentPanel.setBorder(new EmptyBorder(16, 24, 16, 24));

        contentPanel.add(createSectionCard(
                "1. Student Username Identity",
                "Students log in using their <b>Student Registration Number</b> (e.g., <code>STU2026001</code>).<br/>" +
                "When an administrator registers a student in the Student Directory, the linked user account is provisioned automatically with this identifier."
        ));
        contentPanel.add(Box.createVerticalStrut(12));

        contentPanel.add(createSectionCard(
                "2. Faculty Username Identity",
                "Faculty members log in using their assigned <b>Faculty ID</b> (e.g., <code>FAC2026001</code>).<br/>" +
                "This guarantees unique, institutional identity alignment across rosters, assignments, and directories."
        ));
        contentPanel.add(Box.createVerticalStrut(12));

        contentPanel.add(createSectionCard(
                "3. One-Time Temporary Passwords",
                "On initial creation, a cryptographically strong 12-character temporary password is generated.<br/>" +
                "<b>Security Rule:</b> The password is displayed exactly once in the secure modal dialog. Because passwords are stored as irreversible BCrypt hashes, the system cannot retrieve previously generated plaintext passwords."
        ));
        contentPanel.add(Box.createVerticalStrut(12));

        contentPanel.add(createSectionCard(
                "4. What to Do If Credentials Are Lost",
                "If a student or faculty member forgets or misplaces their credentials, an administrator can generate a new temporary password at any time via:<br/>" +
                "• <b>User Directory:</b> Select the user and click <i>Reset Password</i>.<br/>" +
                "• <b>Student / Faculty Directory:</b> Select the record and click <i>Reset Password</i>.<br/>" +
                "This generates a fresh temporary credential and displays the copy dialog."
        ));
        contentPanel.add(Box.createVerticalStrut(12));

        contentPanel.add(createSectionCard(
                "5. Mandatory Password Change on First Login",
                "All newly provisioned accounts or reset accounts have the <code>must_change_password</code> flag enabled.<br/>" +
                "Upon signing in with the temporary password, the portal immediately prompts the user to define their personal, private password before accessing courses or grades."
        ));
        contentPanel.add(Box.createVerticalStrut(12));

        contentPanel.add(createSectionCard(
                "6. Security & Confidentiality Best Practices",
                "Administrators should never write down or store plaintext credentials on unencrypted sticky notes or spreadsheets. Deliver credentials directly to the student/faculty member via institutional communication channels."
        ));

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(14);
        add(scrollPane, BorderLayout.CENTER);

        // Bottom Close
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 24, 12));
        bottomBar.setBackground(CampusTheme.BG_CARD);
        bottomBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, CampusTheme.BORDER));

        JButton closeBtn = new PrimaryButton("Close Guide");
        closeBtn.setPreferredSize(new Dimension(120, 36));
        closeBtn.addActionListener(e -> dispose());
        bottomBar.add(closeBtn);
        add(bottomBar, BorderLayout.SOUTH);
    }

    private JPanel createSectionCard(String heading, String htmlBody) {
        JPanel card = new JPanel(new BorderLayout(8, 6));
        card.setBackground(CampusTheme.BG_CARD);
        card.setBorder(new CompoundBorder(
                new LineBorder(CampusTheme.BORDER, 1, true),
                new EmptyBorder(10, 14, 10, 14)
        ));

        JLabel title = new JLabel(heading);
        title.setFont(CampusTheme.FONT_BOLD);
        title.setForeground(CampusTheme.PRIMARY);

        JLabel body = new JLabel("<html><body style='width: 480px;'>" + htmlBody + "</body></html>");
        body.setFont(CampusTheme.FONT_SMALL);
        body.setForeground(CampusTheme.TEXT_PRIMARY);

        card.add(title, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }
}

