package com.college.ui.dialog;

import com.college.ui.components.CampusIcons;
import com.college.ui.components.PrimaryButton;
import com.college.ui.components.SecondaryButton;
import com.college.ui.theme.CampusTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

/**
 * Institutional modal dialog providing secure, one-time presentation of temporary login credentials.
 * Includes explicit buttons for Copy Username, Copy Password, Copy All (with login instructions),
 * and security policy notices.
 */
public class TemporaryPasswordDialog extends JDialog {

    private final String personName;
    private final String naturalId;
    private final String username;
    private final String tempPassword;
    private final JLabel statusMsg;

    public TemporaryPasswordDialog(Window parent, String naturalId, String username, String tempPassword, String titleText) {
        this(parent, null, naturalId, username, tempPassword, titleText);
    }

    public TemporaryPasswordDialog(Window parent, String personName, String naturalId, String username, String tempPassword, String titleText) {
        super(parent, titleText, ModalityType.APPLICATION_MODAL);
        this.personName = personName != null && !personName.isBlank() ? personName.trim() : null;
        this.naturalId = naturalId != null ? naturalId : "—";
        this.username = username != null ? username : "";
        this.tempPassword = tempPassword != null ? tempPassword : "";

        setSize(540, 480);
        setLocationRelativeTo(parent);
        setResizable(false);
        setLayout(new BorderLayout(16, 16));
        getContentPane().setBackground(CampusTheme.BG_CARD);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setOpaque(false);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));

        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setFont(CampusTheme.FONT_TITLE);
        titleLabel.setForeground(CampusTheme.PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createVerticalStrut(10));

        // Prominent Warning Banner
        JPanel warningBanner = new JPanel(new BorderLayout(10, 8));
        warningBanner.setBackground(CampusTheme.BG_MAIN);
        warningBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CampusTheme.WARNING, 1, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        JLabel warnIcon = new JLabel(CampusIcons.get(CampusIcons.IconType.WARNING, 20, CampusTheme.WARNING));
        JLabel warnText = new JLabel("<html><b>SECURITY NOTICE:</b><br/>"
                + "This temporary password is displayed <b>only once</b> and is not retrievable afterwards.<br/>"
                + "Securely transmit these credentials to the user. The user will be required to change their password on initial login.</html>");
        warnText.setFont(CampusTheme.FONT_MICRO);
        warnText.setForeground(CampusTheme.TEXT_PRIMARY);
        warningBanner.add(warnIcon, BorderLayout.WEST);
        warningBanner.add(warnText, BorderLayout.CENTER);
        warningBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(warningBanner);
        mainPanel.add(Box.createVerticalStrut(14));

        // Credentials Grid
        int rows = this.personName != null ? 3 : 2;
        JPanel credGrid = new JPanel(new GridLayout(rows, 2, 10, 8));
        credGrid.setOpaque(false);
        credGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (this.personName != null) {
            JLabel lblName = new JLabel("Full Name:");
            lblName.setFont(CampusTheme.FONT_BOLD);
            JTextField txtName = new JTextField(this.personName);
            txtName.setEditable(false);
            txtName.setFont(CampusTheme.FONT_BODY);
            credGrid.add(lblName);
            credGrid.add(txtName);
        }

        JLabel lblNaturalId = new JLabel("Registration / Employee ID:");
        lblNaturalId.setFont(CampusTheme.FONT_BOLD);
        JTextField txtNaturalId = new JTextField(this.naturalId);
        txtNaturalId.setEditable(false);
        txtNaturalId.setFont(CampusTheme.FONT_BODY);

        JLabel lblUser = new JLabel("Assigned Username:");
        lblUser.setFont(CampusTheme.FONT_BOLD);
        JTextField txtUser = new JTextField(this.username);
        txtUser.setEditable(false);
        txtUser.setFont(CampusTheme.FONT_BODY);

        credGrid.add(lblNaturalId);
        credGrid.add(txtNaturalId);
        credGrid.add(lblUser);
        credGrid.add(txtUser);
        mainPanel.add(credGrid);
        mainPanel.add(Box.createVerticalStrut(14));

        // Generated Temporary Password Display
        JLabel lblPass = new JLabel("Generated Temporary Password:");
        lblPass.setFont(CampusTheme.FONT_BOLD);
        lblPass.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(lblPass);
        mainPanel.add(Box.createVerticalStrut(6));

        JTextField passField = new JTextField(this.tempPassword);
        passField.setEditable(false);
        passField.setFont(new Font("Consolas", Font.BOLD, 16));
        passField.setForeground(CampusTheme.PRIMARY);
        passField.setBackground(Color.WHITE);
        passField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CampusTheme.BORDER, 1, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        passField.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(passField);
        mainPanel.add(Box.createVerticalStrut(10));

        // Instructions notice
        JLabel lblInstructions = new JLabel("<html><b>Login Instructions:</b> Launch Campus ERP, enter Assigned Username and Temporary Password. "
                + "System will enforce immediate permanent password rotation on first login.</html>");
        lblInstructions.setFont(CampusTheme.FONT_MICRO);
        lblInstructions.setForeground(CampusTheme.TEXT_SECONDARY);
        lblInstructions.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(lblInstructions);
        mainPanel.add(Box.createVerticalStrut(10));

        // Clipboard Actions Row
        JPanel copyBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        copyBar.setOpaque(false);
        copyBar.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton copyUserBtn = new SecondaryButton("Copy Username");
        copyUserBtn.addActionListener(e -> copyToClipboard(this.username, "Username copied to clipboard."));

        JButton copyPassBtn = new SecondaryButton("Copy Password");
        copyPassBtn.addActionListener(e -> copyToClipboard(this.tempPassword, "Password copied to clipboard."));

        JButton copyAllBtn = new PrimaryButton("Copy All Credentials");
        copyAllBtn.addActionListener(e -> {
            String fullMemo = String.format(
                    "CAMPUS ERP LOGIN CREDENTIALS\n" +
                    "------------------------------------\n" +
                    (this.personName != null ? "Full Name:        " + this.personName + "\n" : "") +
                    "Institutional ID: %s\n" +
                    "Username:         %s\n" +
                    "Temporary Pass:   %s\n" +
                    "------------------------------------\n" +
                    "Instructions: Launch Campus ERP, enter your username and temporary password.\n" +
                    "You will be required to set your permanent private password immediately.",
                    this.naturalId, this.username, this.tempPassword
            );
            copyToClipboard(fullMemo, "Complete login details copied to clipboard.");
        });

        copyBar.add(copyUserBtn);
        copyBar.add(copyPassBtn);
        copyBar.add(copyAllBtn);
        mainPanel.add(copyBar);
        mainPanel.add(Box.createVerticalStrut(8));

        statusMsg = new JLabel(" ");
        statusMsg.setFont(CampusTheme.FONT_SMALL_BOLD);
        statusMsg.setForeground(CampusTheme.PRIMARY);
        statusMsg.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(statusMsg);

        add(mainPanel, BorderLayout.CENTER);

        // Bottom Done / Close Button
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 12));
        bottomBar.setBackground(CampusTheme.BG_MAIN);
        bottomBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, CampusTheme.BORDER));

        JButton doneBtn = new PrimaryButton("Done");
        doneBtn.setPreferredSize(new Dimension(100, 34));
        doneBtn.addActionListener(e -> dispose());
        bottomBar.add(doneBtn);
        add(bottomBar, BorderLayout.SOUTH);
    }

    private void copyToClipboard(String content, String message) {
        StringSelection selection = new StringSelection(content);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        statusMsg.setText("✓ " + message);
    }
}

