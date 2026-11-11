package com.college.ui;

import com.college.config.AppConfig;
import com.college.config.DBConnection;
import com.college.config.DatabaseProfile;
import com.college.model.User;
import com.college.service.AuthService;
import com.college.ui.components.CampusIcons;
import com.college.ui.components.CampusLogo;
import com.college.ui.components.DatabaseInfoDialog;
import com.college.ui.components.PrimaryButton;
import com.college.ui.components.SecondaryButton;
import com.college.ui.theme.CampusTheme;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Unified, institutional authentication login interface for Campus ERP v2.0.5.
 *
 * <p>Requirements:
 * <ul>
 *   <li>One single, shared login page for all institutional roles (Admin, Faculty, Student).</li>
 *   <li>No role selection dropdown — the system dynamically routes based on verified credentials.</li>
 *   <li>Warm institutional palette with light sage hero panel and clean white card.</li>
 *   <li>Pure Java2D vector iconography (CampusIcons) eliminating Unicode emoji boxes.</li>
 *   <li>Integrates secure first-run administrator provisioning on fresh databases.</li>
 * </ul>
 * </p>
 */
public class LoginFrame extends JFrame {

    private final AuthService authService;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private PrimaryButton loginButton;
    private JLabel statusLabel;
    private JLabel capsLockLabel;

    public LoginFrame() {
        this(new AuthService());
    }

    public LoginFrame(AuthService authService) {
        super(AppConfig.APP_TITLE + " [" + DBConnection.getActiveProfile().getUiBadge() + "] — Sign In");
        this.authService = authService;
        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setSize(800, 540);
        setLocationRelativeTo(null);
        setIconImage(CampusLogo.createLogoImage(64, 64));

        JPanel container = new JPanel(new GridLayout(1, 2));
        container.setBackground(CampusTheme.BG_MAIN);

        // =====================================================================
        // Left Side: Institutional Brand Hero Panel (Light Sage #ECEFE8)
        // =====================================================================
        JPanel brandPanel = new JPanel();
        brandPanel.setLayout(new BoxLayout(brandPanel, BoxLayout.Y_AXIS));
        brandPanel.setBackground(CampusTheme.BG_SIDEBAR);
        brandPanel.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, CampusTheme.BORDER),
                new EmptyBorder(40, 36, 40, 36)
        ));

        brandPanel.add(Box.createVerticalGlue());

        CampusLogo logo = new CampusLogo(64, CampusLogo.LogoVariant.ICON_ONLY);
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        brandPanel.add(logo);
        brandPanel.add(Box.createVerticalStrut(18));

        JLabel brandTitle = new JLabel("CAMPUS ERP");
        brandTitle.setFont(CampusTheme.FONT_HERO);
        brandTitle.setForeground(CampusTheme.TEXT_PRIMARY);
        brandTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        brandPanel.add(brandTitle);

        JLabel brandSubtitle = new JLabel(AppConfig.APP_TAGLINE);
        brandSubtitle.setFont(CampusTheme.FONT_SUBTITLE);
        brandSubtitle.setForeground(CampusTheme.TEXT_SECONDARY);
        brandSubtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        brandPanel.add(brandSubtitle);

        brandPanel.add(Box.createVerticalStrut(18));

        // Decorative Terracotta Divider
        JPanel divider = new JPanel();
        divider.setBackground(CampusTheme.TERRACOTTA);
        divider.setMaximumSize(new Dimension(54, 3));
        divider.setAlignmentX(Component.LEFT_ALIGNMENT);
        brandPanel.add(divider);

        brandPanel.add(Box.createVerticalStrut(22));

        // Feature Highlights with Vector Checkmarks
        brandPanel.add(createFeatureItem("Single Unified Institutional Access"));
        brandPanel.add(Box.createVerticalStrut(10));
        brandPanel.add(createFeatureItem("Role-Based Academic Governance"));
        brandPanel.add(Box.createVerticalStrut(10));
        brandPanel.add(createFeatureItem("Audited Administrative Operations"));

        brandPanel.add(Box.createVerticalStrut(16));

        // Institutional Login Instructions Card
        JPanel guideCard = new JPanel(new BorderLayout(8, 6));
        guideCard.setBackground(Color.WHITE);
        guideCard.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(CampusTheme.BORDER, 1, true),
                new EmptyBorder(10, 12, 10, 12)
        ));
        guideCard.setMaximumSize(new Dimension(300, 105));
        guideCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel guideHeader = new JLabel("Institutional Sign-In Instructions");
        guideHeader.setFont(CampusTheme.FONT_BOLD);
        guideHeader.setForeground(CampusTheme.PRIMARY);

        JLabel guideText = new JLabel("<html><body style='width:240px;'>"
                + "• <b>Students:</b> Registration No. (e.g. STU2026001)<br/>"
                + "• <b>Faculty:</b> Faculty ID (e.g. FAC2026001)<br/>"
                + "• <b>First Login:</b> Enter temporary password to set personal password."
                + "</body></html>");
        guideText.setFont(CampusTheme.FONT_MICRO);
        guideText.setForeground(CampusTheme.TEXT_SECONDARY);

        guideCard.add(guideHeader, BorderLayout.NORTH);
        guideCard.add(guideText, BorderLayout.CENTER);
        brandPanel.add(guideCard);

        brandPanel.add(Box.createVerticalGlue());

        JLabel versionLabel = new JLabel("Release v" + AppConfig.APP_VERSION + " • Production Ready");
        versionLabel.setFont(CampusTheme.FONT_MICRO);
        versionLabel.setForeground(CampusTheme.TEXT_MUTED);
        versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        brandPanel.add(versionLabel);

        // Database Profile Badge
        DatabaseProfile activeProfile = DBConnection.getActiveProfile();
        boolean isDemo = activeProfile == DatabaseProfile.DEMO;
        JButton dbBadge = new JButton(activeProfile.getUiBadge());
        dbBadge.setFont(CampusTheme.FONT_MICRO);
        dbBadge.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        dbBadge.setForeground(isDemo ? CampusTheme.TERRACOTTA : CampusTheme.PRIMARY);
        dbBadge.setBackground(isDemo ? new Color(0xFD, 0xEB, 0xE7) : new Color(0xE8, 0xF5, 0xE9));
        dbBadge.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(isDemo ? CampusTheme.TERRACOTTA : CampusTheme.PRIMARY, 1, true),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)
        ));
        dbBadge.setToolTipText("Database: " + DBConnection.getActiveDatabasePath() + " (Click for details)");
        dbBadge.addActionListener(e -> new DatabaseInfoDialog(this).setVisible(true));
        dbBadge.setAlignmentX(Component.LEFT_ALIGNMENT);
        brandPanel.add(Box.createVerticalStrut(6));
        brandPanel.add(dbBadge);

        container.add(brandPanel);

        // =====================================================================
        // Right Side: Authentication Form Card (#FFFFFF surface)
        // =====================================================================
        JPanel formCard = new JPanel(new BorderLayout());
        formCard.setBackground(CampusTheme.BG_CARD);
        formCard.setBorder(new EmptyBorder(40, 40, 36, 40));

        // Form Header
        JPanel formHeader = new JPanel();
        formHeader.setLayout(new BoxLayout(formHeader, BoxLayout.Y_AXIS));
        formHeader.setOpaque(false);

        JLabel welcomeLabel = new JLabel("Welcome back");
        welcomeLabel.setFont(CampusTheme.FONT_HERO);
        welcomeLabel.setForeground(CampusTheme.TEXT_PRIMARY);
        welcomeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        formHeader.add(welcomeLabel);
        formHeader.add(Box.createVerticalStrut(4));

        JLabel instructionLabel = new JLabel("Please enter your credentials to access your portal.");
        instructionLabel.setFont(CampusTheme.FONT_SMALL);
        instructionLabel.setForeground(CampusTheme.TEXT_SECONDARY);
        instructionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        formHeader.add(instructionLabel);

        formCard.add(formHeader, BorderLayout.NORTH);

        // Fields Body
        JPanel fieldsPanel = new JPanel();
        fieldsPanel.setLayout(new BoxLayout(fieldsPanel, BoxLayout.Y_AXIS));
        fieldsPanel.setOpaque(false);
        fieldsPanel.add(Box.createVerticalStrut(22));

        // Username Label & Icon
        JLabel userLabel = new JLabel("Username");
        userLabel.setIcon(CampusIcons.get(CampusIcons.IconType.PROFILE, 14, CampusTheme.TEXT_SECONDARY));
        userLabel.setIconTextGap(6);
        userLabel.setFont(CampusTheme.FONT_BOLD);
        userLabel.setForeground(CampusTheme.TEXT_SECONDARY);
        userLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        fieldsPanel.add(userLabel);
        fieldsPanel.add(Box.createVerticalStrut(6));

        usernameField = new JTextField();
        styleInputField(usernameField);
        usernameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        fieldsPanel.add(usernameField);
        fieldsPanel.add(Box.createVerticalStrut(14));

        // Password Label & Icon
        JLabel passLabel = new JLabel("Password");
        passLabel.setIcon(CampusIcons.get(CampusIcons.IconType.PASSWORD, 14, CampusTheme.TEXT_SECONDARY));
        passLabel.setIconTextGap(6);
        passLabel.setFont(CampusTheme.FONT_BOLD);
        passLabel.setForeground(CampusTheme.TEXT_SECONDARY);
        passLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        fieldsPanel.add(passLabel);
        fieldsPanel.add(Box.createVerticalStrut(6));

        passwordField = new JPasswordField();
        styleInputField(passwordField);
        passwordField.setAlignmentX(Component.LEFT_ALIGNMENT);
        fieldsPanel.add(passwordField);
        fieldsPanel.add(Box.createVerticalStrut(8));

        // Caps Lock and Error / Status Indicators
        capsLockLabel = new JLabel("Caps Lock is ON");
        capsLockLabel.setIcon(CampusIcons.get(CampusIcons.IconType.WARNING, 12, CampusTheme.WARNING));
        capsLockLabel.setFont(CampusTheme.FONT_MICRO);
        capsLockLabel.setForeground(CampusTheme.WARNING);
        capsLockLabel.setVisible(Toolkit.getDefaultToolkit().getLockingKeyState(KeyEvent.VK_CAPS_LOCK));
        capsLockLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        fieldsPanel.add(capsLockLabel);
        fieldsPanel.add(Box.createVerticalStrut(4));

        statusLabel = new JLabel(" ");
        statusLabel.setFont(CampusTheme.FONT_SMALL_BOLD);
        statusLabel.setForeground(CampusTheme.ERROR);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        fieldsPanel.add(statusLabel);

        formCard.add(fieldsPanel, BorderLayout.CENTER);

        // Form Actions Footer
        JPanel actionsPanel = new JPanel();
        actionsPanel.setLayout(new BoxLayout(actionsPanel, BoxLayout.Y_AXIS));
        actionsPanel.setOpaque(false);

        loginButton = new PrimaryButton("Sign In to Portal");
        loginButton.setPreferredSize(new Dimension(340, 42));
        loginButton.setMaximumSize(new Dimension(Short.MAX_VALUE, 42));
        loginButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        loginButton.addActionListener(e -> performLogin());

        // Keyboard navigation and Caps Lock listener
        KeyAdapter enterSubmit = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                checkCapsLock(e);
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    performLogin();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                checkCapsLock(e);
            }
        };
        usernameField.addKeyListener(enterSubmit);
        passwordField.addKeyListener(enterSubmit);

        actionsPanel.add(loginButton);
        actionsPanel.add(Box.createVerticalStrut(10));

        // Secondary Exit option
        JPanel bottomRow = new JPanel(new BorderLayout());
        bottomRow.setOpaque(false);
        bottomRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel secureNote = new JLabel("Secure Institutional Access");
        secureNote.setIcon(CampusIcons.get(CampusIcons.IconType.CHECK, 12, CampusTheme.PRIMARY));
        secureNote.setFont(CampusTheme.FONT_MICRO);
        secureNote.setForeground(CampusTheme.TEXT_MUTED);
        bottomRow.add(secureNote, BorderLayout.WEST);

        JButton exitBtn = new SecondaryButton("Exit");
        exitBtn.setPreferredSize(new Dimension(72, 28));
        exitBtn.setFont(CampusTheme.FONT_SMALL);
        exitBtn.addActionListener(e -> System.exit(0));
        bottomRow.add(exitBtn, BorderLayout.EAST);

        actionsPanel.add(bottomRow);

        formCard.add(actionsPanel, BorderLayout.SOUTH);

        container.add(formCard);
        setContentPane(container);
    }

    private void checkCapsLock(KeyEvent e) {
        try {
            boolean isCaps = Toolkit.getDefaultToolkit().getLockingKeyState(KeyEvent.VK_CAPS_LOCK);
            capsLockLabel.setVisible(isCaps);
        } catch (Exception ignored) {
            // Ignore on platforms without locking key state support
        }
    }

    private JPanel createFeatureItem(String text) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        item.setOpaque(false);
        item.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel icon = new JLabel(CampusIcons.get(CampusIcons.IconType.CHECK, 14, CampusTheme.PRIMARY));
        item.add(icon);

        JLabel label = new JLabel(text);
        label.setFont(CampusTheme.FONT_SMALL);
        label.setForeground(CampusTheme.TEXT_PRIMARY);
        item.add(label);

        return item;
    }

    private void styleInputField(JTextField field) {
        field.setFont(CampusTheme.FONT_BODY);
        field.setPreferredSize(new Dimension(340, 38));
        field.setMaximumSize(new Dimension(Short.MAX_VALUE, 38));
        field.setBorder(new CompoundBorder(
                new LineBorder(CampusTheme.BORDER, 1, true),
                new EmptyBorder(6, 12, 6, 12)
        ));
        field.setBackground(Color.WHITE);
        field.setForeground(CampusTheme.TEXT_PRIMARY);
        field.setCaretColor(CampusTheme.PRIMARY);
    }

    private void performLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty()) {
            statusLabel.setText("Please enter your username.");
            usernameField.requestFocusInWindow();
            return;
        }
        if (password.isEmpty()) {
            statusLabel.setText("Please enter your password.");
            passwordField.requestFocusInWindow();
            return;
        }

        statusLabel.setText("Authenticating with institutional directory...");
        loginButton.setEnabled(false);

        SwingUtils.executeAsync(
                this,
                () -> authService.login(username, password),
                user -> {
                    loginButton.setEnabled(true);
                    statusLabel.setText(" ");
                    if (user.isMustChangePassword()) {
                        promptForcedPasswordChange(user);
                    } else {
                        launchMainFrame(user);
                    }
                },
                error -> {
                    loginButton.setEnabled(true);
                    passwordField.setText("");
                    passwordField.requestFocusInWindow();
                    statusLabel.setText(error.getMessage());
                }
        );
    }

    private void promptForcedPasswordChange(User user) {
        ForcedPasswordChangeDialog dialog = new ForcedPasswordChangeDialog(this, authService, user);
        dialog.setVisible(true);
        if (dialog.isPasswordSuccessfullyChanged()) {
            user.setMustChangePassword(false);
            launchMainFrame(user);
        }
    }

    private void launchMainFrame(User user) {
        dispose();
        SwingUtilities.invokeLater(() -> {
            MainFrame mainFrame = new MainFrame(user, authService);
            mainFrame.setVisible(true);
        });
    }

    @Override
    public void setVisible(boolean b) {
        super.setVisible(b);
        if (b && !GraphicsEnvironment.isHeadless()) {
            SwingUtilities.invokeLater(this::checkAndPromptFirstRunAdmin);
        }
    }

    /**
     * Checks if an active administrator exists. If not, displays the first-run administrator
     * setup dialog to prompt for secure initial administrator creation.
     */
    public void checkAndPromptFirstRunAdmin() {
        if (!authService.hasAdminUser()) {
            FirstRunAdminSetupDialog dialog = new FirstRunAdminSetupDialog(this, authService);
            dialog.setVisible(true);
            if (dialog.isSuccessfullyProvisioned()) {
                usernameField.setText(dialog.getProvisionedUsername());
                passwordField.setText("");
                passwordField.requestFocusInWindow();
                statusLabel.setText("Administrator ready. Please sign in.");
            }
        }
    }

    /**
     * Modal dialog prompting for initial system administrator account creation
     * on fresh unprovisioned database installations.
     */
    public static class FirstRunAdminSetupDialog extends JDialog {
        private final AuthService authService;
        private JTextField usernameField;
        private JPasswordField passwordField;
        private JPasswordField confirmPasswordField;
        private PrimaryButton submitButton;
        private JLabel errorLabel;
        private boolean provisioned = false;
        private String provisionedUsername = "";

        public FirstRunAdminSetupDialog(Frame parent, AuthService authService) {
            super(parent, "Initial Administrator Setup", true);
            this.authService = authService;
            initUI();
        }

        private void initUI() {
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    int opt = JOptionPane.showConfirmDialog(
                            FirstRunAdminSetupDialog.this,
                            "An initial administrator must be created before Campus ERP can be accessed. Exit application?",
                            "Exit Setup",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE);
                    if (opt == JOptionPane.YES_OPTION) {
                        System.exit(0);
                    }
                }
            });

            setSize(480, 420);
            setLocationRelativeTo(getParent());
            setResizable(false);

            JPanel panel = new JPanel(new BorderLayout(16, 16));
            panel.setBackground(CampusTheme.BG_CARD);
            panel.setBorder(new EmptyBorder(24, 28, 24, 28));

            JPanel header = new JPanel(new GridLayout(2, 1, 4, 4));
            header.setOpaque(false);

            JLabel title = new JLabel("Initial Administrator Setup", SwingConstants.CENTER);
            title.setFont(CampusTheme.FONT_TITLE);
            title.setForeground(CampusTheme.TEXT_PRIMARY);

            JLabel subtitle = new JLabel(
                    "<html><center>No administrator account exists. Create the primary institutional administrator.<br/>" +
                    "<small style='color:#68756D'>Password: 8+ chars, upper, lower, digit, and special symbol.</small></center></html>",
                    SwingConstants.CENTER);
            subtitle.setFont(CampusTheme.FONT_SMALL);

            header.add(title);
            header.add(subtitle);
            panel.add(header, BorderLayout.NORTH);

            JPanel form = new JPanel(new GridBagLayout());
            form.setOpaque(false);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(8, 4, 8, 4);

            JLabel lblUser = new JLabel("Admin Username:");
            lblUser.setFont(CampusTheme.FONT_BOLD);
            lblUser.setForeground(CampusTheme.TEXT_SECONDARY);
            usernameField = new JTextField("admin", 15);
            styleInputField(usernameField);

            JLabel lblPass = new JLabel("Admin Password:");
            lblPass.setFont(CampusTheme.FONT_BOLD);
            lblPass.setForeground(CampusTheme.TEXT_SECONDARY);
            passwordField = new JPasswordField(15);
            styleInputField(passwordField);

            JLabel lblConfirm = new JLabel("Confirm Password:");
            lblConfirm.setFont(CampusTheme.FONT_BOLD);
            lblConfirm.setForeground(CampusTheme.TEXT_SECONDARY);
            confirmPasswordField = new JPasswordField(15);
            styleInputField(confirmPasswordField);

            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.4;
            form.add(lblUser, gbc);
            gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.6;
            form.add(usernameField, gbc);

            gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.4;
            form.add(lblPass, gbc);
            gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0.6;
            form.add(passwordField, gbc);

            gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.4;
            form.add(lblConfirm, gbc);
            gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 0.6;
            form.add(confirmPasswordField, gbc);

            panel.add(form, BorderLayout.CENTER);

            JPanel footer = new JPanel(new BorderLayout(8, 8));
            footer.setOpaque(false);

            errorLabel = new JLabel(" ", SwingConstants.CENTER);
            errorLabel.setFont(CampusTheme.FONT_SMALL_BOLD);
            errorLabel.setForeground(CampusTheme.ERROR);
            footer.add(errorLabel, BorderLayout.NORTH);

            submitButton = new PrimaryButton("Create Administrator Account");
            submitButton.setPreferredSize(new Dimension(200, 38));
            submitButton.addActionListener(e -> provisionAdmin());
            footer.add(submitButton, BorderLayout.SOUTH);

            panel.add(footer, BorderLayout.SOUTH);
            setContentPane(panel);
        }

        private void styleInputField(JTextField field) {
            field.setFont(CampusTheme.FONT_BODY);
            field.setPreferredSize(new Dimension(220, 34));
            field.setBorder(new CompoundBorder(
                    new LineBorder(CampusTheme.BORDER, 1, true),
                    new EmptyBorder(4, 8, 4, 8)
            ));
        }

        private void provisionAdmin() {
            String user = usernameField.getText().trim();
            String pass = new String(passwordField.getPassword());
            String confirm = new String(confirmPasswordField.getPassword());

            if (user.isEmpty()) {
                errorLabel.setText("Username cannot be empty.");
                return;
            }
            if (!pass.equals(confirm)) {
                errorLabel.setText("Passwords do not match.");
                return;
            }

            try {
                com.college.util.ValidationUtil.validateStrongPassword(pass);
            } catch (Exception ex) {
                errorLabel.setText("<html><center>" + ex.getMessage() + "</center></html>");
                return;
            }

            errorLabel.setText("Provisioning administrator...");
            submitButton.setEnabled(false);

            SwingUtils.executeAsync(
                    this,
                    () -> authService.provisionInitialAdmin(user, pass),
                    newAdmin -> {
                        submitButton.setEnabled(true);
                        provisioned = true;
                        provisionedUsername = user;
                        SwingUtils.showInfo(this, "Administrator '" + user + "' provisioned successfully. You may now sign in.");
                        dispose();
                    },
                    error -> {
                        submitButton.setEnabled(true);
                        errorLabel.setText(error.getMessage());
                    }
            );
        }

        public boolean isSuccessfullyProvisioned() {
            return provisioned;
        }

        public String getProvisionedUsername() {
            return provisionedUsername;
        }
    }

    /**
     * Non-cancellable modal dialog enforcing mandatory first-login password change.
     */
    private static class ForcedPasswordChangeDialog extends JDialog {
        private final AuthService authService;
        private final User user;
        private final JPasswordField newPasswordField;
        private final JPasswordField confirmPasswordField;
        private final JLabel errorLabel;
        private final PrimaryButton submitButton;
        private boolean passwordChanged = false;

        public ForcedPasswordChangeDialog(Frame parent, AuthService authService, User user) {
            super(parent, "Mandatory Password Change", true);
            this.authService = authService;
            this.user = user;

            setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    int opt = JOptionPane.showConfirmDialog(
                            ForcedPasswordChangeDialog.this,
                            "A password change is required to continue. Exiting will terminate the application. Proceed?",
                            "Exit Confirmation",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE);
                    if (opt == JOptionPane.YES_OPTION) {
                        System.exit(0);
                    }
                }
            });

            setSize(440, 320);
            setLocationRelativeTo(parent);
            setResizable(false);

            JPanel panel = new JPanel(new BorderLayout(14, 14));
            panel.setBackground(CampusTheme.BG_CARD);
            panel.setBorder(new EmptyBorder(24, 28, 24, 28));

            JLabel title = new JLabel("Password Change Required", SwingConstants.CENTER);
            title.setFont(CampusTheme.FONT_TITLE);
            title.setForeground(CampusTheme.TEXT_PRIMARY);

            JLabel subtitle = new JLabel(
                    "<html><center>Your account requires a password change before you can access system functions.<br/>" +
                    "<small style='color:#68756D'>Password: 8+ chars, upper, lower, digit, and special symbol.</small></center></html>",
                    SwingConstants.CENTER);
            subtitle.setFont(CampusTheme.FONT_SMALL);

            JPanel header = new JPanel(new GridLayout(2, 1, 4, 4));
            header.setOpaque(false);
            header.add(title);
            header.add(subtitle);
            panel.add(header, BorderLayout.NORTH);

            JPanel form = new JPanel(new GridBagLayout());
            form.setOpaque(false);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(8, 4, 8, 4);

            JLabel lblNew = new JLabel("New Password:");
            lblNew.setFont(CampusTheme.FONT_BOLD);
            lblNew.setForeground(CampusTheme.TEXT_SECONDARY);
            newPasswordField = new JPasswordField(15);
            styleInputField(newPasswordField);

            JLabel lblConfirm = new JLabel("Confirm Password:");
            lblConfirm.setFont(CampusTheme.FONT_BOLD);
            lblConfirm.setForeground(CampusTheme.TEXT_SECONDARY);
            confirmPasswordField = new JPasswordField(15);
            styleInputField(confirmPasswordField);

            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.4;
            form.add(lblNew, gbc);
            gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.6;
            form.add(newPasswordField, gbc);

            gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.4;
            form.add(lblConfirm, gbc);
            gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0.6;
            form.add(confirmPasswordField, gbc);

            panel.add(form, BorderLayout.CENTER);

            JPanel footer = new JPanel(new BorderLayout(8, 8));
            footer.setOpaque(false);

            errorLabel = new JLabel(" ", SwingConstants.CENTER);
            errorLabel.setFont(CampusTheme.FONT_SMALL_BOLD);
            errorLabel.setForeground(CampusTheme.ERROR);
            footer.add(errorLabel, BorderLayout.NORTH);

            submitButton = new PrimaryButton("Update Password & Continue");
            submitButton.setPreferredSize(new Dimension(220, 38));
            submitButton.addActionListener(e -> updatePassword());
            footer.add(submitButton, BorderLayout.SOUTH);

            panel.add(footer, BorderLayout.SOUTH);
            setContentPane(panel);
        }

        private void styleInputField(JTextField field) {
            field.setFont(CampusTheme.FONT_BODY);
            field.setPreferredSize(new Dimension(220, 34));
            field.setBorder(new CompoundBorder(
                    new LineBorder(CampusTheme.BORDER, 1, true),
                    new EmptyBorder(4, 8, 4, 8)
            ));
        }

        private void updatePassword() {
            String newPass = new String(newPasswordField.getPassword());
            String confirmPass = new String(confirmPasswordField.getPassword());

            if (!newPass.equals(confirmPass)) {
                errorLabel.setText("Passwords do not match.");
                return;
            }

            try {
                com.college.util.ValidationUtil.validateStrongPassword(newPass);
            } catch (Exception ex) {
                errorLabel.setText("<html><center>" + ex.getMessage() + "</center></html>");
                return;
            }

            errorLabel.setText("Updating password...");
            submitButton.setEnabled(false);

            SwingUtils.executeAsync(
                    this,
                    () -> {
                        authService.forceChangePassword(user, newPass);
                        return null;
                    },
                    unused -> {
                        submitButton.setEnabled(true);
                        passwordChanged = true;
                        SwingUtils.showInfo(this, "Password updated successfully. Logging in...");
                        dispose();
                    },
                    error -> {
                        submitButton.setEnabled(true);
                        errorLabel.setText(error.getMessage());
                    }
            );
        }

        public boolean isPasswordSuccessfullyChanged() {
            return passwordChanged;
        }
    }
}
