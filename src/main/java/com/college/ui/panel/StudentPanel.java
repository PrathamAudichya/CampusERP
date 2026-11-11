package com.college.ui.panel;

import com.college.dao.AcademicProgramDAO;
import com.college.dao.StudentCohortDAO;
import com.college.dao.StudentDAO;
import com.college.model.AcademicProgram;
import com.college.model.Department;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.StudentCohort;
import com.college.model.User;
import com.college.service.AuthService;
import com.college.service.DepartmentService;
import com.college.service.StudentService;
import com.college.ui.components.CampusIcons;
import com.college.ui.components.PrimaryButton;
import com.college.ui.components.SecondaryButton;
import com.college.ui.components.StudentDetailsDialog;
import com.college.ui.dialog.AdminCredentialHelpDialog;
import com.college.ui.theme.CampusTheme;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Student management panel supporting Admin CRUD, Faculty read-only inspection,
 * and Student personal profile view.
 */
public class StudentPanel extends JPanel {

    private final User currentUser;
    private final StudentService studentService;
    private final DepartmentService departmentService;
    private final StudentDAO studentDAO;
    private final AcademicProgramDAO programDAO;
    private final StudentCohortDAO cohortDAO;
    private final AuthService authService;

    private JTable studentTable;
    private DefaultTableModel tableModel;
    private JComboBox<DepartmentItem> deptFilterCombo;
    private JComboBox<DepartmentItem> deptFormCombo;
    private JComboBox<ProgramItem> progFormCombo;
    private JComboBox<CohortItem> cohortFormCombo;
    private JTextField expectedGraduationField;
    private JLabel schoolDegreeLabel;

    private JTextField studentIdField;
    private JTextField firstNameField;
    private JTextField lastNameField;
    private JTextField emailField;
    private JTextField phoneField;
    private JTextField dobField;
    private JTextField admissionDateField;
    private JSpinner semesterSpinner;

    private JButton createButton;
    private JButton updateButton;
    private JButton deleteButton;
    private JButton reactivateButton;
    private JButton refreshButton;
    private JButton viewDetailsButton;
    private JLabel countBadge;
    private TableRowSorter<TableModel> rowSorter;
    private String currentSearchQuery = "";
    private JButton resetPasswordButton;
    private JButton changeUsernameButton;
    private JButton forcePasswordChangeButton;

    private Integer selectedStudentPk = null;
    private final Map<Integer, String> departmentMap = new HashMap<>();
    private final Map<Integer, Student> studentCache = new HashMap<>();
    private final Map<Integer, AcademicProgram> programMap = new HashMap<>();
    private final Map<Integer, StudentCohort> cohortMap = new HashMap<>();

    public StudentPanel(User currentUser) {
        this(currentUser, new StudentService(), new DepartmentService(), new StudentDAO(), new AcademicProgramDAO(), new StudentCohortDAO(), new AuthService());
    }

    public StudentPanel(User currentUser, StudentService studentService,
                        DepartmentService departmentService, StudentDAO studentDAO) {
        this(currentUser, studentService, departmentService, studentDAO, new AcademicProgramDAO(), new StudentCohortDAO(), new AuthService());
    }

    public StudentPanel(User currentUser, StudentService studentService,
                        DepartmentService departmentService, StudentDAO studentDAO,
                        AuthService authService) {
        this(currentUser, studentService, departmentService, studentDAO, new AcademicProgramDAO(), new StudentCohortDAO(), authService);
    }

    public StudentPanel(User currentUser, StudentService studentService,
                        DepartmentService departmentService, StudentDAO studentDAO,
                        AcademicProgramDAO programDAO, StudentCohortDAO cohortDAO,
                        AuthService authService) {
        this.currentUser = currentUser;
        this.studentService = studentService;
        this.departmentService = departmentService;
        this.studentDAO = studentDAO;
        this.programDAO = programDAO != null ? programDAO : new AcademicProgramDAO();
        this.cohortDAO = cohortDAO != null ? cohortDAO : new StudentCohortDAO();
        this.authService = authService;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(12, 12));
        setBackground(SwingUtils.BG_LIGHT);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        if (currentUser.getRole() == Role.STUDENT) {
            initStudentProfileView();
        } else {
            initAdminFacultyTableView();
        }
    }

    private void initStudentProfileView() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JLabel titleLabel = new JLabel("My Student Profile & Institutional Identity");
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

        // Card 2: Academic Program Information
        JPanel academicCard = SwingUtils.createCardPanel("Academic Information");
        JPanel academicGrid = new JPanel(new GridBagLayout());
        academicGrid.setOpaque(false);

        // Card 3: Institutional Identity
        JPanel identityCard = SwingUtils.createCardPanel("Institutional Identity");
        JPanel identityGrid = new JPanel(new GridBagLayout());
        identityGrid.setOpaque(false);

        // Card 4: Contact Information
        JPanel contactCard = SwingUtils.createCardPanel("Contact Information");
        JPanel contactGrid = new JPanel(new GridBagLayout());
        contactGrid.setOpaque(false);

        // Card 5: Account Security & Credentials
        JPanel securityCard = SwingUtils.createCardPanel("Account Security");
        JPanel secGrid = new JPanel(new GridBagLayout());
        secGrid.setOpaque(false);

        // Card 6: Password Management
        JPanel pwdCard = SwingUtils.createCardPanel("Password Management");
        JPanel pwdPanel = new JPanel(new BorderLayout(12, 12));
        pwdPanel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 12, 8, 12);
        gbc.anchor = GridBagConstraints.WEST;

        SwingUtils.executeAsync(
                this,
                () -> {
                    Student s = studentDAO.findByUserId(currentUser.getId());
                    Department d = (s != null && s.getDepartmentId() > 0) ? departmentService.getDepartmentById(currentUser, s.getDepartmentId()) : null;
                    return new Object[]{s, d};
                },
                result -> {
                    Student s = (Student) result[0];
                    Department d = (Department) result[1];
                    if (s == null) {
                        personalGrid.add(new JLabel("No student record linked to account."), gbc);
                        personalCard.add(personalGrid, BorderLayout.CENTER);
                        personalCard.revalidate();
                        return;
                    }

                    // Card 1: Personal Information
                    int r1 = 0;
                    addProfileRow(personalGrid, gbc, r1++, "Full Legal Name:", s.getFirstName() + " " + s.getLastName());
                    addProfileRow(personalGrid, gbc, r1++, "Date of Birth:", s.getDateOfBirth() != null ? s.getDateOfBirth() : "Not Recorded");
                    addProfileRow(personalGrid, gbc, r1++, "Admission Date:", s.getAdmissionDate() != null ? s.getAdmissionDate() : "Not Recorded");
                    addProfileRow(personalGrid, gbc, r1++, "Student Category:", "Regular Enrolled Student");
                    personalCard.add(personalGrid, BorderLayout.CENTER);
                    personalCard.revalidate(); personalCard.repaint();

                    // Card 2: Academic Information
                    int r2 = 0;
                    String school = s.getSchoolName() != null ? s.getSchoolName() : (d != null && d.getSchool() != null ? d.getSchool() : "General Studies");
                    addProfileRow(academicGrid, gbc, r2++, "Faculty / School:", school);
                    addProfileRow(academicGrid, gbc, r2++, "Academic Department:", (d != null ? d.getName() + " (" + d.getCode() + ")" : "General Academic Division"));
                    addProfileRow(academicGrid, gbc, r2++, "Degree Programme:", (s.getProgramName() != null ? s.getProgramName() + " (" + s.getProgramCode() + ")" : "General Academic Programme"));
                    addProfileRow(academicGrid, gbc, r2++, "Degree Level:", (s.getDegree() != null ? s.getDegree() : "Undergraduate"));
                    addProfileRow(academicGrid, gbc, r2++, "Student Cohort:", (s.getCohortCode() != null ? s.getCohortCode() : "Regular Admissions"));
                    addProfileRow(academicGrid, gbc, r2++, "Current Semester:", s.getSemester() != null ? "Semester " + s.getSemester() : "Semester 1");
                    addProfileRow(academicGrid, gbc, r2++, "Expected Graduation:", (s.getExpectedGraduationYear() != null ? s.getExpectedGraduationYear() : "Not Specified"));
                    addProfileRow(academicGrid, gbc, r2++, "Enrollment Status:", s.isActive() ? "Active / In Good Standing" : "Inactive / Withdrawn");
                    addProfileRow(academicGrid, gbc, r2++, "Academic Calendar:", "2026-2027 Academic Session");
                    academicCard.add(academicGrid, BorderLayout.CENTER);
                    academicCard.revalidate(); academicCard.repaint();

                    // Card 3: Institutional Identity
                    int r3 = 0;
                    addProfileRow(identityGrid, gbc, r3++, "Student Registration ID:", s.getStudentId());
                    addProfileRow(identityGrid, gbc, r3++, "Institutional Username:", currentUser.getUsername());
                    addProfileRow(identityGrid, gbc, r3++, "System Role:", currentUser.getRole().name());
                    addProfileRow(identityGrid, gbc, r3++, "Identity Verification:", "Verified Institutional Member");
                    identityCard.add(identityGrid, BorderLayout.CENTER);
                    identityCard.revalidate(); identityCard.repaint();

                    // Card 4: Contact Information
                    int r4c = 0;
                    addProfileRow(contactGrid, gbc, r4c++, "Institutional Email:", s.getEmail());
                    addProfileRow(contactGrid, gbc, r4c++, "Contact Phone:", s.getPhone() != null && !s.getPhone().isBlank() ? s.getPhone() : "Not Provided");
                    addProfileRow(contactGrid, gbc, r4c++, "Emergency Contact:", "Registered Campus Contact");
                    contactCard.add(contactGrid, BorderLayout.CENTER);
                    contactCard.revalidate(); contactCard.repaint();

                    // Card 5: Account Security
                    int r4 = 0;
                    addProfileRow(secGrid, gbc, r4++, "Account Status:", currentUser.isActive() ? "Active & Verified" : "Suspended");
                    addProfileRow(secGrid, gbc, r4++, "Encryption Standard:", "BCrypt Cryptographic Hash (Adaptive Work Factor 12)");
                    addProfileRow(secGrid, gbc, r4++, "Password Policy:", "Minimum 8 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special symbol");
                    addProfileRow(secGrid, gbc, r4++, "Mandatory Password Reset:", currentUser.isMustChangePassword() ? "YES (Required on next action)" : "NO (Compliant)");
                    securityCard.add(secGrid, BorderLayout.CENTER);
                    securityCard.revalidate(); securityCard.repaint();

                    // Card 6: Password Management
                    JLabel pwdDesc = new JLabel("<html>Maintain security of your student records, coursework enrollments, and fee ledgers by updating your password regularly.</html>");
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
        scrollContent.add(academicCard);
        scrollContent.add(Box.createVerticalStrut(14));
        scrollContent.add(identityCard);
        scrollContent.add(Box.createVerticalStrut(14));
        scrollContent.add(contactCard);
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
                        studentService.changeOwnPassword(currentUser, c, n);
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

    private void initAdminFacultyTableView() {
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
        JLabel titleLabel = new JLabel(currentUser.getRole() == Role.ADMIN ? "Student Directory" : "Active Students");
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
        JPanel searchBox = SwingUtils.createSearchBox("Search ID, name, email...", this::applySearchFilter);
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

        if (currentUser.getRole() == Role.ADMIN) {
            JButton helpBtn = SwingUtils.createSecondaryButton("Credentials Guide");
            helpBtn.setIcon(CampusIcons.get(CampusIcons.IconType.PASSWORD, 14, CampusTheme.TEXT_SECONDARY));
            helpBtn.addActionListener(e -> {
                AdminCredentialHelpDialog dlg = new AdminCredentialHelpDialog(SwingUtilities.getWindowAncestor(this));
                dlg.setVisible(true);
            });
            rightPanel.add(helpBtn);
        }

        gbc.gridx = 2;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(0, 0, 0, 0);
        topBar.add(rightPanel, gbc);

        add(topBar, BorderLayout.NORTH);

        // Center Table
        String[] columns = {"ID", "Student ID", "First Name", "Last Name", "Department", "Semester", "Email", "Phone", "Status"};
        tableModel = new DefaultTableModel(columns, 0);

        int[] minWidths = {45, 100, 100, 100, 150, 65, 160, 100, 75};
        int[] prefWidths = {50, 115, 130, 130, 200, 75, 220, 120, 85};
        int[] maxWidths = {70, 140, 0, 0, 0, 90, 0, 150, 100};
        int[] alignments = {
                SwingConstants.CENTER,
                SwingConstants.CENTER,
                SwingConstants.LEFT,
                SwingConstants.LEFT,
                SwingConstants.LEFT,
                SwingConstants.CENTER,
                SwingConstants.LEFT,
                SwingConstants.LEFT,
                SwingConstants.CENTER
        };

        studentTable = SwingUtils.createDirectoryTable(tableModel, minWidths, prefWidths, maxWidths, alignments);
        rowSorter = (TableRowSorter<TableModel>) studentTable.getRowSorter();

        // Double-click row inspection
        studentTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    openDetailsDialog();
                }
            }
        });

        if (currentUser.getRole() == Role.ADMIN) {
            studentTable.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting() && studentTable.getSelectedRow() != -1) {
                    populateFormFromSelection();
                }
            });
        }

        JScrollPane scrollPane = new JScrollPane(studentTable);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(scrollPane, BorderLayout.CENTER);

        // South Form (Admin only)
        if (currentUser.getRole() == Role.ADMIN) {
            JPanel formCard = SwingUtils.createCardPanel("Student Registration & Profile Management");
            JPanel formGrid = new JPanel(new GridLayout(4, 4, 8, 8));
            formGrid.setOpaque(false);

            studentIdField = new JTextField();
            firstNameField = new JTextField();
            lastNameField = new JTextField();
            emailField = new JTextField();
            phoneField = new JTextField();
            dobField = new JTextField("2005-01-15");
            admissionDateField = new JTextField("2026-08-01");
            deptFormCombo = new JComboBox<>();

            progFormCombo = new JComboBox<>();
            cohortFormCombo = new JComboBox<>();
            semesterSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 8, 1));
            expectedGraduationField = new JTextField("2030");
            schoolDegreeLabel = new JLabel("<html><small style='color:#68756D;'>Select programme to view faculty school</small></html>");

            // Program selection listener to auto-populate school, department, cohort, max semester & graduation year
            progFormCombo.addActionListener(e -> {
                ProgramItem item = (ProgramItem) progFormCombo.getSelectedItem();
                if (item != null && item.program != null) {
                    AcademicProgram prog = item.program;
                    if (deptFormCombo != null) {
                        for (int i = 0; i < deptFormCombo.getItemCount(); i++) {
                            if (deptFormCombo.getItemAt(i).id == prog.getDepartmentId()) {
                                deptFormCombo.setSelectedIndex(i);
                                break;
                            }
                        }
                    }
                    if (schoolDegreeLabel != null) {
                        String sch = prog.getDepartmentSchool() != null ? prog.getDepartmentSchool() : "Academic School";
                        schoolDegreeLabel.setText("<html><small style='color:#2F6B4F;'><b>" + sch + "</b><br/>" + prog.getDegree() + " (" + prog.getLevel() + ") • " + prog.getTotalSemesters() + " Sems</small></html>");
                    }
                    if (semesterSpinner != null) {
                        int maxSem = Math.max(1, prog.getTotalSemesters());
                        int curVal = (Integer) semesterSpinner.getValue();
                        semesterSpinner.setModel(new SpinnerNumberModel(Math.min(curVal, maxSem), 1, maxSem, 1));
                    }
                    if (cohortFormCombo != null) {
                        for (int i = 0; i < cohortFormCombo.getItemCount(); i++) {
                            CohortItem cItem = cohortFormCombo.getItemAt(i);
                            if (cItem.cohort != null && cItem.cohort.getProgramId() == prog.getId()) {
                                cohortFormCombo.setSelectedIndex(i);
                                break;
                            }
                        }
                    }
                    if (expectedGraduationField != null) {
                        try {
                            String adm = admissionDateField.getText().trim();
                            int admYear = java.time.LocalDate.parse(adm).getYear();
                            expectedGraduationField.setText(String.valueOf(admYear + prog.getDurationYears()));
                        } catch (Exception ex) {
                            int curYear = java.time.LocalDate.now().getYear();
                            expectedGraduationField.setText(String.valueOf(curYear + prog.getDurationYears()));
                        }
                    }
                }
            });

            // Row 1
            formGrid.add(createFieldBox("Student ID (e.g. STU2026010):", studentIdField));
            formGrid.add(createFieldBox("First Name:", firstNameField));
            formGrid.add(createFieldBox("Last Name:", lastNameField));
            formGrid.add(createFieldBox("Email Address:", emailField));

            // Row 2
            formGrid.add(createFieldBox("Phone Number:", phoneField));
            formGrid.add(createFieldBox("Date of Birth (YYYY-MM-DD):", dobField));
            formGrid.add(createFieldBox("Admission Date (YYYY-MM-DD):", admissionDateField));
            formGrid.add(createFieldBox("Department:", deptFormCombo));

            // Row 3
            formGrid.add(createFieldBox("Academic Programme:", progFormCombo));
            formGrid.add(createFieldBox("Student Cohort:", cohortFormCombo));
            formGrid.add(createFieldBox("Current Semester:", semesterSpinner));
            formGrid.add(createFieldBox("Expected Graduation Year:", expectedGraduationField));

            // Row 4
            formGrid.add(createFieldBox("School & Degree Level:", schoolDegreeLabel));
            JLabel credsNotice = new JLabel("<html><small style='color:#2F6B4F;'>✓ Auto-generates student account<br/>using Student ID as username</small></html>");
            formGrid.add(createFieldBox("Account Credentialing:", credsNotice));
            formGrid.add(new JLabel("")); // spacer
            formGrid.add(new JLabel("")); // spacer

            JPanel southBar = new JPanel(new GridLayout(2, 1, 0, 6));
            southBar.setOpaque(false);
            southBar.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

            JPanel accountButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            accountButtons.setOpaque(false);

            resetPasswordButton = SwingUtils.createSecondaryButton("Reset Password");
            resetPasswordButton.setEnabled(false);
            resetPasswordButton.addActionListener(e -> resetStudentPassword());

            changeUsernameButton = SwingUtils.createSecondaryButton("Change Username");
            changeUsernameButton.setEnabled(false);
            changeUsernameButton.addActionListener(e -> changeStudentUsername());

            forcePasswordChangeButton = SwingUtils.createSecondaryButton("Force Password Change");
            forcePasswordChangeButton.setEnabled(false);
            forcePasswordChangeButton.addActionListener(e -> forceStudentPasswordChange());

            accountButtons.add(new JLabel("Security Actions:"));
            accountButtons.add(resetPasswordButton);
            accountButtons.add(changeUsernameButton);
            accountButtons.add(forcePasswordChangeButton);

            JPanel formButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            formButtons.setOpaque(false);

            createButton = SwingUtils.createPrimaryButton("Create Student");
            createButton.addActionListener(e -> createStudent());

            updateButton = SwingUtils.createSecondaryButton("Update Profile");
            updateButton.setEnabled(false);
            updateButton.addActionListener(e -> updateStudent());

            deleteButton = SwingUtils.createSecondaryButton("Deactivate");
            deleteButton.setEnabled(false);
            deleteButton.addActionListener(e -> deleteStudent());

            reactivateButton = SwingUtils.createSecondaryButton("Reactivate");
            reactivateButton.setEnabled(false);
            reactivateButton.addActionListener(e -> reactivateStudent());

            JButton clearButton = SwingUtils.createSecondaryButton("Clear Form");
            clearButton.addActionListener(e -> clearForm());

            formButtons.add(clearButton);
            formButtons.add(reactivateButton);
            formButtons.add(deleteButton);
            formButtons.add(updateButton);
            formButtons.add(createButton);

            southBar.add(accountButtons);
            southBar.add(formButtons);

            formCard.add(formGrid, BorderLayout.CENTER);
            formCard.add(southBar, BorderLayout.SOUTH);
            add(formCard, BorderLayout.SOUTH);
        }

        loadDepartments();
        loadData();
    }

    public void openDetailsDialog() {
        int viewRow = studentTable.getSelectedRow();
        if (viewRow == -1) {
            SwingUtils.showWarning(this, "Please select a student from the directory to view complete details.");
            return;
        }
        int modelRow = studentTable.convertRowIndexToModel(viewRow);
        int pk = (Integer) tableModel.getValueAt(modelRow, 0);

        SwingUtils.executeAsync(
                this,
                () -> {
                    Student s = studentDAO.findById(pk);
                    Department d = (s != null && s.getDepartmentId() > 0) ? departmentService.getDepartmentById(currentUser, s.getDepartmentId()) : null;
                    User u = (s != null) ? studentService.getLinkedUserForStudent(currentUser, s.getId()) : null;
                    return new Object[]{s, d, u};
                },
                result -> {
                    Student s = (Student) result[0];
                    Department d = (Department) result[1];
                    User u = (User) result[2];
                    if (s != null) {
                        Window parentWindow = SwingUtilities.getWindowAncestor(this);
                        StudentDetailsDialog dlg = new StudentDetailsDialog(parentWindow, s, d, u);
                        dlg.setVisible(true);
                    } else {
                        SwingUtils.showError(this, "Student record could not be loaded.");
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
                    for (int col : new int[]{1, 2, 3, 4, 6, 7}) {
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
        int visible = studentTable.getRowCount();
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
                () -> {
                    List<Department> depts = departmentService.getAllActiveDepartments(currentUser);
                    List<AcademicProgram> progs = programDAO.findAllActive();
                    List<StudentCohort> cohorts = cohortDAO.findAllActive();
                    return new Object[]{depts, progs, cohorts};
                },
                results -> {
                    @SuppressWarnings("unchecked")
                    List<Department> departments = (List<Department>) results[0];
                    @SuppressWarnings("unchecked")
                    List<AcademicProgram> progs = (List<AcademicProgram>) results[1];
                    @SuppressWarnings("unchecked")
                    List<StudentCohort> cohorts = (List<StudentCohort>) results[2];

                    departmentMap.clear();
                    if (deptFormCombo != null) deptFormCombo.removeAllItems();

                    for (Department d : departments) {
                        departmentMap.put(d.getId(), d.getCode() + " - " + d.getName());
                        if (deptFormCombo != null) {
                            deptFormCombo.addItem(new DepartmentItem(d.getId(), d.getCode() + " - " + d.getName()));
                        }
                    }

                    if (progFormCombo != null) {
                        progFormCombo.removeAllItems();
                        progFormCombo.addItem(new ProgramItem(0, "-- Select Programme --", null));
                        programMap.clear();
                        for (AcademicProgram p : progs) {
                            programMap.put(p.getId(), p);
                            progFormCombo.addItem(new ProgramItem(p.getId(), p.getCode() + " - " + p.getName() + " (" + p.getDegree() + ")", p));
                        }
                    }

                    if (cohortFormCombo != null) {
                        cohortFormCombo.removeAllItems();
                        cohortFormCombo.addItem(new CohortItem(0, "-- Select Cohort --", null));
                        cohortMap.clear();
                        for (StudentCohort c : cohorts) {
                            cohortMap.put(c.getId(), c);
                            cohortFormCombo.addItem(new CohortItem(c.getId(), c.getCohortCode() + " (" + c.getAdmissionYear() + ")", c));
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
        if (currentUser.getRole() == Role.STUDENT) return;
        DepartmentItem filter = (deptFilterCombo != null) ? (DepartmentItem) deptFilterCombo.getSelectedItem() : null;
        int filterDeptId = (filter != null) ? filter.id : 0;

        refreshButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> {
                    if (filterDeptId > 0) {
                        return studentService.getStudentsByDepartment(currentUser, filterDeptId);
                    } else {
                        return studentService.getAllActiveStudents(currentUser);
                    }
                },
                students -> {
                    tableModel.setRowCount(0);
                    studentCache.clear();
                    for (Student s : students) {
                        studentCache.put(s.getId(), s);
                        String deptName = departmentMap.getOrDefault(s.getDepartmentId(), "Dept #" + s.getDepartmentId());
                        tableModel.addRow(new Object[]{
                                s.getId(),
                                s.getStudentId(),
                                s.getFirstName(),
                                s.getLastName(),
                                deptName,
                                s.getSemester() != null ? s.getSemester() : "1",
                                s.getEmail(),
                                s.getPhone(),
                                s.isActive() ? "Active" : "Inactive"
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
        int viewRow = studentTable.getSelectedRow();
        if (viewRow == -1) return;

        int modelRow = studentTable.convertRowIndexToModel(viewRow);
        selectedStudentPk = (Integer) tableModel.getValueAt(modelRow, 0);
        final Integer targetStudentPk = selectedStudentPk;
        Student cached = studentCache.get(targetStudentPk);
        if (cached != null) {
            populateFormFromStudent(cached);
        } else {
            SwingUtils.executeAsync(
                    this,
                    () -> studentService.getStudentById(currentUser, targetStudentPk),
                    s -> {
                        if (s != null && java.util.Objects.equals(selectedStudentPk, targetStudentPk)) {
                            studentCache.put(s.getId(), s);
                            populateFormFromStudent(s);
                        }
                    }
            );
        }
    }

    public void populateFormFromStudent(Student s) {
        if (s == null) return;
        selectedStudentPk = s.getId();
        if (studentIdField != null) studentIdField.setText(s.getStudentId());
        if (firstNameField != null) firstNameField.setText(s.getFirstName());
        if (lastNameField != null) lastNameField.setText(s.getLastName());
        if (dobField != null) dobField.setText(s.getDateOfBirth() != null ? s.getDateOfBirth() : "");
        if (admissionDateField != null) admissionDateField.setText(s.getAdmissionDate() != null ? s.getAdmissionDate() : "");
        if (emailField != null) emailField.setText(s.getEmail());
        if (phoneField != null) phoneField.setText(s.getPhone() != null ? s.getPhone() : "");

        if (semesterSpinner != null) {
            try {
                int sem = Integer.parseInt(s.getSemester() != null ? s.getSemester().trim() : "1");
                semesterSpinner.setValue(Math.max(1, Math.min(8, sem)));
            } catch (Exception ex) {
                semesterSpinner.setValue(1);
            }
        }

        if (deptFormCombo != null) {
            for (int i = 0; i < deptFormCombo.getItemCount(); i++) {
                if (deptFormCombo.getItemAt(i).id == s.getDepartmentId()) {
                    deptFormCombo.setSelectedIndex(i);
                    break;
                }
            }
        }

        if (progFormCombo != null && s.getProgramId() != null) {
            for (int i = 0; i < progFormCombo.getItemCount(); i++) {
                if (progFormCombo.getItemAt(i).id == s.getProgramId()) {
                    progFormCombo.setSelectedIndex(i);
                    break;
                }
            }
        }
        if (cohortFormCombo != null && s.getCohortId() != null) {
            for (int i = 0; i < cohortFormCombo.getItemCount(); i++) {
                if (cohortFormCombo.getItemAt(i).id == s.getCohortId()) {
                    cohortFormCombo.setSelectedIndex(i);
                    break;
                }
            }
        }
        if (expectedGraduationField != null) {
            expectedGraduationField.setText(s.getExpectedGraduationYear() != null ? s.getExpectedGraduationYear() : "");
        }

        if (updateButton != null) updateButton.setEnabled(true);
        if (deleteButton != null) deleteButton.setEnabled(s.isActive());
        if (reactivateButton != null) reactivateButton.setEnabled(!s.isActive());
        if (resetPasswordButton != null) resetPasswordButton.setEnabled(s.isActive());
        if (changeUsernameButton != null) changeUsernameButton.setEnabled(true);
        if (forcePasswordChangeButton != null) forcePasswordChangeButton.setEnabled(s.isActive());
    }

    private void createStudent() {
        Student s = buildStudentFromForm();
        if (s == null) return;

        createButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> studentService.createStudentWithInstitutionalAccount(currentUser, s),
                result -> {
                    createButton.setEnabled(true);
                    clearForm();
                    loadData();
                    Window parentWin = SwingUtilities.getWindowAncestor(this);
                    com.college.ui.dialog.TemporaryPasswordDialog dlg = new com.college.ui.dialog.TemporaryPasswordDialog(
                            parentWin,
                            result.getStudent().getFirstName() + " " + result.getStudent().getLastName(),
                            result.getStudent().getStudentId(),
                            result.getUsername(),
                            result.getTemporaryPassword(),
                            "Student Account & Credentials Created Successfully"
                    );
                    dlg.setVisible(true);
                },
                error -> {
                    createButton.setEnabled(true);
                    SwingUtils.showError(this, SwingUtils.getSafeUserErrorMessage(error, "Failed to create student"));
                }
        );
    }

    private void resetStudentPassword() {
        if (selectedStudentPk == null) return;
        final Integer targetStudentPk = selectedStudentPk;
        Student cached = studentCache.get(targetStudentPk);
        String stuCode = cached != null ? cached.getStudentId() : "ID " + targetStudentPk;

        boolean confirm = SwingUtils.showConfirm(this,
                "Reset credentials for student " + stuCode + "?\n\n"
                        + "A new secure temporary password will be generated, and the student\n"
                        + "will be required to set a new password upon their next login.",
                "Confirm Password Reset");
        if (!confirm) return;

        resetPasswordButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> {
                    String tempPass = studentService.resetStudentPassword(currentUser, targetStudentPk);
                    User linkedUser = studentService.getLinkedUserForStudent(currentUser, targetStudentPk);
                    String username = (linkedUser != null) ? linkedUser.getUsername() : stuCode;
                    return new String[]{stuCode, username, tempPass};
                },
                res -> {
                    resetPasswordButton.setEnabled(true);
                    Window parentWin = SwingUtilities.getWindowAncestor(this);
                    TemporaryPasswordDialog dlg = new TemporaryPasswordDialog(
                            parentWin,
                            res[0],
                            res[1],
                            res[2],
                            "Student Password Reset Successfully"
                    );
                    dlg.setVisible(true);
                },
                error -> {
                    resetPasswordButton.setEnabled(true);
                    SwingUtils.showError(this, SwingUtils.getSafeUserErrorMessage(error, "Failed to reset student password"));
                }
        );
    }

    private void changeStudentUsername() {
        if (selectedStudentPk == null) return;
        final Integer targetStudentPk = selectedStudentPk;
        Student cached = studentCache.get(targetStudentPk);
        String currentId = cached != null ? cached.getStudentId() : "";

        changeUsernameButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> studentService.getLinkedUserForStudent(currentUser, targetStudentPk),
                linkedUser -> {
                    changeUsernameButton.setEnabled(true);
                    String currentUsername = (linkedUser != null) ? linkedUser.getUsername() : currentId;
                    String newUsername = JOptionPane.showInputDialog(
                            this,
                            "Enter new login username for student " + currentId + ":",
                            currentUsername
                    );
                    if (newUsername == null || newUsername.trim().isEmpty() || newUsername.trim().equals(currentUsername)) {
                        return;
                    }
                    changeUsernameButton.setEnabled(false);
                    SwingUtils.executeAsync(
                            this,
                            () -> {
                                studentService.updateStudentUsername(currentUser, targetStudentPk, newUsername.trim());
                                return null;
                            },
                            unused -> {
                                changeUsernameButton.setEnabled(true);
                                SwingUtils.showInfo(this, "Username successfully changed to '" + newUsername.trim() + "'.");
                                loadData();
                            },
                            error -> {
                                changeUsernameButton.setEnabled(true);
                                SwingUtils.showError(this, SwingUtils.getSafeUserErrorMessage(error, "Failed to change student username"));
                            }
                    );
                },
                error -> {
                    changeUsernameButton.setEnabled(true);
                    SwingUtils.showError(this, SwingUtils.getSafeUserErrorMessage(error, "Failed to load linked student user account"));
                }
        );
    }

    private void forceStudentPasswordChange() {
        if (selectedStudentPk == null) return;
        final Integer targetStudentPk = selectedStudentPk;
        Student cached = studentCache.get(targetStudentPk);
        String stuCode = cached != null ? cached.getStudentId() : "ID " + targetStudentPk;

        boolean confirm = SwingUtils.showConfirm(this,
                "Require student " + stuCode + " to change their password upon their next login?",
                "Force Password Change");
        if (!confirm) return;

        forcePasswordChangeButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> {
                    studentService.forcePasswordChange(currentUser, targetStudentPk);
                    return null;
                },
                unused -> {
                    forcePasswordChangeButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Mandatory password change flag set for student " + stuCode + ".");
                },
                error -> {
                    forcePasswordChangeButton.setEnabled(true);
                    SwingUtils.showError(this, SwingUtils.getSafeUserErrorMessage(error, "Failed to enforce password change"));
                }
        );
    }

    private void updateStudent() {
        if (selectedStudentPk == null) return;
        final Integer targetStudentPk = selectedStudentPk;
        Student s = buildStudentFromForm();
        if (s == null) return;
        s.setId(targetStudentPk);

        Student existing = studentCache.get(targetStudentPk);
        if (existing != null) {
            s.setActive(existing.isActive());
            s.setUserId(existing.getUserId());
        } else {
            s.setActive(true);
        }

        updateButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> studentService.updateStudent(currentUser, s),
                updated -> {
                    updateButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Student profile updated successfully.");
                    loadData();
                },
                error -> {
                    updateButton.setEnabled(true);
                    SwingUtils.showError(this, SwingUtils.getSafeUserErrorMessage(error, "Failed to update student profile"));
                }
        );
    }

    private void deleteStudent() {
        if (selectedStudentPk == null) return;
        final Integer targetStudentPk = selectedStudentPk;
        boolean confirm = SwingUtils.showConfirm(this,
                "Are you sure you want to deactivate student ID " + targetStudentPk + "?",
                "Confirm Deactivation");
        if (!confirm) return;

        deleteButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> {
                    studentService.deleteStudent(currentUser, targetStudentPk);
                    return null;
                },
                unused -> {
                    deleteButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Student deactivated.");
                    loadData();
                },
                error -> {
                    deleteButton.setEnabled(true);
                    SwingUtils.showError(this, SwingUtils.getSafeUserErrorMessage(error, "Failed to deactivate student"));
                }
        );
    }

    private void reactivateStudent() {
        if (selectedStudentPk == null) return;
        final Integer targetStudentPk = selectedStudentPk;
        reactivateButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> studentService.reactivateStudent(currentUser, targetStudentPk),
                reactivated -> {
                    reactivateButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Student reactivated.");
                    loadData();
                },
                error -> {
                    reactivateButton.setEnabled(true);
                    SwingUtils.showError(this, SwingUtils.getSafeUserErrorMessage(error, "Failed to reactivate student"));
                }
        );
    }

    private Student buildStudentFromForm() {
        String stuId = studentIdField.getText().trim();
        String first = firstNameField.getText().trim();
        String last = lastNameField.getText().trim();
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();
        String dob = dobField.getText().trim();
        String admission = admissionDateField.getText().trim();
        int sem = (Integer) semesterSpinner.getValue();

        DepartmentItem dept = (DepartmentItem) deptFormCombo.getSelectedItem();
        if (dept == null || dept.id <= 0) {
            SwingUtils.showWarning(this, "Please select a valid department.");
            return null;
        }

        Student s = new Student();
        s.setStudentId(stuId);
        s.setFirstName(first);
        s.setLastName(last);
        s.setEmail(email);
        s.setPhone(phone.isEmpty() ? null : phone);
        s.setDateOfBirth(dob);
        s.setAdmissionDate(admission);
        s.setDepartmentId(dept.id);
        s.setSemester(String.valueOf(sem));

        ProgramItem progItem = (progFormCombo != null) ? (ProgramItem) progFormCombo.getSelectedItem() : null;
        if (progItem != null && progItem.id > 0) {
            s.setProgramId(progItem.id);
            if (progItem.program != null) {
                s.setProgramName(progItem.program.getName());
                s.setProgramCode(progItem.program.getCode());
                s.setDegree(progItem.program.getDegree());
                s.setSchoolName(progItem.program.getDepartmentSchool());
            }
        }

        CohortItem cohortItem = (cohortFormCombo != null) ? (CohortItem) cohortFormCombo.getSelectedItem() : null;
        if (cohortItem != null && cohortItem.id > 0) {
            s.setCohortId(cohortItem.id);
            if (cohortItem.cohort != null) {
                s.setCohortCode(cohortItem.cohort.getCohortCode());
            }
        }

        try {
            int admYear = java.time.LocalDate.parse(admission).getYear();
            s.setAdmissionYear(String.valueOf(admYear));
        } catch (Exception ex) {
            s.setAdmissionYear(String.valueOf(java.time.LocalDate.now().getYear()));
        }

        if (expectedGraduationField != null && !expectedGraduationField.getText().trim().isEmpty()) {
            s.setExpectedGraduationYear(expectedGraduationField.getText().trim());
        } else if (progItem != null && progItem.program != null) {
            try {
                int admY = Integer.parseInt(s.getAdmissionYear());
                s.setExpectedGraduationYear(String.valueOf(admY + progItem.program.getDurationYears()));
            } catch (Exception ignored) {}
        }
        return s;
    }

    private String suggestNextStudentId() {
        int currentYear = java.time.LocalDate.now().getYear();
        String prefix = "STU" + currentYear;
        int maxSeq = 0;
        for (Student s : studentCache.values()) {
            String sid = s.getStudentId();
            if (sid != null && sid.startsWith(prefix)) {
                try {
                    int seq = Integer.parseInt(sid.substring(prefix.length()));
                    if (seq > maxSeq) {
                        maxSeq = seq;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return String.format("%s%03d", prefix, maxSeq + 1);
    }

    private void clearForm() {
        selectedStudentPk = null;
        if (studentIdField != null) studentIdField.setText(suggestNextStudentId());
        if (firstNameField != null) firstNameField.setText("");
        if (lastNameField != null) lastNameField.setText("");
        if (emailField != null) emailField.setText("");
        if (phoneField != null) phoneField.setText("");
        if (semesterSpinner != null) semesterSpinner.setValue(1);
        if (progFormCombo != null && progFormCombo.getItemCount() > 0) progFormCombo.setSelectedIndex(0);
        if (cohortFormCombo != null && cohortFormCombo.getItemCount() > 0) cohortFormCombo.setSelectedIndex(0);
        if (expectedGraduationField != null) expectedGraduationField.setText("2030");
        if (schoolDegreeLabel != null) schoolDegreeLabel.setText("<html><small style='color:#68756D;'>Select programme to view faculty school</small></html>");
        if (updateButton != null) updateButton.setEnabled(false);
        if (deleteButton != null) deleteButton.setEnabled(false);
        if (reactivateButton != null) reactivateButton.setEnabled(false);
        if (resetPasswordButton != null) resetPasswordButton.setEnabled(false);
        if (changeUsernameButton != null) changeUsernameButton.setEnabled(false);
        if (forcePasswordChangeButton != null) forcePasswordChangeButton.setEnabled(false);
        if (studentTable != null) studentTable.clearSelection();
    }

    /**
     * Modal dialog providing secure, one-time display of auto-generated temporary student credentials.
     * Inherits from unified institutional TemporaryPasswordDialog.
     */
    public static class TemporaryPasswordDialog extends com.college.ui.dialog.TemporaryPasswordDialog {
        public TemporaryPasswordDialog(Window parent, String studentId, String username, String tempPassword, String titleText) {
            super(parent, null, studentId, username, tempPassword, titleText);
        }

        public TemporaryPasswordDialog(Window parent, String studentName, String studentId, String username, String tempPassword, String titleText) {
            super(parent, studentName, studentId, username, tempPassword, titleText);
        }
    }

    // =========================================================================
    // Testing & Form Inspection Accessors
    // =========================================================================
    public Integer getSelectedStudentPk() { return selectedStudentPk; }
    public String getStudentIdText() { return studentIdField != null ? studentIdField.getText() : ""; }
    public String getFirstNameText() { return firstNameField != null ? firstNameField.getText() : ""; }
    public String getLastNameText() { return lastNameField != null ? lastNameField.getText() : ""; }
    public String getEmailText() { return emailField != null ? emailField.getText() : ""; }
    public String getPhoneText() { return phoneField != null ? phoneField.getText() : ""; }
    public String getDobText() { return dobField != null ? dobField.getText() : ""; }
    public String getAdmissionDateText() { return admissionDateField != null ? admissionDateField.getText() : ""; }
    public int getSemesterValue() { return semesterSpinner != null ? (Integer) semesterSpinner.getValue() : 1; }
    public int getSelectedDepartmentId() {
        if (deptFormCombo == null) return 0;
        DepartmentItem item = (DepartmentItem) deptFormCombo.getSelectedItem();
        return item != null ? item.id : 0;
    }
    public int getSelectedProgramId() {
        if (progFormCombo == null) return 0;
        ProgramItem item = (ProgramItem) progFormCombo.getSelectedItem();
        return item != null ? item.id : 0;
    }
    public int getSelectedCohortId() {
        if (cohortFormCombo == null) return 0;
        CohortItem item = (CohortItem) cohortFormCombo.getSelectedItem();
        return item != null ? item.id : 0;
    }
    public void setSelectedProgramId(int progId) {
        if (progFormCombo == null) return;
        for (int i = 0; i < progFormCombo.getItemCount(); i++) {
            if (progFormCombo.getItemAt(i).id == progId) {
                progFormCombo.setSelectedIndex(i);
                break;
            }
        }
    }
    public void setSelectedCohortId(int cohortId) {
        if (cohortFormCombo == null) return;
        for (int i = 0; i < cohortFormCombo.getItemCount(); i++) {
            if (cohortFormCombo.getItemAt(i).id == cohortId) {
                cohortFormCombo.setSelectedIndex(i);
                break;
            }
        }
    }
    public void setPhoneText(String phone) { if (phoneField != null) phoneField.setText(phone); }
    public void setLastNameText(String last) { if (lastNameField != null) lastNameField.setText(last); }
    public Student getStudentFromForm() { return buildStudentFromForm(); }

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

    public static class ProgramItem {
        public final int id;
        public final String label;
        public final AcademicProgram program;

        public ProgramItem(int id, String label, AcademicProgram program) {
            this.id = id;
            this.label = label;
            this.program = program;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static class CohortItem {
        public final int id;
        public final String label;
        public final StudentCohort cohort;

        public CohortItem(int id, String label, StudentCohort cohort) {
            this.id = id;
            this.label = label;
            this.cohort = cohort;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
