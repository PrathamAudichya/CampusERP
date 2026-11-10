package com.college.ui.panel;

import com.college.dao.FacultyDAO;
import com.college.model.Department;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.User;
import com.college.service.AuthService;
import com.college.service.DepartmentService;
import com.college.service.FacultyService;
import com.college.ui.components.CampusIcons;
import com.college.ui.components.FacultyDetailsDialog;
import com.college.ui.dialog.AdminCredentialHelpDialog;
import com.college.ui.dialog.TemporaryPasswordDialog;
import com.college.ui.theme.CampusTheme;
import com.college.ui.util.SwingUtils;
import com.college.util.PasswordUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Faculty management panel supporting Admin CRUD and Faculty own profile inspection.
 */
public class FacultyPanel extends JPanel {

    private final User currentUser;
    private final FacultyService facultyService;
    private final DepartmentService departmentService;
    private final FacultyDAO facultyDAO;
    private final AuthService authService;

    private JTable facultyTable;
    private DefaultTableModel tableModel;
    private JComboBox<DepartmentItem> deptFilterCombo;
    private JComboBox<DepartmentItem> deptFormCombo;

    private JTextField employeeIdField;
    private JTextField firstNameField;
    private JTextField lastNameField;
    private JTextField emailField;
    private JTextField phoneField;
    private JTextField hireDateField;
    private JTextField designationField;
    private JCheckBox autoGeneratePasswordCheck;
    private JPasswordField initialPasswordField;

    private JButton createButton;
    private JButton updateButton;
    private JButton deleteButton;
    private JButton reactivateButton;
    private JButton resetPasswordButton;
    private JButton refreshButton;
    private JButton viewDetailsButton;
    private JButton helpButton;
    private JLabel countBadge;
    private TableRowSorter<TableModel> rowSorter;
    private String currentSearchQuery = "";

    private Integer selectedFacultyPk = null;
    private final Map<Integer, String> departmentMap = new HashMap<>();
    private final Map<Integer, Faculty> facultyCache = new HashMap<>();

    public FacultyPanel(User currentUser) {
        this(currentUser, new FacultyService(), new DepartmentService(), new FacultyDAO(), new AuthService());
    }

    public FacultyPanel(User currentUser, FacultyService facultyService,
                        DepartmentService departmentService, FacultyDAO facultyDAO) {
        this(currentUser, facultyService, departmentService, facultyDAO, new AuthService());
    }

    public FacultyPanel(User currentUser, FacultyService facultyService,
                        DepartmentService departmentService, FacultyDAO facultyDAO,
                        AuthService authService) {
        this.currentUser = currentUser;
        this.facultyService = facultyService;
        this.departmentService = departmentService;
        this.facultyDAO = facultyDAO;
        this.authService = authService;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(12, 12));
        setBackground(SwingUtils.BG_LIGHT);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        if (currentUser.getRole() == Role.FACULTY) {
            initFacultyProfileView();
        } else {
            initAdminTableView();
        }
    }

    private void initFacultyProfileView() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JLabel titleLabel = new JLabel("My Faculty Profile & Institutional Identity");
        titleLabel.setFont(SwingUtils.TITLE_FONT);
        titleLabel.setForeground(SwingUtils.PRIMARY_COLOR);
        headerPanel.add(titleLabel, BorderLayout.WEST);
        add(headerPanel, BorderLayout.NORTH);

        JPanel scrollContent = new JPanel();
        scrollContent.setLayout(new BoxLayout(scrollContent, BoxLayout.Y_AXIS));
        scrollContent.setOpaque(false);

        // Card 1: Personal Information
        JPanel personalCard = SwingUtils.createCardPanel("Personal Information");
        JPanel personalGrid = new JPanel(new GridBagLayout());
        personalGrid.setOpaque(false);

        // Card 2: Professional Information
        JPanel profCard = SwingUtils.createCardPanel("Professional Information");
        JPanel profGrid = new JPanel(new GridBagLayout());
        profGrid.setOpaque(false);

        // Card 3: Department and Designation
        JPanel deptCard = SwingUtils.createCardPanel("Department and Designation");
        JPanel deptGrid = new JPanel(new GridBagLayout());
        deptGrid.setOpaque(false);

        // Card 4: Contact Information
        JPanel contactCard = SwingUtils.createCardPanel("Contact Information");
        JPanel contactGrid = new JPanel(new GridBagLayout());
        contactGrid.setOpaque(false);

        // Card 5: Institutional Identity
        JPanel identityCard = SwingUtils.createCardPanel("Institutional Identity");
        JPanel identityGrid = new JPanel(new GridBagLayout());
        identityGrid.setOpaque(false);

        // Card 6: Account Security & Credentials
        JPanel securityCard = SwingUtils.createCardPanel("Account Security");
        JPanel secGrid = new JPanel(new GridBagLayout());
        secGrid.setOpaque(false);

        // Card 7: Password Management
        JPanel pwdCard = SwingUtils.createCardPanel("Password Management");
        JPanel pwdPanel = new JPanel(new BorderLayout(12, 12));
        pwdPanel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 12, 8, 12);
        gbc.anchor = GridBagConstraints.WEST;

        SwingUtils.executeAsync(
                this,
                () -> {
                    Faculty f = facultyDAO.findByUserId(currentUser.getId());
                    Department d = (f != null && f.getDepartmentId() > 0) ? departmentService.getDepartmentById(currentUser, f.getDepartmentId()) : null;
                    return new Object[]{f, d};
                },
                result -> {
                    Faculty f = (Faculty) result[0];
                    Department d = (Department) result[1];
                    if (f == null) {
                        personalGrid.add(new JLabel("No faculty profile associated with this account."), gbc);
                        personalCard.add(personalGrid, BorderLayout.CENTER);
                        personalCard.revalidate();
                        return;
                    }

                    // Card 1: Personal Information
                    int r1 = 0;
                    addProfileRow(personalGrid, gbc, r1++, "Full Legal Name:", f.getFirstName() + " " + f.getLastName());
                    addProfileRow(personalGrid, gbc, r1++, "Date of Joining:", f.getJoinDate() != null ? f.getJoinDate() : "Not Recorded");
                    addProfileRow(personalGrid, gbc, r1++, "Employment Category:", "Full-Time Academic Faculty");
                    personalCard.add(personalGrid, BorderLayout.CENTER);
                    personalCard.revalidate(); personalCard.repaint();

                    // Card 2: Professional Information
                    int r2 = 0;
                    addProfileRow(profGrid, gbc, r2++, "Academic Specialization:", f.getSpecialization() != null ? f.getSpecialization() : "Core Disciplines");
                    addProfileRow(profGrid, gbc, r2++, "Faculty Appointment:", "Tenured / Regular Full-Time");
                    addProfileRow(profGrid, gbc, r2++, "Teaching Status:", f.isActive() ? "Active Teaching Faculty" : "On Leave / Inactive");
                    profCard.add(profGrid, BorderLayout.CENTER);
                    profCard.revalidate(); profCard.repaint();

                    // Card 3: Department and Designation
                    int r3d = 0;
                    addProfileRow(deptGrid, gbc, r3d++, "Academic Department:", (d != null ? d.getName() + " (" + d.getCode() + ")" : "General Faculty Division"));
                    addProfileRow(deptGrid, gbc, r3d++, "Academic Designation:", "Professor / Senior Faculty");
                    addProfileRow(deptGrid, gbc, r3d++, "Faculty Division:", "Academic Instruction & Research");
                    deptCard.add(deptGrid, BorderLayout.CENTER);
                    deptCard.revalidate(); deptCard.repaint();

                    // Card 4: Contact Information
                    int r4c = 0;
                    addProfileRow(contactGrid, gbc, r4c++, "Institutional Email:", f.getEmail());
                    addProfileRow(contactGrid, gbc, r4c++, "Contact Phone:", f.getPhone() != null && !f.getPhone().isBlank() ? f.getPhone() : "Not Provided");
                    addProfileRow(contactGrid, gbc, r4c++, "Faculty Office:", "Department Faculty Wing");
                    contactCard.add(contactGrid, BorderLayout.CENTER);
                    contactCard.revalidate(); contactCard.repaint();

                    // Card 5: Institutional Identity
                    int r3 = 0;
                    addProfileRow(identityGrid, gbc, r3++, "Faculty Identification No:", f.getFacultyId());
                    addProfileRow(identityGrid, gbc, r3++, "Institutional Username:", currentUser.getUsername());
                    addProfileRow(identityGrid, gbc, r3++, "System Role:", currentUser.getRole().name());
                    addProfileRow(identityGrid, gbc, r3++, "Identity Verification:", "Verified Faculty Member");
                    identityCard.add(identityGrid, BorderLayout.CENTER);
                    identityCard.revalidate(); identityCard.repaint();

                    // Card 6: Account Security & Credentials
                    int r4 = 0;
                    addProfileRow(secGrid, gbc, r4++, "Account Status:", currentUser.isActive() ? "Active & Verified" : "Suspended");
                    addProfileRow(secGrid, gbc, r4++, "Encryption Standard:", "BCrypt Cryptographic Hash (Adaptive Work Factor 12)");
                    addProfileRow(secGrid, gbc, r4++, "Password Policy:", "Minimum 8 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special symbol");
                    addProfileRow(secGrid, gbc, r4++, "Mandatory Password Reset:", currentUser.isMustChangePassword() ? "YES (Required on next action)" : "NO (Compliant)");
                    securityCard.add(secGrid, BorderLayout.CENTER);
                    securityCard.revalidate(); securityCard.repaint();

                    // Card 7: Password Management
                    JLabel pwdDesc = new JLabel("<html>Maintain security of academic evaluations, marks submissions, and attendance logs by updating your password regularly.</html>");
                    pwdDesc.setFont(CampusTheme.FONT_BODY);
                    pwdDesc.setForeground(CampusTheme.TEXT_SECONDARY);
                    pwdPanel.add(pwdDesc, BorderLayout.CENTER);

                    JPanel pwdBtnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 8));
                    pwdBtnRow.setOpaque(false);
                    JButton changePasswordBtn = SwingUtils.createPrimaryButton("Change Account Password");
                    changePasswordBtn.addActionListener(e -> showSelfServicePasswordDialog());
                    pwdBtnRow.add(changePasswordBtn);
                    pwdPanel.add(pwdBtnRow, BorderLayout.SOUTH);

                    pwdCard.add(pwdPanel, BorderLayout.CENTER);
                    pwdCard.revalidate(); pwdCard.repaint();
                }
        );

        scrollContent.add(personalCard);
        scrollContent.add(Box.createVerticalStrut(14));
        scrollContent.add(profCard);
        scrollContent.add(Box.createVerticalStrut(14));
        scrollContent.add(deptCard);
        scrollContent.add(Box.createVerticalStrut(14));
        scrollContent.add(contactCard);
        scrollContent.add(Box.createVerticalStrut(14));
        scrollContent.add(identityCard);
        scrollContent.add(Box.createVerticalStrut(14));
        scrollContent.add(securityCard);
        scrollContent.add(Box.createVerticalStrut(14));
        scrollContent.add(pwdCard);
        scrollContent.add(Box.createVerticalStrut(20));

        JScrollPane scrollPane = new JScrollPane(scrollContent);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void showSelfServicePasswordDialog() {
        Window parent = SwingUtilities.getWindowAncestor(this);
        JDialog dlg = new JDialog(parent, "Change Account Password", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setSize(440, 340);
        dlg.setLocationRelativeTo(parent);
        dlg.setResizable(false);

        JPanel panel = new JPanel(new BorderLayout(14, 14));
        panel.setBackground(CampusTheme.BG_CARD);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JLabel title = new JLabel("Update Account Password");
        title.setFont(CampusTheme.FONT_TITLE);
        title.setForeground(CampusTheme.PRIMARY);
        panel.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
        form.setOpaque(false);

        JPasswordField curPass = new JPasswordField();
        JPasswordField newPass = new JPasswordField();
        JPasswordField cnfPass = new JPasswordField();

        form.add(new JLabel("Current Password:"));
        form.add(curPass);
        form.add(new JLabel("New Password:"));
        form.add(newPass);
        form.add(new JLabel("Confirm Password:"));
        form.add(cnfPass);
        panel.add(form, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.setOpaque(false);

        JLabel errLbl = new JLabel(" ");
        errLbl.setFont(CampusTheme.FONT_MICRO);
        errLbl.setForeground(CampusTheme.ERROR);
        bottom.add(errLbl, BorderLayout.NORTH);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setOpaque(false);
        JButton cancelBtn = SwingUtils.createSecondaryButton("Cancel");
        cancelBtn.addActionListener(e -> dlg.dispose());
        JButton saveBtn = SwingUtils.createPrimaryButton("Update Password");
        saveBtn.addActionListener(e -> {
            String c = new String(curPass.getPassword());
            String n = new String(newPass.getPassword());
            String cf = new String(cnfPass.getPassword());

            if (c.isEmpty() || n.isEmpty() || cf.isEmpty()) {
                errLbl.setText("All fields are required.");
                return;
            }
            if (!n.equals(cf)) {
                errLbl.setText("New passwords do not match.");
                return;
            }
            try {
                com.college.util.ValidationUtil.validateStrongPassword(n);
            } catch (Exception ex) {
                errLbl.setText("<html>" + ex.getMessage() + "</html>");
                return;
            }

            saveBtn.setEnabled(false);
            SwingUtils.executeAsync(
                    dlg,
                    () -> {
                        authService.changePassword(currentUser, c, n);
                        return null;
                    },
                    unused -> {
                        saveBtn.setEnabled(true);
                        SwingUtils.showInfo(dlg, "Password updated successfully!");
                        dlg.dispose();
                    },
                    err -> {
                        saveBtn.setEnabled(true);
                        errLbl.setText("<html>" + err.getMessage() + "</html>");
                    }
            );
        });

        btnRow.add(cancelBtn);
        btnRow.add(saveBtn);
        bottom.add(btnRow, BorderLayout.SOUTH);
        panel.add(bottom, BorderLayout.SOUTH);

        dlg.setContentPane(panel);
        dlg.setVisible(true);
    }

    private void addProfileRow(JPanel panel, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        JLabel lbl = new JLabel(label);
        lbl.setFont(SwingUtils.BOLD_FONT);
        panel.add(lbl, gbc);

        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 0.7;
        JLabel val = new JLabel(value);
        val.setFont(SwingUtils.REGULAR_FONT);
        panel.add(val, gbc);
    }

    private void initAdminTableView() {
        // Unified Top Toolbar
        JPanel topBar = new JPanel(new GridBagLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.NONE;

        // Left Section: Title + Count Badge
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftPanel.setOpaque(false);
        JLabel titleLabel = new JLabel("Faculty Directory");
        titleLabel.setFont(CampusTheme.FONT_TITLE);
        titleLabel.setForeground(CampusTheme.PRIMARY);
        leftPanel.add(titleLabel);

        countBadge = SwingUtils.createCountBadge("0 records");
        leftPanel.add(countBadge);

        gbc.gridx = 0;
        gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.WEST;
        topBar.add(leftPanel, gbc);

        // Center Section: Instant Search Box
        JPanel searchBox = SwingUtils.createSearchBox("Search ID, name, email, specialization...", this::applySearchFilter);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(0, 16, 0, 16);
        topBar.add(searchBox, gbc);

        // Right Section: Department filter + Refresh + View Details
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightPanel.setOpaque(false);

        JLabel deptLbl = new JLabel("Dept:");
        deptLbl.setFont(CampusTheme.FONT_BOLD);
        deptLbl.setForeground(CampusTheme.TEXT_SECONDARY);
        rightPanel.add(deptLbl);

        deptFilterCombo = new JComboBox<>();
        deptFilterCombo.setFont(CampusTheme.FONT_BODY);
        deptFilterCombo.setPreferredSize(new Dimension(170, 32));
        deptFilterCombo.addItem(new DepartmentItem(0, "All Departments"));
        deptFilterCombo.addActionListener(e -> loadData());
        rightPanel.add(deptFilterCombo);

        refreshButton = SwingUtils.createSecondaryButton("Refresh");
        refreshButton.setIcon(CampusIcons.get(CampusIcons.IconType.REPORTS, 14, CampusTheme.TEXT_SECONDARY));
        refreshButton.addActionListener(e -> {
            loadDepartments();
            loadData();
        });
        rightPanel.add(refreshButton);

        viewDetailsButton = SwingUtils.createSecondaryButton("View Details");
        viewDetailsButton.setIcon(CampusIcons.get(CampusIcons.IconType.PROFILE, 14, CampusTheme.TEXT_SECONDARY));
        viewDetailsButton.addActionListener(e -> openDetailsDialog());
        rightPanel.add(viewDetailsButton);

        helpButton = SwingUtils.createSecondaryButton("Credentials Guide");
        helpButton.setIcon(CampusIcons.get(CampusIcons.IconType.PASSWORD, 14, CampusTheme.TEXT_SECONDARY));
        helpButton.addActionListener(e -> {
            AdminCredentialHelpDialog dlg = new AdminCredentialHelpDialog(SwingUtilities.getWindowAncestor(this));
            dlg.setVisible(true);
        });
        rightPanel.add(helpButton);

        gbc.gridx = 2;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(0, 0, 0, 0);
        topBar.add(rightPanel, gbc);

        add(topBar, BorderLayout.NORTH);

        // Center Table
        String[] columns = {"ID", "Faculty ID", "Name", "Specialization", "Department", "Email", "Phone", "Join Date", "Status"};
        tableModel = new DefaultTableModel(columns, 0);

        int[] minWidths = {45, 100, 120, 160, 150, 160, 100, 85, 75};
        int[] prefWidths = {50, 110, 150, 220, 200, 220, 120, 100, 85};
        int[] maxWidths = {70, 140, 0, 0, 0, 0, 150, 120, 100};
        int[] alignments = {
                SwingConstants.CENTER,
                SwingConstants.CENTER,
                SwingConstants.LEFT,
                SwingConstants.LEFT,
                SwingConstants.LEFT,
                SwingConstants.LEFT,
                SwingConstants.LEFT,
                SwingConstants.CENTER,
                SwingConstants.CENTER
        };

        facultyTable = SwingUtils.createDirectoryTable(tableModel, minWidths, prefWidths, maxWidths, alignments);
        rowSorter = (TableRowSorter<TableModel>) facultyTable.getRowSorter();

        // Double-click row inspection
        facultyTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    openDetailsDialog();
                }
            }
        });

        facultyTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && facultyTable.getSelectedRow() != -1) {
                populateFormFromSelection();
            }
        });

        JScrollPane scrollPane = new JScrollPane(facultyTable);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(scrollPane, BorderLayout.CENTER);

        // South Form
        JPanel formCard = SwingUtils.createCardPanel("Faculty Registration & Details");
        JPanel formGrid = new JPanel(new GridLayout(2, 4, 8, 8));
        formGrid.setOpaque(false);

        employeeIdField = new JTextField();
        firstNameField = new JTextField();
        lastNameField = new JTextField();
        emailField = new JTextField();
        phoneField = new JTextField();
        hireDateField = new JTextField("2026-08-01");
        designationField = new JTextField("Assistant Professor");
        deptFormCombo = new JComboBox<>();
        autoGeneratePasswordCheck = new JCheckBox("Auto-Generate Temporary Password", true);
        autoGeneratePasswordCheck.setFont(CampusTheme.FONT_BODY);
        initialPasswordField = new JPasswordField();
        initialPasswordField.setEnabled(false);
        autoGeneratePasswordCheck.addActionListener(e -> {
            boolean auto = autoGeneratePasswordCheck.isSelected();
            initialPasswordField.setEnabled(!auto);
            if (auto) {
                initialPasswordField.setText("");
            }
        });

        formGrid.add(createFieldBox("Faculty ID (e.g. FAC2026010):", employeeIdField));
        formGrid.add(createFieldBox("First Name:", firstNameField));
        formGrid.add(createFieldBox("Last Name:", lastNameField));
        formGrid.add(createFieldBox("Email Address:", emailField));

        formGrid.add(createFieldBox("Phone Number:", phoneField));
        formGrid.add(createFieldBox("Designation:", designationField));
        formGrid.add(createFieldBox("Hire Date (YYYY-MM-DD):", hireDateField));
        formGrid.add(createFieldBox("Department:", deptFormCombo));

        JPanel extraGrid = new JPanel(new GridLayout(1, 2, 12, 8));
        extraGrid.setOpaque(false);
        JPanel passPanel = new JPanel(new BorderLayout(8, 4));
        passPanel.setOpaque(false);
        passPanel.add(autoGeneratePasswordCheck, BorderLayout.WEST);
        passPanel.add(createFieldBox("Or Enter Custom Password:", initialPasswordField), BorderLayout.CENTER);
        extraGrid.add(passPanel);
        extraGrid.add(new JLabel(""));

        JPanel centerWrapper = new JPanel(new BorderLayout(4, 4));
        centerWrapper.setOpaque(false);
        centerWrapper.add(formGrid, BorderLayout.NORTH);
        centerWrapper.add(extraGrid, BorderLayout.SOUTH);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        buttonRow.setOpaque(false);

        createButton = SwingUtils.createPrimaryButton("Create Faculty");
        createButton.addActionListener(e -> createFaculty());

        updateButton = SwingUtils.createSecondaryButton("Update Profile");
        updateButton.setEnabled(false);
        updateButton.addActionListener(e -> updateFaculty());

        resetPasswordButton = SwingUtils.createSecondaryButton("Reset Password");
        resetPasswordButton.setEnabled(false);
        resetPasswordButton.addActionListener(e -> resetSelectedFacultyPassword());

        deleteButton = SwingUtils.createSecondaryButton("Deactivate");
        deleteButton.setEnabled(false);
        deleteButton.addActionListener(e -> deleteFaculty());

        reactivateButton = SwingUtils.createSecondaryButton("Reactivate");
        reactivateButton.setEnabled(false);
        reactivateButton.addActionListener(e -> reactivateFaculty());

        JButton clearButton = SwingUtils.createSecondaryButton("Clear Form");
        clearButton.addActionListener(e -> clearForm());

        buttonRow.add(clearButton);
        buttonRow.add(resetPasswordButton);
        buttonRow.add(reactivateButton);
        buttonRow.add(deleteButton);
        buttonRow.add(updateButton);
        buttonRow.add(createButton);

        formCard.add(centerWrapper, BorderLayout.CENTER);
        formCard.add(buttonRow, BorderLayout.SOUTH);
        add(formCard, BorderLayout.SOUTH);

        loadDepartments();
        loadData();
    }

    public void openDetailsDialog() {
        int viewRow = facultyTable.getSelectedRow();
        if (viewRow == -1) {
            SwingUtils.showWarning(this, "Please select a faculty member from the directory to view complete details.");
            return;
        }
        int modelRow = facultyTable.convertRowIndexToModel(viewRow);
        int pk = (Integer) tableModel.getValueAt(modelRow, 0);

        SwingUtils.executeAsync(
                this,
                () -> {
                    Faculty f = facultyDAO.findById(pk);
                    Department d = (f != null && f.getDepartmentId() > 0) ? departmentService.getDepartmentById(currentUser, f.getDepartmentId()) : null;
                    return new Object[]{f, d};
                },
                result -> {
                    Faculty f = (Faculty) result[0];
                    Department d = (Department) result[1];
                    if (f != null) {
                        Window parentWindow = SwingUtilities.getWindowAncestor(this);
                        FacultyDetailsDialog dlg = new FacultyDetailsDialog(parentWindow, f, d);
                        dlg.setVisible(true);
                    } else {
                        SwingUtils.showError(this, "Faculty record could not be loaded.");
                    }
                }
        );
    }

    private void applySearchFilter(String query) {
        currentSearchQuery = query;
        if (rowSorter == null) return;
        if (query == null || query.trim().isEmpty()) {
            rowSorter.setRowFilter(null);
        } else {
            String q = query.trim().toLowerCase();
            rowSorter.setRowFilter(new RowFilter<TableModel, Integer>() {
                @Override
                public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
                    for (int col : new int[]{1, 2, 3, 4, 5, 6}) {
                        Object val = entry.getValue(col);
                        if (val != null && val.toString().toLowerCase().contains(q)) {
                            return true;
                        }
                    }
                    return false;
                }
            });
        }
        updateRecordCount();
    }

    private void updateRecordCount() {
        if (countBadge == null || tableModel == null) return;
        int total = tableModel.getRowCount();
        int visible = facultyTable.getRowCount();
        if (visible == total) {
            countBadge.setText(total + (total == 1 ? " record" : " records"));
        } else {
            countBadge.setText(visible + " of " + total + " records");
        }
    }

    private JPanel createFieldBox(String labelText, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(2, 2));
        p.setOpaque(false);
        JLabel l = new JLabel(labelText);
        l.setFont(SwingUtils.SMALL_FONT);
        p.add(l, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    private void loadDepartments() {
        SwingUtils.executeAsync(
                this,
                () -> departmentService.getAllActiveDepartments(currentUser),
                departments -> {
                    departmentMap.clear();
                    if (deptFormCombo != null) deptFormCombo.removeAllItems();

                    for (Department d : departments) {
                        departmentMap.put(d.getId(), d.getCode() + " - " + d.getName());
                        if (deptFormCombo != null) {
                            deptFormCombo.addItem(new DepartmentItem(d.getId(), d.getCode() + " - " + d.getName()));
                        }
                    }

                    if (deptFilterCombo != null) {
                        DepartmentItem selected = (DepartmentItem) deptFilterCombo.getSelectedItem();
                        int selId = selected != null ? selected.id : 0;
                        deptFilterCombo.removeAllItems();
                        deptFilterCombo.addItem(new DepartmentItem(0, "All Departments"));
                        for (Department d : departments) {
                            deptFilterCombo.addItem(new DepartmentItem(d.getId(), d.getCode() + " - " + d.getName()));
                        }
                        for (int i = 0; i < deptFilterCombo.getItemCount(); i++) {
                            if (deptFilterCombo.getItemAt(i).id == selId) {
                                deptFilterCombo.setSelectedIndex(i);
                                break;
                            }
                        }
                    }
                }
        );
    }

    public void loadData() {
        if (currentUser.getRole() == Role.FACULTY) return;
        DepartmentItem filter = (deptFilterCombo != null) ? (DepartmentItem) deptFilterCombo.getSelectedItem() : null;
        int filterDeptId = (filter != null) ? filter.id : 0;

        refreshButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> {
                    if (filterDeptId > 0) {
                        return facultyService.getFacultyByDepartment(currentUser, filterDeptId);
                    } else {
                        return facultyService.getAllActiveFaculty(currentUser);
                    }
                },
                facultyList -> {
                    tableModel.setRowCount(0);
                    facultyCache.clear();
                    for (Faculty f : facultyList) {
                        facultyCache.put(f.getId(), f);
                        String deptName = departmentMap.getOrDefault(f.getDepartmentId(), "Dept #" + f.getDepartmentId());
                        tableModel.addRow(new Object[]{
                                f.getId(),
                                f.getFacultyId(),
                                f.getFirstName() + " " + f.getLastName(),
                                f.getSpecialization(),
                                deptName,
                                f.getEmail(),
                                f.getPhone(),
                                f.getJoinDate(),
                                f.isActive() ? "Active" : "Inactive"
                        });
                    }
                    clearForm();
                    applySearchFilter(currentSearchQuery);
                    updateRecordCount();
                    refreshButton.setEnabled(true);
                },
                error -> refreshButton.setEnabled(true)
        );
    }

    private void populateFormFromSelection() {
        int viewRow = facultyTable.getSelectedRow();
        if (viewRow == -1) return;

        int modelRow = facultyTable.convertRowIndexToModel(viewRow);
        selectedFacultyPk = (Integer) tableModel.getValueAt(modelRow, 0);
        Faculty cached = facultyCache.get(selectedFacultyPk);
        if (cached != null) {
            populateFormFromFaculty(cached);
        } else {
            SwingUtils.executeAsync(
                    this,
                    () -> facultyService.getFacultyById(currentUser, selectedFacultyPk),
                    f -> {
                        if (f != null) {
                            facultyCache.put(f.getId(), f);
                            populateFormFromFaculty(f);
                        }
                    }
            );
        }
    }

    public void populateFormFromFaculty(Faculty f) {
        if (f == null) return;
        selectedFacultyPk = f.getId();
        if (employeeIdField != null) employeeIdField.setText(f.getFacultyId());
        if (firstNameField != null) firstNameField.setText(f.getFirstName());
        if (lastNameField != null) lastNameField.setText(f.getLastName());
        if (designationField != null) designationField.setText(f.getSpecialization() != null ? f.getSpecialization() : "");
        if (emailField != null) emailField.setText(f.getEmail());
        if (phoneField != null) phoneField.setText(f.getPhone() != null ? f.getPhone() : "");
        if (hireDateField != null) hireDateField.setText(f.getJoinDate() != null ? f.getJoinDate() : "");

        if (deptFormCombo != null) {
            for (int i = 0; i < deptFormCombo.getItemCount(); i++) {
                if (deptFormCombo.getItemAt(i).id == f.getDepartmentId()) {
                    deptFormCombo.setSelectedIndex(i);
                    break;
                }
            }
        }

        if (updateButton != null) updateButton.setEnabled(true);
        if (deleteButton != null) deleteButton.setEnabled(f.isActive());
        if (reactivateButton != null) reactivateButton.setEnabled(!f.isActive());
        if (resetPasswordButton != null) resetPasswordButton.setEnabled(f.isActive() && f.getUserId() > 0);
    }

    private void createFaculty() {
        Faculty f = buildFacultyFromForm();
        if (f == null) return;

        String initialPassword;
        if (autoGeneratePasswordCheck != null && autoGeneratePasswordCheck.isSelected()) {
            initialPassword = PasswordUtil.generateTemporaryPassword();
        } else {
            initialPassword = new String(initialPasswordField.getPassword()).trim();
            if (initialPassword.length() < 8) {
                SwingUtils.showWarning(this, "Initial password must be at least 8 characters long.");
                return;
            }
        }

        final String passwordUsed = initialPassword;
        createButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> facultyService.createFacultyWithCredentials(currentUser, f, passwordUsed, null),
                result -> {
                    createButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Faculty profile created: " + result.getFaculty().getFacultyId());
                    clearForm();
                    loadData();
                    TemporaryPasswordDialog dlg = new TemporaryPasswordDialog(
                            SwingUtilities.getWindowAncestor(this),
                            result.getFaculty().getFirstName() + " " + result.getFaculty().getLastName(),
                            result.getFaculty().getFacultyId(),
                            result.getUsername(),
                            result.getTemporaryPassword(),
                            "Faculty Account & Credentials Created Successfully"
                    );
                    dlg.setVisible(true);
                },
                error -> createButton.setEnabled(true)
        );
    }

    private void resetSelectedFacultyPassword() {
        if (selectedFacultyPk == null) return;
        Faculty f = facultyCache.get(selectedFacultyPk);
        if (f == null || f.getUserId() <= 0) {
            SwingUtils.showWarning(this, "Selected faculty has no linked user account.");
            return;
        }

        boolean confirm = SwingUtils.showConfirm(
                this,
                "Reset temporary password for " + f.getFullName() + " (" + f.getFacultyId() + ")?\n" +
                "A new temporary password will be generated and mandatory change will be required on login.",
                "Confirm Password Reset"
        );
        if (!confirm) return;

        resetPasswordButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> authService.resetUserPassword(currentUser, f.getUserId(), null),
                newTempPass -> {
                    resetPasswordButton.setEnabled(true);
                    TemporaryPasswordDialog dlg = new TemporaryPasswordDialog(
                            SwingUtilities.getWindowAncestor(this),
                            f.getFacultyId(),
                            f.getFacultyId(),
                            newTempPass,
                            "Faculty Password Reset"
                    );
                    dlg.setVisible(true);
                },
                error -> {
                    resetPasswordButton.setEnabled(true);
                    SwingUtils.showError(this, "Failed to reset password: " + error.getMessage());
                }
        );
    }

    private void updateFaculty() {
        if (selectedFacultyPk == null) return;
        Faculty f = buildFacultyFromForm();
        if (f == null) return;
        f.setId(selectedFacultyPk);

        Faculty existing = facultyCache.get(selectedFacultyPk);
        if (existing != null) {
            f.setActive(existing.isActive());
            f.setUserId(existing.getUserId());
        } else {
            f.setActive(true);
        }

        updateButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> facultyService.updateFaculty(currentUser, f),
                updated -> {
                    updateButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Faculty profile updated successfully.");
                    loadData();
                },
                error -> updateButton.setEnabled(true)
        );
    }

    private void deleteFaculty() {
        if (selectedFacultyPk == null) return;
        boolean confirm = SwingUtils.showConfirm(this,
                "Are you sure you want to deactivate faculty member ID " + selectedFacultyPk + "?",
                "Confirm Deactivation");
        if (!confirm) return;

        deleteButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> {
                    facultyService.deleteFaculty(currentUser, selectedFacultyPk);
                    return null;
                },
                unused -> {
                    deleteButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Faculty member deactivated.");
                    loadData();
                },
                error -> deleteButton.setEnabled(true)
        );
    }

    private void reactivateFaculty() {
        if (selectedFacultyPk == null) return;
        reactivateButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> facultyService.reactivateFaculty(currentUser, selectedFacultyPk),
                reactivated -> {
                    reactivateButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Faculty member reactivated.");
                    loadData();
                },
                error -> reactivateButton.setEnabled(true)
        );
    }

    private Faculty buildFacultyFromForm() {
        String facId = employeeIdField.getText().trim();
        String first = firstNameField.getText().trim();
        String last = lastNameField.getText().trim();
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();
        String hire = hireDateField.getText().trim();
        String desig = designationField.getText().trim();

        DepartmentItem dept = (DepartmentItem) deptFormCombo.getSelectedItem();
        if (dept == null || dept.id <= 0) {
            SwingUtils.showWarning(this, "Please select a valid department.");
            return null;
        }

        Faculty f = new Faculty();
        f.setFacultyId(facId);
        f.setFirstName(first);
        f.setLastName(last);
        f.setEmail(email);
        f.setPhone(phone.isEmpty() ? null : phone);
        f.setJoinDate(hire);
        f.setSpecialization(desig);
        f.setDepartmentId(dept.id);
        return f;
    }

    private void clearForm() {
        selectedFacultyPk = null;
        if (employeeIdField != null) employeeIdField.setText("");
        if (firstNameField != null) firstNameField.setText("");
        if (lastNameField != null) lastNameField.setText("");
        if (emailField != null) emailField.setText("");
        if (phoneField != null) phoneField.setText("");
        if (autoGeneratePasswordCheck != null) autoGeneratePasswordCheck.setSelected(true);
        if (initialPasswordField != null) {
            initialPasswordField.setText("");
            initialPasswordField.setEnabled(false);
        }
        if (updateButton != null) updateButton.setEnabled(false);
        if (deleteButton != null) deleteButton.setEnabled(false);
        if (reactivateButton != null) reactivateButton.setEnabled(false);
        if (resetPasswordButton != null) resetPasswordButton.setEnabled(false);
        if (facultyTable != null) facultyTable.clearSelection();
    }

    // =========================================================================
    // Testing & Form Inspection Accessors
    // =========================================================================
    public Integer getSelectedFacultyPk() { return selectedFacultyPk; }
    public String getFacultyIdText() { return employeeIdField != null ? employeeIdField.getText() : ""; }
    public String getFirstNameText() { return firstNameField != null ? firstNameField.getText() : ""; }
    public String getLastNameText() { return lastNameField != null ? lastNameField.getText() : ""; }
    public String getDesignationText() { return designationField != null ? designationField.getText() : ""; }
    public String getEmailText() { return emailField != null ? emailField.getText() : ""; }
    public String getPhoneText() { return phoneField != null ? phoneField.getText() : ""; }
    public String getHireDateText() { return hireDateField != null ? hireDateField.getText() : ""; }
    public int getSelectedDepartmentId() {
        if (deptFormCombo == null) return 0;
        DepartmentItem item = (DepartmentItem) deptFormCombo.getSelectedItem();
        return item != null ? item.id : 0;
    }
    public void setDesignationText(String desig) { if (designationField != null) designationField.setText(desig); }
    public void setPhoneText(String phone) { if (phoneField != null) phoneField.setText(phone); }
    public Faculty getFacultyFromForm() { return buildFacultyFromForm(); }

    public static class DepartmentItem {
        public final int id;
        public final String name;

        public DepartmentItem(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
