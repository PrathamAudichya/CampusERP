package com.college.ui.components;

import com.college.model.Department;
import com.college.model.Faculty;
import com.college.model.User;
import com.college.ui.theme.CampusTheme;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

/**
 * Modal dialog for inspecting the complete, untruncated profile of a Faculty member.
 * Displays all academic appointment, specialization, contact, credentials, and departmental information without clipping.
 */
public class FacultyDetailsDialog extends JDialog {

    public FacultyDetailsDialog(Window owner, Faculty faculty, Department department) {
        this(owner, faculty, department, null);
    }

    public FacultyDetailsDialog(Window owner, Faculty faculty, Department department, User linkedUser) {
        super(owner, "Faculty Member Details — " + faculty.getFacultyId(), ModalityType.APPLICATION_MODAL);
        initUI(faculty, department, linkedUser);
    }

    private void initUI(Faculty f, Department d, User u) {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(620, 560);
        setMinimumSize(new Dimension(520, 440));
        setLocationRelativeTo(getOwner());

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(CampusTheme.BG_MAIN);
        root.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        // Header Panel
        JPanel headerPanel = new JPanel(new BorderLayout(12, 0));
        headerPanel.setBackground(CampusTheme.CARD);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CampusTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        JPanel namePanel = new JPanel(new GridLayout(2, 1, 0, 4));
        namePanel.setOpaque(false);
        JLabel nameLabel = new JLabel(f.getFirstName() + " " + f.getLastName());
        nameLabel.setFont(CampusTheme.FONT_TITLE);
        nameLabel.setForeground(CampusTheme.PRIMARY);

        String deptStr = (d != null) ? d.getName() + " (" + d.getCode() + ")" : "Department #" + f.getDepartmentId();
        JLabel subLabel = new JLabel("ID: " + f.getFacultyId() + "  •  " + deptStr);
        subLabel.setFont(CampusTheme.FONT_SMALL);
        subLabel.setForeground(CampusTheme.TEXT_SECONDARY);
        namePanel.add(nameLabel);
        namePanel.add(subLabel);
        headerPanel.add(namePanel, BorderLayout.WEST);

        JPanel badgePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 8));
        badgePanel.setOpaque(false);
        StatusBadge statusBadge = StatusBadge.of(f.isActive() ? "ACTIVE" : "INACTIVE");
        badgePanel.add(statusBadge);
        headerPanel.add(badgePanel, BorderLayout.EAST);

        root.add(headerPanel, BorderLayout.NORTH);

        // Body Content (Scrollable for compact screens)
        JPanel bodyPanel = new JPanel();
        bodyPanel.setLayout(new BoxLayout(bodyPanel, BoxLayout.Y_AXIS));
        bodyPanel.setOpaque(false);

        // Academic & Appointment Card
        JPanel academicCard = SwingUtils.createCardPanel("Academic & Appointment Details");
        JPanel acadGrid = new JPanel(new GridBagLayout());
        acadGrid.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addDetailRow(acadGrid, gbc, row++, "Faculty ID:", f.getFacultyId());
        addDetailRow(acadGrid, gbc, row++, "Full Name:", f.getFirstName() + " " + f.getLastName());
        addDetailRow(acadGrid, gbc, row++, "Department:", deptStr);
        addDetailRow(acadGrid, gbc, row++, "Specialization:", f.getSpecialization() != null && !f.getSpecialization().isEmpty() ? f.getSpecialization() : "General Academic");
        addDetailRow(acadGrid, gbc, row++, "Date of Joining:", f.getJoinDate() != null ? f.getJoinDate() : "Not Specified");
        academicCard.add(acadGrid, BorderLayout.CENTER);
        bodyPanel.add(academicCard);
        bodyPanel.add(Box.createVerticalStrut(12));

        // Contact Card
        JPanel contactCard = SwingUtils.createCardPanel("Contact & Institutional Communication");
        JPanel contactGrid = new JPanel(new GridBagLayout());
        contactGrid.setOpaque(false);
        row = 0;
        addDetailRow(contactGrid, gbc, row++, "Official Email:", f.getEmail() != null ? f.getEmail() : "N/A");
        addDetailRow(contactGrid, gbc, row++, "Phone Number:", f.getPhone() != null ? f.getPhone() : "Not Provided");
        contactCard.add(contactGrid, BorderLayout.CENTER);
        bodyPanel.add(contactCard);
        bodyPanel.add(Box.createVerticalStrut(12));

        // Account & Security Card
        JPanel secCard = SwingUtils.createCardPanel("System Account & Credentials");
        JPanel secGrid = new JPanel(new GridBagLayout());
        secGrid.setOpaque(false);
        row = 0;
        if (u != null) {
            addDetailRow(secGrid, gbc, row++, "Account Username:", u.getUsername());
            addDetailRow(secGrid, gbc, row++, "Account Status:", u.isActive() ? "Active" : "Disabled");
            addDetailRow(secGrid, gbc, row++, "Role:", u.getRole() != null ? u.getRole().name() : "FACULTY");
            addDetailRow(secGrid, gbc, row++, "Password Policy:", u.isMustChangePassword() ? "Temporary — Must Change on Next Login" : "Standard Active");
        } else {
            addDetailRow(secGrid, gbc, row++, "User Account:", "No user account linked to this faculty record");
        }
        secCard.add(secGrid, BorderLayout.CENTER);
        bodyPanel.add(secCard);
        bodyPanel.add(Box.createVerticalStrut(12));

        // System Status Card
        JPanel statusCard = SwingUtils.createCardPanel("Institutional System Status");
        JPanel statusGrid = new JPanel(new GridBagLayout());
        statusGrid.setOpaque(false);
        row = 0;
        addDetailRow(statusGrid, gbc, row++, "Faculty Status:", f.isActive() ? "Active Teaching Faculty" : "Inactive / On Leave");
        addDetailRow(statusGrid, gbc, row++, "System ID (PK):", String.valueOf(f.getId()));
        statusCard.add(statusGrid, BorderLayout.CENTER);
        bodyPanel.add(statusCard);

        JScrollPane scrollPane = new JScrollPane(bodyPanel);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(14);
        root.add(scrollPane, BorderLayout.CENTER);

        // Footer Actions
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        JLabel statusLabel = new JLabel(" ");
        statusLabel.setFont(CampusTheme.FONT_SMALL_BOLD);
        statusLabel.setForeground(CampusTheme.PRIMARY);
        footer.add(statusLabel, BorderLayout.WEST);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setOpaque(false);

        JButton copyBtn = new SecondaryButton("Copy Details");
        copyBtn.setIcon(CampusIcons.get(CampusIcons.IconType.REPORTS, 14, CampusTheme.TEXT_SECONDARY));
        copyBtn.addActionListener(e -> {
            StringBuilder sb = new StringBuilder();
            sb.append("=== CAMPUS ERP FACULTY RECORD ===\n");
            sb.append("Faculty ID:     ").append(f.getFacultyId()).append("\n");
            sb.append("Full Name:      ").append(f.getFirstName()).append(" ").append(f.getLastName()).append("\n");
            sb.append("Department:     ").append(deptStr).append("\n");
            sb.append("Specialization: ").append(f.getSpecialization()).append("\n");
            sb.append("Join Date:      ").append(f.getJoinDate() != null ? f.getJoinDate() : "N/A").append("\n");
            sb.append("Email Address:  ").append(f.getEmail()).append("\n");
            sb.append("Phone Number:   ").append(f.getPhone() != null ? f.getPhone() : "N/A").append("\n");
            sb.append("Status:         ").append(f.isActive() ? "Active" : "Inactive").append("\n");
            if (u != null) {
                sb.append("Account User:   ").append(u.getUsername()).append("\n");
                sb.append("Password State: ").append(u.isMustChangePassword() ? "Temporary (Change Pending)" : "Active").append("\n");
            }
            sb.append("=================================\n");
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
            statusLabel.setText("✓ Copied full record to clipboard.");
        });
        btnRow.add(copyBtn);

        JButton closeBtn = new PrimaryButton("Close");
        closeBtn.addActionListener(e -> dispose());
        btnRow.add(closeBtn);

        footer.add(btnRow, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void addDetailRow(JPanel panel, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.35;
        JLabel lbl = new JLabel(label);
        lbl.setFont(CampusTheme.FONT_BOLD);
        lbl.setForeground(CampusTheme.TEXT_SECONDARY);
        panel.add(lbl, gbc);

        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 0.65;
        JLabel val = new JLabel(value);
        val.setFont(CampusTheme.FONT_BODY);
        val.setForeground(CampusTheme.TEXT_PRIMARY);
        panel.add(val, gbc);
    }
}

