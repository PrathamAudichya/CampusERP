package com.college.ui.panel;

import com.college.dao.UserDAO;
import com.college.model.Role;
import com.college.model.User;
import com.college.model.UserDirectoryItem;
import com.college.service.AuthService;
import com.college.ui.components.CampusIcons;
import com.college.ui.components.PrimaryButton;
import com.college.ui.components.SecondaryButton;
import com.college.ui.components.StatusBadge;
import com.college.ui.dialog.AdminCredentialHelpDialog;
import com.college.ui.dialog.TemporaryPasswordDialog;
import com.college.ui.theme.CampusTheme;
import com.college.ui.util.SwingUtils;
import com.college.util.UsernameUtil;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Institutional User Directory & Account Governance Panel for Administrators.
 *
 * <p>Provides a comprehensive view of all system accounts joined with linked academic
 * profiles (students, faculty, and departments). Supports real-time text searching, role filtering,
 * modal profile inspection, administrative password reset with one-time credential display,
 * mandatory password rotation enforcement, and account activation management.</p>
 */
public class UserManagementPanel extends JPanel {

    private final User currentUser;
    private final UserDAO userDAO;
    private final AuthService authService;

    private JTable userTable;
    private DefaultTableModel userModel;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField searchField;
    private JComboBox<String> roleFilterCombo;
    private JButton refreshButton;
    private JButton viewProfileBtn;
    private JButton editUsernameBtn;
    private JButton resetPasswordBtn;
    private JButton forceChangePassBtn;
    private JButton toggleActiveButton;
    private JButton standardizeBtn;
    private JButton helpButton;
    private JLabel countLabel;

    private List<UserDirectoryItem> currentItems = new ArrayList<>();

    public UserManagementPanel(User currentUser) {
        this(currentUser, new UserDAO(), new AuthService());
    }

    public UserManagementPanel(User currentUser, UserDAO userDAO) {
        this(currentUser, userDAO, new AuthService());
    }

    public UserManagementPanel(User currentUser, UserDAO userDAO, AuthService authService) {
        this.currentUser = currentUser;
        this.userDAO = userDAO;
        this.authService = authService;
        AuthService.requireRole(currentUser, Role.ADMIN);
        initUI();
        loadUsers();
    }

    private void initUI() {
        setLayout(new BorderLayout(16, 16));
        setBackground(CampusTheme.BG_MAIN);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        // =====================================================================
        // Top Header and Controls Area
        // =====================================================================
        JPanel topPanel = new JPanel(new BorderLayout(16, 12));
        topPanel.setOpaque(false);

        // Titles and Help Button
        JPanel titleRow = new JPanel(new BorderLayout(12, 4));
        titleRow.setOpaque(false);

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 2, 2));
        titleBlock.setOpaque(false);

        JLabel titleLabel = new JLabel("Institutional User Directory & Identity Management");
        titleLabel.setFont(CampusTheme.FONT_TITLE);
        titleLabel.setForeground(CampusTheme.NAVY);

        JLabel subtitleLabel = new JLabel("Comprehensive account governance, credential management, and role-based directory.");
        subtitleLabel.setFont(CampusTheme.FONT_SMALL);
        subtitleLabel.setForeground(CampusTheme.TEXT_MUTED);

        titleBlock.add(titleLabel);
        titleBlock.add(subtitleLabel);
        titleRow.add(titleBlock, BorderLayout.CENTER);

        helpButton = new SecondaryButton("How Login Credentials Work");
        helpButton.setIcon(CampusIcons.get(CampusIcons.IconType.PASSWORD, 14, CampusTheme.TEXT_SECONDARY));
        helpButton.setIconTextGap(6);
        helpButton.addActionListener(e -> {
            AdminCredentialHelpDialog helpDialog = new AdminCredentialHelpDialog(SwingUtilities.getWindowAncestor(this));
            helpDialog.setVisible(true);
        });
        titleRow.add(helpButton, BorderLayout.EAST);

        topPanel.add(titleRow, BorderLayout.NORTH);

        // Filter & Search Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        toolbar.setOpaque(false);

        JLabel searchLbl = new JLabel("Search:");
        searchLbl.setFont(CampusTheme.FONT_BOLD);
        toolbar.add(searchLbl);

        searchField = new JTextField(18);
        searchField.setFont(CampusTheme.FONT_BODY);
        searchField.setToolTipText("Filter by Name, Username, Natural ID, Email, or Department");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { applyFilters(); }
            @Override
            public void removeUpdate(DocumentEvent e) { applyFilters(); }
            @Override
            public void changedUpdate(DocumentEvent e) { applyFilters(); }
        });
        toolbar.add(searchField);

        toolbar.add(Box.createHorizontalStrut(8));

        JLabel roleLbl = new JLabel("Role:");
        roleLbl.setFont(CampusTheme.FONT_BOLD);
        toolbar.add(roleLbl);

        roleFilterCombo = new JComboBox<>(new String[]{"ALL ROLES", "ADMIN", "FACULTY", "STUDENT"});
        roleFilterCombo.setFont(CampusTheme.FONT_BODY);
        roleFilterCombo.addActionListener(e -> applyFilters());
        toolbar.add(roleFilterCombo);

        toolbar.add(Box.createHorizontalStrut(8));

        refreshButton = new SecondaryButton("Refresh");
        refreshButton.setIcon(CampusIcons.get(CampusIcons.IconType.REFRESH, 13, CampusTheme.TEXT_SECONDARY));
        refreshButton.setIconTextGap(6);
        refreshButton.addActionListener(e -> loadUsers());
        toolbar.add(refreshButton);

        countLabel = new JLabel("Loading accounts...");
        countLabel.setFont(CampusTheme.FONT_MICRO);
        countLabel.setForeground(CampusTheme.TEXT_SECONDARY);
        toolbar.add(Box.createHorizontalStrut(12));
        toolbar.add(countLabel);

        topPanel.add(toolbar, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        // =====================================================================
        // Center Table Card (10 Columns)
        // =====================================================================
        JPanel card = SwingUtils.createCardPanel("All System User Accounts");
        String[] cols = {
                "User ID", "Full Name", "Username", "Registration ID / Faculty ID", "Role",
                "Department", "Email", "Account Status", "Must Change Password", "Account Creation Status"
        };
        userModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        userTable = SwingUtils.createStandardTable(userModel);
        sorter = new TableRowSorter<>(userModel);
        userTable.setRowSorter(sorter);

        // Column width configurations
        userTable.getColumnModel().getColumn(0).setPreferredWidth(55);  // User ID
        userTable.getColumnModel().getColumn(1).setPreferredWidth(140); // Full Name
        userTable.getColumnModel().getColumn(2).setPreferredWidth(110); // Username
        userTable.getColumnModel().getColumn(3).setPreferredWidth(120); // Registration ID / Faculty ID
        userTable.getColumnModel().getColumn(4).setPreferredWidth(85);  // Role
        userTable.getColumnModel().getColumn(5).setPreferredWidth(125); // Department
        userTable.getColumnModel().getColumn(6).setPreferredWidth(140); // Email
        userTable.getColumnModel().getColumn(7).setPreferredWidth(95);  // Account Status
        userTable.getColumnModel().getColumn(8).setPreferredWidth(110); // Must Change Password
        userTable.getColumnModel().getColumn(9).setPreferredWidth(110); // Account Creation Status

        // Status renderer for Role
        userTable.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                if (value != null) {
                    return StatusBadge.of(value.toString());
                }
                return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            }
        });

        // Status renderer for Account Status
        userTable.getColumnModel().getColumn(7).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                if (value != null) {
                    return StatusBadge.of(value.toString());
                }
                return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            }
        });

        // Status renderer for Must Change Password (YES / NO badge)
        userTable.getColumnModel().getColumn(8).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                if (value != null) {
                    return StatusBadge.of(value.toString());
                }
                return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            }
        });

        // Status renderer for Account Creation Status (YES / NO badge)
        userTable.getColumnModel().getColumn(9).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                if (value != null) {
                    return StatusBadge.of(value.toString());
                }
                return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            }
        });

        card.add(new JScrollPane(userTable), BorderLayout.CENTER);

        // =====================================================================
        // Bottom Action Bar
        // =====================================================================
        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        actionBar.setOpaque(false);

        viewProfileBtn = new SecondaryButton("View Profile Details");
        viewProfileBtn.setIcon(CampusIcons.get(CampusIcons.IconType.PROFILE, 14, CampusTheme.TEXT_SECONDARY));
        viewProfileBtn.setIconTextGap(6);
        viewProfileBtn.addActionListener(e -> showSelectedUserProfile());
        actionBar.add(viewProfileBtn);

        editUsernameBtn = new SecondaryButton("Edit Username");
        editUsernameBtn.addActionListener(e -> editSelectedUserUsername());
        actionBar.add(editUsernameBtn);

        resetPasswordBtn = new PrimaryButton("Reset Password");
        resetPasswordBtn.setIcon(CampusIcons.get(CampusIcons.IconType.PASSWORD, 14, Color.WHITE));
        resetPasswordBtn.setIconTextGap(6);
        resetPasswordBtn.addActionListener(e -> resetSelectedUserPassword());
        actionBar.add(resetPasswordBtn);

        forceChangePassBtn = new SecondaryButton("Force Password Change");
        forceChangePassBtn.addActionListener(e -> forcePasswordChangeOnNextLogin());
        actionBar.add(forceChangePassBtn);

        toggleActiveButton = new SecondaryButton("Activate / Deactivate");
        toggleActiveButton.addActionListener(e -> toggleSelectedUserStatus());
        actionBar.add(toggleActiveButton);

        standardizeBtn = new SecondaryButton("Standardize Usernames");
        standardizeBtn.setToolTipText("Preview and standardize legacy usernames to institutional format (AA999999999999)");
        standardizeBtn.addActionListener(e -> showStandardizeUsernamesDialog());
        actionBar.add(standardizeBtn);

        card.add(actionBar, BorderLayout.SOUTH);
        add(card, BorderLayout.CENTER);
    }

    private void loadUsers() {
        if (refreshButton != null) refreshButton.setEnabled(false);
        countLabel.setText("Refreshing accounts from database...");

        SwingUtils.executeAsync(
                this,
                () -> authService.getUserDirectory(currentUser),
                items -> {
                    if (refreshButton != null) refreshButton.setEnabled(true);
                    this.currentItems = items;
                    applyFilters();
                },
                error -> {
                    if (refreshButton != null) refreshButton.setEnabled(true);
                    countLabel.setText("Failed to load accounts.");
                    SwingUtils.showError(this, "Failed to load institutional user directory: " + error.getMessage());
                }
        );
    }

    private void applyFilters() {
        if (currentItems == null) return;

        String selectedRole = (String) roleFilterCombo.getSelectedItem();
        String searchText = searchField.getText().trim().toLowerCase();

        List<UserDirectoryItem> filtered = currentItems.stream().filter(item -> {
            // Role Filter
            if (selectedRole != null && !"ALL ROLES".equalsIgnoreCase(selectedRole)) {
                if (!item.getRole().name().equalsIgnoreCase(selectedRole)) {
                    return false;
                }
            }
            // Search Query Filter
            if (!searchText.isEmpty()) {
                boolean matchesUser = item.getUsername() != null && item.getUsername().toLowerCase().contains(searchText);
                boolean matchesName = item.getFullName() != null && item.getFullName().toLowerCase().contains(searchText);
                boolean matchesNaturalId = item.getNaturalId() != null && item.getNaturalId().toLowerCase().contains(searchText);
                boolean matchesEmail = item.getEmail() != null && item.getEmail().toLowerCase().contains(searchText);
                boolean matchesDept = item.getDepartmentName() != null && item.getDepartmentName().toLowerCase().contains(searchText);
                boolean matchesDeptCode = item.getDepartmentCode() != null && item.getDepartmentCode().toLowerCase().contains(searchText);
                if (!matchesUser && !matchesName && !matchesNaturalId && !matchesEmail && !matchesDept && !matchesDeptCode) {
                    return false;
                }
            }
            return true;
        }).collect(Collectors.toList());

        userModel.setRowCount(0);
        for (UserDirectoryItem item : filtered) {
            userModel.addRow(new Object[]{
                    item.getUserId(),
                    item.getFullName(),
                    item.getUsername(),
                    item.getDisplayNaturalId(),
                    item.getRole() != null ? item.getRole().name() : "—",
                    item.getDisplayDepartment(),
                    item.getEmail() != null ? item.getEmail() : "—",
                    !item.isAccountCreated() ? "UNPROVISIONED" : (item.isActive() ? "ACTIVE" : "INACTIVE"),
                    !item.isAccountCreated() ? "—" : (item.isMustChangePassword() ? "YES" : "NO"),
                    item.getDisplayAccountCreationStatus()
            });
        }

        countLabel.setText(String.format("Showing %d of %d accounts", filtered.size(), currentItems.size()));
    }

    private UserDirectoryItem getSelectedDirectoryItem() {
        int selectedRow = userTable.getSelectedRow();
        if (selectedRow < 0) return null;
        int modelRow = userTable.convertRowIndexToModel(selectedRow);
        int userId = (int) userModel.getValueAt(modelRow, 0);
        String naturalId = (String) userModel.getValueAt(modelRow, 3);
        if (userId > 0) {
            return currentItems.stream().filter(it -> it.getUserId() == userId).findFirst().orElse(null);
        } else {
            return currentItems.stream().filter(it -> it.getDisplayNaturalId().equals(naturalId)).findFirst().orElse(null);
        }
    }

    private void showSelectedUserProfile() {
        UserDirectoryItem item = getSelectedDirectoryItem();
        if (item == null) {
            SwingUtils.showWarning(this, "Please select a user account to view profile details.");
            return;
        }

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "User Profile — " + item.getUsername(), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(480, 480);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(CampusTheme.BG_CARD);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(20, 24, 16, 24));
        content.setOpaque(false);

        JLabel title = new JLabel(item.getFullName());
        title.setFont(CampusTheme.FONT_TITLE);
        title.setForeground(CampusTheme.PRIMARY);
        content.add(title);

        JLabel sub = new JLabel("Role: " + item.getRole().name() + " • Status: " + (item.isActive() ? "ACTIVE" : "INACTIVE"));
        sub.setFont(CampusTheme.FONT_SMALL);
        sub.setForeground(CampusTheme.TEXT_SECONDARY);
        content.add(sub);
        content.add(Box.createVerticalStrut(14));

        JPanel grid = new JPanel(new GridLayout(8, 2, 8, 8));
        grid.setOpaque(false);

        addDetailRow(grid, "User Account ID:", String.valueOf(item.getUserId()));
        addDetailRow(grid, "Assigned Username:", item.getUsername());
        addDetailRow(grid, "Institutional ID:", item.getDisplayNaturalId());
        addDetailRow(grid, "Department:", item.getDisplayDepartment());
        addDetailRow(grid, "Email Address:", item.getEmail() != null ? item.getEmail() : "—");
        addDetailRow(grid, "Phone Number:", item.getPhone() != null ? item.getPhone() : "—");
        addDetailRow(grid, "Must Change Password:", item.isMustChangePassword() ? "Yes (Required on next login)" : "No");
        addDetailRow(grid, "Account Created On:", item.getCreatedAt() != null ? item.getCreatedAt() : "—");

        content.add(grid);
        dialog.add(content, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        btnPanel.setBackground(CampusTheme.BG_MAIN);
        btnPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, CampusTheme.BORDER));

        if (item.getUserId() > 0) {
            JButton editUserBtn = new SecondaryButton("Edit Username");
            editUserBtn.setIcon(CampusIcons.get(CampusIcons.IconType.PROFILE, 14, CampusTheme.TEXT_SECONDARY));
            editUserBtn.addActionListener(e -> {
                dialog.dispose();
                editSelectedUserUsername();
            });
            btnPanel.add(editUserBtn);
        }

        JButton close = new PrimaryButton("Close");
        close.setPreferredSize(new Dimension(90, 32));
        close.addActionListener(e -> dialog.dispose());
        btnPanel.add(close);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void addDetailRow(JPanel grid, String label, String value) {
        JLabel l = new JLabel(label);
        l.setFont(CampusTheme.FONT_BOLD);
        l.setForeground(CampusTheme.TEXT_SECONDARY);

        JTextField v = new JTextField(value);
        v.setEditable(false);
        v.setFont(CampusTheme.FONT_BODY);
        v.setBackground(Color.WHITE);
        v.setBorder(new CompoundBorder(new LineBorder(CampusTheme.BORDER, 1), new EmptyBorder(3, 6, 3, 6)));

        grid.add(l);
        grid.add(v);
    }

    private void resetSelectedUserPassword() {
        UserDirectoryItem item = getSelectedDirectoryItem();
        if (item == null) {
            SwingUtils.showWarning(this, "Please select a user account to reset password.");
            return;
        }

        if (!item.isActive()) {
            SwingUtils.showWarning(this, "Cannot reset password for inactive user account '" + item.getUsername() + "'.");
            return;
        }

        // Option: Auto-generate or custom password
        String[] options = {"Auto-Generate Secure Password (Recommended)", "Enter Custom Temporary Password", "Cancel"};
        int choice = JOptionPane.showOptionDialog(
                this,
                "Reset temporary password for user: " + item.getUsername() + " (" + item.getFullName() + ")?\n" +
                "The user will be required to change this password on next login.",
                "Confirm Credential Reset",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) return;

        String customPassword = null;
        if (choice == 1) {
            customPassword = JOptionPane.showInputDialog(
                    this,
                    "Enter new temporary password (min 8 characters):",
                    "Custom Temporary Password",
                    JOptionPane.PLAIN_MESSAGE
            );
            if (customPassword == null) return;
            customPassword = customPassword.trim();
            if (customPassword.length() < 8) {
                SwingUtils.showError(this, "Password must be at least 8 characters long.");
                return;
            }
        }

        final String passToApply = customPassword;
        SwingUtils.executeAsync(
                this,
                () -> authService.resetUserPassword(currentUser, item.getUserId(), passToApply),
                generatedTempPass -> {
                    loadUsers();
                    TemporaryPasswordDialog dlg = new TemporaryPasswordDialog(
                            SwingUtilities.getWindowAncestor(this),
                            item.getDisplayNaturalId(),
                            item.getUsername(),
                            generatedTempPass,
                            "Temporary Password Reset"
                    );
                    dlg.setVisible(true);
                },
                error -> SwingUtils.showError(this, "Failed to reset password: " + error.getMessage())
        );
    }

    private void forcePasswordChangeOnNextLogin() {
        UserDirectoryItem item = getSelectedDirectoryItem();
        if (item == null) {
            SwingUtils.showWarning(this, "Please select a user account.");
            return;
        }

        boolean confirm = SwingUtils.showConfirm(
                this,
                "Require user '" + item.getUsername() + "' to change password on their next login?",
                "Force Password Rotation"
        );
        if (!confirm) return;

        SwingUtils.executeAsync(
                this,
                () -> authService.forcePasswordChange(currentUser, item.getUserId()),
                success -> {
                    SwingUtils.showInfo(this, "Password change flagged successfully for '" + item.getUsername() + "'.");
                    loadUsers();
                },
                error -> SwingUtils.showError(this, "Failed to update policy: " + error.getMessage())
        );
    }

    private void toggleSelectedUserStatus() {
        UserDirectoryItem item = getSelectedDirectoryItem();
        if (item == null) {
            SwingUtils.showWarning(this, "Please select a user account to toggle status.");
            return;
        }

        int userId = item.getUserId();
        String username = item.getUsername();
        boolean newStatus = !item.isActive();

        if (userId == currentUser.getId() && !newStatus) {
            SwingUtils.showWarning(this, "You cannot deactivate your own administrative account.");
            return;
        }

        boolean confirm = SwingUtils.showConfirm(this,
                "Change status for '" + username + "' to " + (newStatus ? "ACTIVE" : "INACTIVE") + "?",
                "Confirm Account Status Change");
        if (!confirm) return;

        SwingUtils.executeAsync(
                this,
                () -> {
                    if (newStatus) {
                        authService.reactivateUser(currentUser, userId);
                    } else {
                        authService.deactivateUser(currentUser, userId);
                    }
                    return null;
                },
                unused -> {
                    SwingUtils.showInfo(this, "Account status updated successfully for '" + username + "'.");
                    loadUsers();
                },
                error -> SwingUtils.showError(this, "Failed to update status: " + error.getMessage())
        );
    }

    private void editSelectedUserUsername() {
        UserDirectoryItem item = getSelectedDirectoryItem();
        if (item == null) {
            SwingUtils.showWarning(this, "Please select a user account to edit username.");
            return;
        }

        if (item.getUserId() <= 0) {
            SwingUtils.showWarning(this, "Cannot edit username for unprovisioned account. Please provision user account first.");
            return;
        }

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Edit Username — " + item.getUsername(), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(520, 360);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(CampusTheme.BG_CARD);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(20, 24, 16, 24));
        content.setOpaque(false);

        JLabel title = new JLabel("Edit Account Username");
        title.setFont(CampusTheme.FONT_TITLE);
        title.setForeground(CampusTheme.PRIMARY);
        content.add(title);

        JLabel sub = new JLabel("User: " + item.getFullName() + " • Role: " + (item.getRole() != null ? item.getRole().name() : "—"));
        sub.setFont(CampusTheme.FONT_SMALL);
        sub.setForeground(CampusTheme.TEXT_SECONDARY);
        content.add(sub);
        content.add(Box.createVerticalStrut(14));

        JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
        form.setOpaque(false);

        form.add(new JLabel("Current Username:"));
        JTextField currentField = new JTextField(item.getUsername());
        currentField.setEditable(false);
        form.add(currentField);

        form.add(new JLabel("New Username:"));
        JTextField newUsernameField = new JTextField(item.getUsername());
        form.add(newUsernameField);

        form.add(new JLabel("Format Policy:"));
        JLabel formatNotice = new JLabel("AA999999999999 (14 chars) or custom");
        formatNotice.setFont(CampusTheme.FONT_MICRO);
        formatNotice.setForeground(CampusTheme.TEXT_MUTED);
        form.add(formatNotice);

        content.add(form);
        content.add(Box.createVerticalStrut(10));

        JPanel actionsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actionsRow.setOpaque(false);

        JButton generateBtn = new SecondaryButton("Generate Replacement (AA...)");
        generateBtn.setToolTipText("Generate unique institutional username in AA999999999999 format");
        generateBtn.addActionListener(e -> newUsernameField.setText(UsernameUtil.generateCandidateUsername()));
        actionsRow.add(generateBtn);

        JButton copyCurrentBtn = new SecondaryButton("Copy Current");
        copyCurrentBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(item.getUsername()), null);
            SwingUtils.showInfo(dialog, "Current username copied to clipboard.");
        });
        actionsRow.add(copyCurrentBtn);

        JButton copyNewBtn = new SecondaryButton("Copy New");
        copyNewBtn.addActionListener(e -> {
            String val = newUsernameField.getText().trim();
            if (!val.isEmpty()) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(val), null);
                SwingUtils.showInfo(dialog, "New username copied to clipboard.");
            }
        });
        actionsRow.add(copyNewBtn);

        content.add(actionsRow);

        dialog.add(content, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        btnPanel.setBackground(CampusTheme.BG_MAIN);
        btnPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, CampusTheme.BORDER));

        JButton saveBtn = new PrimaryButton("Save Username");
        saveBtn.addActionListener(e -> {
            String newUsername = newUsernameField.getText().trim();
            if (newUsername.isEmpty()) {
                SwingUtils.showWarning(dialog, "Username cannot be empty.");
                return;
            }
            if (newUsername.equals(item.getUsername())) {
                dialog.dispose();
                return;
            }

            boolean confirm = SwingUtils.showConfirm(dialog,
                    "Change username for " + item.getFullName() + " from '" + item.getUsername() + "' to '" + newUsername + "'?\n" +
                    "This change will be logged in the permanent audit trail.",
                    "Confirm Username Change");
            if (!confirm) return;

            saveBtn.setEnabled(false);
            SwingUtils.executeAsync(
                    dialog,
                    () -> authService.updateUsername(currentUser, item.getUserId(), newUsername),
                    success -> {
                        dialog.dispose();
                        SwingUtils.showInfo(this, "Username updated successfully to '" + newUsername + "'.");
                        loadUsers();
                    },
                    error -> {
                        saveBtn.setEnabled(true);
                        SwingUtils.showError(dialog, "Failed to update username: " + error.getMessage());
                    }
            );
        });

        JButton cancelBtn = new SecondaryButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());

        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void showStandardizeUsernamesDialog() {
        List<UserDirectoryItem> nonCompliant = currentItems.stream()
                .filter(item -> !UsernameUtil.isValidInstitutionalUsername(item.getUsername()))
                .collect(Collectors.toList());

        if (nonCompliant.isEmpty()) {
            SwingUtils.showInfo(this, "All user accounts currently comply with the 14-character institutional username policy (AA999999999999).");
            return;
        }

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Standardize Usernames to Institutional Format", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(680, 480);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(12, 12));
        dialog.getContentPane().setBackground(CampusTheme.BG_MAIN);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(16, 20, 8, 20));

        JLabel title = new JLabel("Institutional Username Standardization Preview");
        title.setFont(CampusTheme.FONT_TITLE);
        title.setForeground(CampusTheme.PRIMARY);
        header.add(title, BorderLayout.NORTH);

        JLabel sub = new JLabel("Found " + nonCompliant.size() + " accounts with legacy usernames. Preview proposed 14-character usernames (AA999999999999):");
        sub.setFont(CampusTheme.FONT_SMALL);
        sub.setForeground(CampusTheme.TEXT_SECONDARY);
        header.add(sub, BorderLayout.SOUTH);
        dialog.add(header, BorderLayout.NORTH);

        String[] cols = {"User ID", "Full Name", "Role", "Current Username", "Proposed Username"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return col == 4; }
        };

        for (UserDirectoryItem item : nonCompliant) {
            model.addRow(new Object[]{
                    item.getUserId(),
                    item.getFullName(),
                    item.getRole().name(),
                    item.getUsername(),
                    UsernameUtil.generateCandidateUsername()
            });
        }

        JTable table = SwingUtils.createStandardTable(model);
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        btnBar.setBackground(CampusTheme.BG_MAIN);
        btnBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, CampusTheme.BORDER));

        JButton exportBtn = new SecondaryButton("Export / Copy CSV");
        exportBtn.addActionListener(e -> {
            StringBuilder sb = new StringBuilder();
            sb.append("User ID,Full Name,Role,Current Username,Proposed Username\n");
            for (int r = 0; r < model.getRowCount(); r++) {
                sb.append(model.getValueAt(r, 0)).append(",")
                        .append("\"").append(model.getValueAt(r, 1)).append("\",")
                        .append(model.getValueAt(r, 2)).append(",")
                        .append(model.getValueAt(r, 3)).append(",")
                        .append(model.getValueAt(r, 4)).append("\n");
            }
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(sb.toString()), null);
            SwingUtils.showInfo(dialog, "Mapping exported to clipboard (" + model.getRowCount() + " rows in CSV format).");
        });

        JButton applyBtn = new PrimaryButton("Apply Standardization");
        applyBtn.addActionListener(e -> {
            boolean confirm = SwingUtils.showConfirm(dialog,
                    "Apply " + model.getRowCount() + " username updates now?\n" +
                    "Existing users must use their new usernames on subsequent logins.\n" +
                    "All changes will be permanently logged to the audit trail.",
                    "Confirm Standardization");
            if (!confirm) return;

            applyBtn.setEnabled(false);
            SwingUtils.executeAsync(
                    dialog,
                    () -> {
                        int count = 0;
                        for (int r = 0; r < model.getRowCount(); r++) {
                            int uid = (Integer) model.getValueAt(r, 0);
                            String newUsername = (String) model.getValueAt(r, 4);
                            authService.updateUsername(currentUser, uid, newUsername);
                            count++;
                        }
                        return count;
                    },
                    count -> {
                        dialog.dispose();
                        SwingUtils.showInfo(this, "Standardization complete: " + count + " usernames updated.");
                        loadUsers();
                    },
                    error -> {
                        applyBtn.setEnabled(true);
                        SwingUtils.showError(dialog, "Standardization error: " + error.getMessage());
                    }
            );
        });

        JButton closeBtn = new SecondaryButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());

        btnBar.add(exportBtn);
        btnBar.add(applyBtn);
        btnBar.add(closeBtn);
        dialog.add(btnBar, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }
}
