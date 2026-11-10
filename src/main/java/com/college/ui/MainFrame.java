package com.college.ui;

import com.college.config.AppConfig;
import com.college.model.Role;
import com.college.model.User;
import com.college.service.AuthService;
import com.college.ui.components.CampusIcons;
import com.college.ui.components.CampusLogo;
import com.college.ui.components.DangerButton;
import com.college.ui.components.PrimaryButton;
import com.college.ui.components.SecondaryButton;
import com.college.ui.components.StatusBadge;
import com.college.ui.components.StyledButton;
import com.college.ui.components.StyledButton.ButtonStyle;
import com.college.ui.panel.AcademicOverviewPanel;
import com.college.ui.panel.AcademicProgramPanel;
import com.college.ui.panel.AdminDashboardPanel;
import com.college.ui.panel.AttendancePanel;
import com.college.ui.panel.AuditLogPanel;
import com.college.ui.panel.CohortProgressionPanel;
import com.college.ui.panel.CourseAssignmentPanel;
import com.college.ui.panel.CoursePanel;
import com.college.ui.panel.DepartmentPanel;
import com.college.ui.panel.EnrollmentPanel;
import com.college.ui.panel.ExaminationPanel;
import com.college.ui.panel.FacultyDashboardPanel;
import com.college.ui.panel.FacultyPanel;
import com.college.ui.panel.FeePaymentPanel;
import com.college.ui.panel.FeeStructurePanel;
import com.college.ui.panel.MarkPanel;
import com.college.ui.panel.ProgrammeCurriculumPanel;
import com.college.ui.panel.ReportPanel;
import com.college.ui.panel.StudentDashboardPanel;
import com.college.ui.panel.StudentPanel;
import com.college.ui.panel.TimetablePanel;
import com.college.ui.panel.UserManagementPanel;
import com.college.ui.theme.CampusTheme;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Professional institutional application shell for Campus ERP v2.0.5.
 *
 * <p>Features:
 * <ul>
 *   <li>Light, clean application shell using warm institutional palette (#F5F6F2 canvas, #ECEFE8 sidebar).</li>
 *   <li>Top header bar in clean white (#FFFFFF) with geometric monogram, breadcrumbs, and profile chip.</li>
 *   <li>Pure Java2D vector iconography (CampusIcons) eliminating missing-glyph unicode boxes.</li>
 *   <li>Single-parent CardLayout content hosting guaranteeing persistent panel rendering.</li>
 *   <li>Role-segregated presentation for Administrator, Faculty, and Student workflows.</li>
 *   <li>Instantaneous content switching without window spawning or visual flicker.</li>
 * </ul>
 * </p>
 */
public class MainFrame extends JFrame {

    private final User currentUser;
    private final AuthService authService;

    private CardLayout cardLayout;
    private JPanel contentArea;
    private JPanel sidebarPanel;
    private JLabel breadcrumbLabel;
    private final List<StyledButton> navButtons = new ArrayList<>();
    private final Map<String, JComponent> views = new LinkedHashMap<>();

    // Compatibility tabbedPane for backwards compatibility
    private JTabbedPane tabbedPane;

    public MainFrame(User currentUser) {
        this(currentUser, new AuthService());
    }

    public MainFrame(User currentUser, AuthService authService) {
        super(AppConfig.APP_TITLE + " — [" + currentUser.getRole() + "] " + currentUser.getUsername());
        this.currentUser = currentUser;
        this.authService = authService;
        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setMinimumSize(new Dimension(1000, 680));
        setLocationRelativeTo(null);
        setIconImage(CampusLogo.createLogoImage(64, 64));

        JPanel rootPane = new JPanel(new BorderLayout());
        rootPane.setBackground(CampusTheme.BG_MAIN);

        // =====================================================================
        // Top Application Header Bar (Clean White #FFFFFF with Soft Border)
        // =====================================================================
        JPanel headerBar = new JPanel(new BorderLayout(16, 0));
        headerBar.setBackground(CampusTheme.CARD);
        headerBar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, CampusTheme.BORDER),
                new EmptyBorder(10, 20, 10, 20)
        ));

        // Brand + Institutional Monogram
        JPanel brandPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        brandPanel.setOpaque(false);

        CampusLogo logo = new CampusLogo(32, CampusLogo.LogoVariant.ICON_ONLY);
        brandPanel.add(logo);

        JPanel brandText = new JPanel(new GridLayout(2, 1, 0, 1));
        brandText.setOpaque(false);
        JLabel titleLabel = new JLabel("CAMPUS ERP");
        titleLabel.setFont(CampusTheme.FONT_TITLE);
        titleLabel.setForeground(CampusTheme.TEXT_PRIMARY);

        JLabel subtitleLabel = new JLabel(AppConfig.APP_TAGLINE);
        subtitleLabel.setFont(CampusTheme.FONT_SMALL);
        subtitleLabel.setForeground(CampusTheme.TEXT_SECONDARY);

        brandText.add(titleLabel);
        brandText.add(subtitleLabel);
        brandPanel.add(brandText);

        headerBar.add(brandPanel, BorderLayout.WEST);

        // Breadcrumb in Center
        breadcrumbLabel = new JLabel("Overview  /  Dashboard");
        breadcrumbLabel.setFont(CampusTheme.FONT_SMALL_BOLD);
        breadcrumbLabel.setForeground(CampusTheme.TEXT_MUTED);
        breadcrumbLabel.setBorder(new EmptyBorder(0, 16, 0, 0));
        headerBar.add(breadcrumbLabel, BorderLayout.CENTER);

        // User Identity Profile and Session Actions (East)
        JPanel userPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        userPanel.setOpaque(false);

        JLabel userIcon = new JLabel(CampusIcons.get(CampusIcons.IconType.PROFILE, 18, CampusTheme.TEXT_SECONDARY));
        userPanel.add(userIcon);

        JLabel usernameLabel = new JLabel(currentUser.getUsername());
        usernameLabel.setFont(CampusTheme.FONT_BOLD);
        usernameLabel.setForeground(CampusTheme.TEXT_PRIMARY);
        userPanel.add(usernameLabel);

        // Role Badge Pill
        StatusBadge roleBadge = StatusBadge.of(currentUser.getRole().name());
        userPanel.add(roleBadge);

        // Change Password Button
        JButton changePassBtn = new SecondaryButton("Password");
        changePassBtn.setIcon(CampusIcons.get(CampusIcons.IconType.PASSWORD, 14, CampusTheme.TEXT_SECONDARY));
        changePassBtn.setIconTextGap(6);
        changePassBtn.setFont(CampusTheme.FONT_SMALL);
        changePassBtn.setPreferredSize(new Dimension(96, 30));
        changePassBtn.addActionListener(e -> promptChangePasswordDialog());
        userPanel.add(changePassBtn);

        // Sign Out Button
        JButton logoutBtn = new DangerButton("Sign Out");
        logoutBtn.setIcon(CampusIcons.get(CampusIcons.IconType.LOGOUT, 14, Color.WHITE));
        logoutBtn.setIconTextGap(6);
        logoutBtn.setFont(CampusTheme.FONT_SMALL);
        logoutBtn.setPreferredSize(new Dimension(96, 30));
        logoutBtn.addActionListener(e -> performLogout());
        userPanel.add(logoutBtn);

        headerBar.add(userPanel, BorderLayout.EAST);
        rootPane.add(headerBar, BorderLayout.NORTH);

        // =====================================================================
        // Left Fixed Navigation Sidebar (Light Sage #ECEFE8)
        // =====================================================================
        sidebarPanel = new JPanel();
        sidebarPanel.setLayout(new BoxLayout(sidebarPanel, BoxLayout.Y_AXIS));
        sidebarPanel.setBackground(CampusTheme.BG_SIDEBAR);
        sidebarPanel.setBorder(new EmptyBorder(16, 12, 16, 12));

        JScrollPane sidebarScroll = new JScrollPane(sidebarPanel);
        sidebarScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, CampusTheme.BORDER));
        sidebarScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sidebarScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        sidebarScroll.getVerticalScrollBar().setUnitIncrement(16);
        sidebarScroll.setPreferredSize(new Dimension(226, rootPane.getHeight()));

        rootPane.add(sidebarScroll, BorderLayout.WEST);

        // =====================================================================
        // Center Content Area (CardLayout — Exclusive Parent)
        // =====================================================================
        cardLayout = new CardLayout();
        contentArea = new JPanel(cardLayout);
        contentArea.setBackground(CampusTheme.BG_MAIN);

        // Build role-specific navigation and panels
        buildRoleViews();

        rootPane.add(contentArea, BorderLayout.CENTER);

        // =====================================================================
        // Status Footer Bar
        // =====================================================================
        JPanel footerBar = new JPanel(new BorderLayout());
        footerBar.setBackground(CampusTheme.BG_SIDEBAR);
        footerBar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, CampusTheme.BORDER),
                new EmptyBorder(6, 16, 6, 16)
        ));

        JLabel footerLeft = new JLabel("Campus ERP v" + AppConfig.APP_VERSION +
                " • Academic Session: 2026-2027 • Status: Operational");
        footerLeft.setFont(CampusTheme.FONT_SMALL);
        footerLeft.setForeground(CampusTheme.TEXT_SECONDARY);
        footerBar.add(footerLeft, BorderLayout.WEST);

        JLabel footerRight = new JLabel("SQLite WAL Mode • Role: " + currentUser.getRole() + " • Single Desktop Node");
        footerRight.setFont(CampusTheme.FONT_SMALL);
        footerRight.setForeground(CampusTheme.TEXT_MUTED);
        footerBar.add(footerRight, BorderLayout.EAST);

        rootPane.add(footerBar, BorderLayout.SOUTH);

        setContentPane(rootPane);

        // Activate the first view by default
        if (!views.isEmpty()) {
            String firstKey = views.keySet().iterator().next();
            selectView(firstKey);
        }
    }

    private void buildRoleViews() {
        Role role = currentUser.getRole();
        views.clear();
        navButtons.clear();
        sidebarPanel.removeAll();

        // Standalone tabbedPane instance maintained for compatibility
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(CampusTheme.FONT_BOLD);

        if (role == Role.ADMIN) {
            addSidebarCategory("OVERVIEW");
            registerView("Dashboard", new AdminDashboardPanel(currentUser));
            registerView("Academic Overview", new AcademicOverviewPanel(currentUser));

            addSidebarCategory("ACADEMICS");
            registerView("Students", new StudentPanel(currentUser));
            registerView("Cohorts & Progression", new CohortProgressionPanel(currentUser));
            registerView("Programmes", new AcademicProgramPanel(currentUser));
            registerView("Curriculum", new ProgrammeCurriculumPanel(currentUser));
            registerView("Faculty", new FacultyPanel(currentUser));
            registerView("Departments", new DepartmentPanel(currentUser));
            registerView("Courses", new CoursePanel(currentUser));
            registerView("Assignments", new CourseAssignmentPanel(currentUser));
            registerView("Enrollments", new EnrollmentPanel(currentUser));
            registerView("Timetables", new TimetablePanel(currentUser));

            addSidebarCategory("EXAMINATIONS & EVAL");
            registerView("Attendance", new AttendancePanel(currentUser));
            registerView("Examinations", new ExaminationPanel(currentUser));
            registerView("Marks", new MarkPanel(currentUser));

            addSidebarCategory("FINANCE & GOVERNANCE");
            registerView("Fee Structures", new FeeStructurePanel(currentUser));
            registerView("Fee Payments", new FeePaymentPanel(currentUser));
            registerView("Reports", new ReportPanel(currentUser));
            registerView("User Directory", new UserManagementPanel(currentUser));
            registerView("Audit Trail", new AuditLogPanel(currentUser));

        } else if (role == Role.FACULTY) {
            addSidebarCategory("MY PORTAL");
            registerView("Dashboard", new FacultyDashboardPanel(currentUser));
            registerView("Assigned Courses", new CoursePanel(currentUser));
            registerView("Course Rosters", new EnrollmentPanel(currentUser));

            addSidebarCategory("TEACHING WORKLOAD");
            registerView("Attendance", new AttendancePanel(currentUser));
            registerView("Examinations", new ExaminationPanel(currentUser));
            registerView("Marks Entry", new MarkPanel(currentUser));
            registerView("Timetable", new TimetablePanel(currentUser));
            registerView("Academic Reports", new ReportPanel(currentUser));
            registerView("My Profile", new FacultyPanel(currentUser));

        } else if (role == Role.STUDENT) {
            addSidebarCategory("MY STUDENT PORTAL");
            registerView("Dashboard", new StudentDashboardPanel(currentUser));
            registerView("My Courses", new EnrollmentPanel(currentUser));
            registerView("Attendance", new AttendancePanel(currentUser));
            registerView("Class Timetable", new TimetablePanel(currentUser));

            addSidebarCategory("ACADEMIC PROGRESS");
            registerView("Examinations", new ExaminationPanel(currentUser));
            registerView("Marks & Grades", new MarkPanel(currentUser));
            registerView("Fee Payments", new FeePaymentPanel(currentUser));
            registerView("Performance Report", new ReportPanel(currentUser));
            registerView("My Profile", new StudentPanel(currentUser));
        }

        sidebarPanel.add(Box.createVerticalGlue());
    }

    private void addSidebarCategory(String title) {
        JLabel categoryLabel = new JLabel(title);
        categoryLabel.setFont(CampusTheme.FONT_MICRO);
        categoryLabel.setForeground(CampusTheme.TEXT_MUTED);
        categoryLabel.setBorder(new EmptyBorder(12, 10, 6, 4));
        categoryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebarPanel.add(categoryLabel);
    }

    private CampusIcons.IconType resolveIconForTitle(String title) {
        switch (title) {
            case "Dashboard":
            case "Academic Overview":
                return CampusIcons.IconType.DASHBOARD;
            case "Students":
            case "Cohorts & Progression":
                return CampusIcons.IconType.STUDENTS;
            case "Faculty":
                return CampusIcons.IconType.FACULTY;
            case "Departments":
                return CampusIcons.IconType.DEPARTMENTS;
            case "Courses":
            case "Assigned Courses":
            case "My Courses":
            case "Assignments":
            case "Programmes":
            case "Curriculum":
                return CampusIcons.IconType.COURSES;
            case "Enrollments":
            case "Course Rosters":
                return CampusIcons.IconType.ENROLLMENTS;
            case "Attendance":
            case "Timetables":
            case "Timetable":
            case "Class Timetable":
                return CampusIcons.IconType.ATTENDANCE;
            case "Examinations":
                return CampusIcons.IconType.EXAMINATIONS;
            case "Marks":
            case "Marks Entry":
            case "Marks & Grades":
                return CampusIcons.IconType.MARKS;
            case "Fee Structures":
            case "Fee Payments":
                return CampusIcons.IconType.FEES;
            case "Reports":
            case "Academic Reports":
            case "Performance Report":
                return CampusIcons.IconType.REPORTS;
            case "User Directory":
                return CampusIcons.IconType.USER_DIRECTORY;
            case "Audit Trail":
                return CampusIcons.IconType.AUDIT_TRAIL;
            case "My Profile":
                return CampusIcons.IconType.PROFILE;
            default:
                return CampusIcons.IconType.DASHBOARD;
        }
    }

    private void registerView(String title, JComponent panel) {
        views.put(title, panel);
        contentArea.add(panel, title);

        CampusIcons.IconType iconType = resolveIconForTitle(title);
        StyledButton btn = new StyledButton(title, ButtonStyle.SIDEBAR);
        btn.setIcon(CampusIcons.get(iconType, 16));
        btn.setIconTextGap(10);
        btn.setMaximumSize(new Dimension(Short.MAX_VALUE, 36));
        btn.setPreferredSize(new Dimension(202, 36));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.addActionListener(e -> selectView(title));

        navButtons.add(btn);
        sidebarPanel.add(btn);
        sidebarPanel.add(Box.createVerticalStrut(2));
    }

    private void selectView(String title) {
        cardLayout.show(contentArea, title);
        for (StyledButton btn : navButtons) {
            btn.setSelected(title.equals(btn.getText()));
            btn.repaint();
        }
        if (breadcrumbLabel != null) {
            String category = getCategoryForTitle(title);
            breadcrumbLabel.setText(category + "  /  " + title);
        }
        setTitle(AppConfig.APP_TITLE + " — " + title + " [" + currentUser.getRole() + "]");
    }

    private String getCategoryForTitle(String title) {
        switch (title) {
            case "Dashboard":
                return "Overview";
            case "Students":
            case "Faculty":
            case "Departments":
            case "Courses":
            case "Assignments":
            case "Enrollments":
            case "Assigned Courses":
            case "Course Rosters":
            case "My Courses":
                return "Academics";
            case "Attendance":
            case "Examinations":
            case "Marks":
            case "Marks Entry":
            case "Marks & Grades":
                return "Examinations";
            case "Fee Structures":
            case "Fee Payments":
            case "Reports":
            case "Academic Reports":
            case "Performance Report":
            case "User Directory":
            case "Audit Trail":
                return "Administration";
            default:
                return "Portal";
        }
    }

    private void promptChangePasswordDialog() {
        JDialog dialog = new JDialog(this, "Change Account Password", true);
        dialog.setSize(400, 290);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);

        JPanel panel = new JPanel(new BorderLayout(14, 14));
        panel.setBackground(CampusTheme.BG_CARD);
        panel.setBorder(new EmptyBorder(20, 24, 20, 24));

        JLabel title = new JLabel("Change Account Password");
        title.setFont(CampusTheme.FONT_TITLE);
        title.setForeground(CampusTheme.TEXT_PRIMARY);
        panel.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(3, 2, 8, 10));
        form.setOpaque(false);

        JPasswordField oldField = new JPasswordField();
        JPasswordField newField = new JPasswordField();
        JPasswordField confirmField = new JPasswordField();

        form.add(new JLabel("Current Password:"));
        form.add(oldField);
        form.add(new JLabel("New Password:"));
        form.add(newField);
        form.add(new JLabel("Confirm Password:"));
        form.add(confirmField);

        panel.add(form, BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnRow.setOpaque(false);

        JButton saveBtn = new PrimaryButton("Update Password");
        saveBtn.addActionListener(e -> {
            String oldPass = new String(oldField.getPassword());
            String newPass = new String(newField.getPassword());
            String confirmPass = new String(confirmField.getPassword());

            if (newPass.length() < 6) {
                SwingUtils.showWarning(dialog, "New password must be at least 6 characters.");
                return;
            }
            if (!newPass.equals(confirmPass)) {
                SwingUtils.showWarning(dialog, "New passwords do not match.");
                return;
            }

            saveBtn.setEnabled(false);
            SwingUtils.executeAsync(
                    dialog,
                    () -> {
                        authService.changePassword(currentUser, oldPass, newPass);
                        return null;
                    },
                    unused -> {
                        saveBtn.setEnabled(true);
                        SwingUtils.showInfo(dialog, "Password changed successfully.");
                        dialog.dispose();
                    },
                    err -> saveBtn.setEnabled(true)
            );
        });

        JButton cancelBtn = new SecondaryButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());

        btnRow.add(cancelBtn);
        btnRow.add(saveBtn);
        panel.add(btnRow, BorderLayout.SOUTH);

        dialog.setContentPane(panel);
        dialog.setVisible(true);
    }

    private void performLogout() {
        boolean confirm = SwingUtils.showConfirm(this,
                "Are you sure you want to sign out from Campus ERP?",
                "Confirm Sign Out");
        if (!confirm) return;

        authService.logout();
        dispose();
        SwingUtilities.invokeLater(() -> {
            LoginFrame loginFrame = new LoginFrame(authService);
            loginFrame.setVisible(true);
        });
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public JTabbedPane getTabbedPane() {
        return tabbedPane;
    }
}
