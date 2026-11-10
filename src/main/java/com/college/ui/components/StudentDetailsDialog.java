package com.college.ui.components;

import com.college.model.Department;
import com.college.model.Student;
import com.college.model.User;
import com.college.ui.theme.CampusTheme;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

/**
 * Modal dialog for inspecting the complete, untruncated profile of a Student record.
 * Displays all academic, contact, and system credentials information without clipping.
 */
public class StudentDetailsDialog extends JDialog {

    public StudentDetailsDialog(Window owner, Student student, Department department, User linkedUser) {
        super(owner, "Student Record Details — " + student.getStudentId(), ModalityType.APPLICATION_MODAL);
        initUI(student, department, linkedUser);
    }

    private void initUI(Student s, Department d, User u) {
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
        JLabel nameLabel = new JLabel(s.getFirstName() + " " + s.getLastName());
        nameLabel.setFont(CampusTheme.FONT_TITLE);
        nameLabel.setForeground(CampusTheme.PRIMARY);

        String deptStr = (d != null) ? d.getName() + " (" + d.getCode() + ")" : "Department #" + s.getDepartmentId();
        JLabel subLabel = new JLabel("ID: " + s.getStudentId() + "  •  " + deptStr);
        subLabel.setFont(CampusTheme.FONT_SMALL);
        subLabel.setForeground(CampusTheme.TEXT_SECONDARY);
        namePanel.add(nameLabel);
        namePanel.add(subLabel);
        headerPanel.add(namePanel, BorderLayout.WEST);

        JPanel badgePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 8));
        badgePanel.setOpaque(false);
        StatusBadge statusBadge = StatusBadge.of(s.isActive() ? "ACTIVE" : "INACTIVE");
        badgePanel.add(statusBadge);
        headerPanel.add(badgePanel, BorderLayout.EAST);

        root.add(headerPanel, BorderLayout.NORTH);

        // Body Content (Scrollable for smaller screens)
        JPanel bodyPanel = new JPanel();
        bodyPanel.setLayout(new BoxLayout(bodyPanel, BoxLayout.Y_AXIS));
        bodyPanel.setOpaque(false);

        // Academic Card
        JPanel academicCard = SwingUtils.createCardPanel("Academic & Registration Details");
        JPanel acadGrid = new JPanel(new GridBagLayout());
        acadGrid.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addDetailRow(acadGrid, gbc, row++, "Registration ID:", s.getStudentId());
        addDetailRow(acadGrid, gbc, row++, "Full Name:", s.getFirstName() + " " + s.getLastName());
        addDetailRow(acadGrid, gbc, row++, "Faculty / School:", (s.getSchoolName() != null ? s.getSchoolName() : (d != null && d.getSchool() != null ? d.getSchool() : "General")));
        addDetailRow(acadGrid, gbc, row++, "Department:", deptStr);
        addDetailRow(acadGrid, gbc, row++, "Programme:", (s.getProgramName() != null ? s.getProgramName() + " (" + s.getProgramCode() + ")" : "General"));
        addDetailRow(acadGrid, gbc, row++, "Degree Level:", (s.getDegree() != null ? s.getDegree() : "Undergraduate"));
        addDetailRow(acadGrid, gbc, row++, "Admission Cohort:", (s.getCohortCode() != null ? s.getCohortCode() : "General"));
        addDetailRow(acadGrid, gbc, row++, "Current Semester:", s.getSemester() != null ? s.getSemester() : "1");
        addDetailRow(acadGrid, gbc, row++, "Expected Graduation:", (s.getExpectedGraduationYear() != null ? s.getExpectedGraduationYear() : "Not Specified"));
        addDetailRow(acadGrid, gbc, row++, "Admission Date:", s.getAdmissionDate() != null ? s.getAdmissionDate() : "Not Specified");
        addDetailRow(acadGrid, gbc, row++, "Date of Birth:", s.getDateOfBirth() != null ? s.getDateOfBirth() : "Not Specified");
        academicCard.add(acadGrid, BorderLayout.CENTER);
        bodyPanel.add(academicCard);
        bodyPanel.add(Box.createVerticalStrut(12));

        // Contact Card
        JPanel contactCard = SwingUtils.createCardPanel("Contact Information");
        JPanel contactGrid = new JPanel(new GridBagLayout());
        contactGrid.setOpaque(false);
        row = 0;
        addDetailRow(contactGrid, gbc, row++, "Email Address:", s.getEmail() != null ? s.getEmail() : "N/A");
        addDetailRow(contactGrid, gbc, row++, "Phone Number:", s.getPhone() != null ? s.getPhone() : "Not Provided");
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
            addDetailRow(secGrid, gbc, row++, "Role:", u.getRole() != null ? u.getRole().name() : "STUDENT");
            addDetailRow(secGrid, gbc, row++, "Password Policy:", u.isMustChangePassword() ? "Temporary — Must Change on Next Login" : "Standard Active");
        } else {
            addDetailRow(secGrid, gbc, row++, "User Account:", "No user account linked to this registration ID");
        }
        secCard.add(secGrid, BorderLayout.CENTER);
        bodyPanel.add(secCard);

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
            sb.append("=== CAMPUS ERP STUDENT RECORD ===\n");
            sb.append("Registration ID: ").append(s.getStudentId()).append("\n");
            sb.append("Full Name:       ").append(s.getFirstName()).append(" ").append(s.getLastName()).append("\n");
            sb.append("Department:      ").append(deptStr).append("\n");
            sb.append("Semester:        ").append(s.getSemester() != null ? s.getSemester() : "1").append("\n");
            sb.append("Admission Date:  ").append(s.getAdmissionDate() != null ? s.getAdmissionDate() : "N/A").append("\n");
            sb.append("Date of Birth:   ").append(s.getDateOfBirth() != null ? s.getDateOfBirth() : "N/A").append("\n");
            sb.append("Email Address:   ").append(s.getEmail()).append("\n");
            sb.append("Phone Number:    ").append(s.getPhone() != null ? s.getPhone() : "N/A").append("\n");
            sb.append("Status:          ").append(s.isActive() ? "Active" : "Inactive").append("\n");
            if (u != null) {
                sb.append("Account User:    ").append(u.getUsername()).append("\n");
                sb.append("Password State:  ").append(u.isMustChangePassword() ? "Temporary (Change Pending)" : "Active").append("\n");
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

