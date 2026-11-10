package com.college.ui.panel;

import com.college.model.AuditLog;
import com.college.model.Enrollment;
import com.college.model.User;
import com.college.service.AdminDashboardService;
import com.college.service.AdminDashboardService.AdminDashboardData;
import com.college.ui.components.CampusIcons;
import com.college.ui.components.EmptyStatePanel;
import com.college.ui.components.SecondaryButton;
import com.college.ui.components.StatusBadge;
import com.college.ui.theme.CampusTheme;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Modern, institutional Admin Dashboard panel for Campus ERP v2.0.5.
 *
 * <p>Displays real database KPIs, system audit trails, and active enrollment activities
 * with warm institutional styling and informative empty states.</p>
 */
public class AdminDashboardPanel extends JPanel {

    private final User currentUser;
    private final AdminDashboardService dashboardService;

    private JLabel studentsStatLabel;
    private JLabel facultyStatLabel;
    private JLabel coursesStatLabel;
    private JLabel enrollmentsStatLabel;
    private JLabel feesStatLabel;

    private JTable auditTable;
    private DefaultTableModel auditModel;
    private JPanel auditBody;
    private CardLayout auditCardLayout;

    private JTable enrollmentsTable;
    private DefaultTableModel enrollmentsModel;
    private JPanel enrollBody;
    private CardLayout enrollCardLayout;

    private JButton refreshButton;

    public AdminDashboardPanel(User currentUser) {
        this(currentUser, new AdminDashboardService());
    }

    public AdminDashboardPanel(User currentUser, AdminDashboardService dashboardService) {
        this.currentUser = currentUser;
        this.dashboardService = dashboardService;
        initUI();
        loadDashboardData();
    }

    private void initUI() {
        setLayout(new BorderLayout(16, 16));
        setBackground(CampusTheme.BG_MAIN);
        setBorder(new EmptyBorder(20, 20, 20, 20));

        // Top Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JPanel titles = new JPanel(new GridLayout(2, 1, 2, 2));
        titles.setOpaque(false);
        JLabel titleLabel = new JLabel("Institutional Administration Dashboard");
        titleLabel.setFont(CampusTheme.FONT_TITLE);
        titleLabel.setForeground(CampusTheme.TEXT_PRIMARY);

        JLabel subtitleLabel = new JLabel("Real-time academic registration, staffing, and financial governance metrics.");
        subtitleLabel.setFont(CampusTheme.FONT_SMALL);
        subtitleLabel.setForeground(CampusTheme.TEXT_SECONDARY);

        titles.add(titleLabel);
        titles.add(subtitleLabel);
        headerPanel.add(titles, BorderLayout.WEST);

        refreshButton = new SecondaryButton("Refresh Metrics");
        refreshButton.setIcon(CampusIcons.get(CampusIcons.IconType.REFRESH, 14, CampusTheme.TEXT_SECONDARY));
        refreshButton.setIconTextGap(8);
        refreshButton.setPreferredSize(new Dimension(150, 36));
        refreshButton.addActionListener(e -> loadDashboardData());
        headerPanel.add(refreshButton, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // Center: KPI Grid + Tables
        JPanel centerPanel = new JPanel(new BorderLayout(16, 16));
        centerPanel.setOpaque(false);

        // 5 KPI Metric Cards
        JPanel kpiGrid = new JPanel(new GridLayout(1, 5, 12, 12));
        kpiGrid.setOpaque(false);

        studentsStatLabel = new JLabel("—", SwingConstants.CENTER);
        facultyStatLabel = new JLabel("—", SwingConstants.CENTER);
        coursesStatLabel = new JLabel("—", SwingConstants.CENTER);
        enrollmentsStatLabel = new JLabel("—", SwingConstants.CENTER);
        feesStatLabel = new JLabel("—", SwingConstants.CENTER);

        kpiGrid.add(createMetricCard("Total Students", studentsStatLabel, CampusTheme.PRIMARY, CampusIcons.IconType.STUDENTS));
        kpiGrid.add(createMetricCard("Faculty Members", facultyStatLabel, CampusTheme.TERRACOTTA, CampusIcons.IconType.FACULTY));
        kpiGrid.add(createMetricCard("Active Courses", coursesStatLabel, CampusTheme.INFO, CampusIcons.IconType.COURSES));
        kpiGrid.add(createMetricCard("Active Enrollments", enrollmentsStatLabel, CampusTheme.SUCCESS, CampusIcons.IconType.ENROLLMENTS));
        kpiGrid.add(createMetricCard("Fee Collections", feesStatLabel, CampusTheme.WARNING, CampusIcons.IconType.FEES));

        centerPanel.add(kpiGrid, BorderLayout.NORTH);

        // Lower Half: Split Card Tables
        JPanel tablesGrid = new JPanel(new GridLayout(1, 2, 16, 16));
        tablesGrid.setOpaque(false);

        // Card 1: Recent System Audit Activities
        JPanel auditCard = SwingUtils.createCardPanel("Recent Administrative & Audit Activities");
        String[] auditCols = {"ID", "Time", "Action", "Entity", "Details"};
        auditModel = new DefaultTableModel(auditCols, 0);
        auditTable = SwingUtils.createStandardTable(auditModel);
        auditTable.getColumnModel().getColumn(0).setPreferredWidth(35);
        auditTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        auditTable.getColumnModel().getColumn(2).setPreferredWidth(110);
        auditTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        auditTable.getColumnModel().getColumn(4).setPreferredWidth(160);

        auditCardLayout = new CardLayout();
        auditBody = new JPanel(auditCardLayout);
        auditBody.setOpaque(false);
        auditBody.add(new JScrollPane(auditTable), "TABLE");
        auditBody.add(new EmptyStatePanel(CampusIcons.IconType.AUDIT_TRAIL, "No Audit Events Recorded",
                "System activities, user logins, and administrative operations will appear here."), "EMPTY");
        auditCard.add(auditBody, BorderLayout.CENTER);
        tablesGrid.add(auditCard);

        // Card 2: Recent Student Enrollments
        JPanel enrollCard = SwingUtils.createCardPanel("Recent Academic Enrollments");
        String[] enrollCols = {"ID", "Student ID", "Course ID", "Academic Year", "Status"};
        enrollmentsModel = new DefaultTableModel(enrollCols, 0);
        enrollmentsTable = SwingUtils.createStandardTable(enrollmentsModel);
        enrollmentsTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        enrollmentsTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        enrollmentsTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        enrollmentsTable.getColumnModel().getColumn(3).setPreferredWidth(90);
        enrollmentsTable.getColumnModel().getColumn(4).setPreferredWidth(90);

        // Custom renderer for status column to render nice badge
        enrollmentsTable.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                if (value != null) {
                    return StatusBadge.of(value.toString());
                }
                return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            }
        });

        enrollCardLayout = new CardLayout();
        enrollBody = new JPanel(enrollCardLayout);
        enrollBody.setOpaque(false);
        enrollBody.add(new JScrollPane(enrollmentsTable), "TABLE");
        enrollBody.add(new EmptyStatePanel(CampusIcons.IconType.ENROLLMENTS, "No Recent Enrollments Found",
                "Student course registrations and enrollment history will be displayed here."), "EMPTY");
        enrollCard.add(enrollBody, BorderLayout.CENTER);
        tablesGrid.add(enrollCard);

        centerPanel.add(tablesGrid, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // Bottom Institutional System Health Banner
        JPanel alertBanner = new JPanel(new BorderLayout());
        alertBanner.setBackground(CampusTheme.BG_CARD);
        alertBanner.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(CampusTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)
        ));

        JLabel healthLabel = new JLabel("System Operational • SQLite WAL Mode Active • ACID Immediate Serialization • Session Audited");
        healthLabel.setIcon(CampusIcons.get(CampusIcons.IconType.CHECK, 14, CampusTheme.SUCCESS));
        healthLabel.setIconTextGap(8);
        healthLabel.setFont(CampusTheme.FONT_MICRO);
        healthLabel.setForeground(CampusTheme.TEXT_SECONDARY);
        alertBanner.add(healthLabel, BorderLayout.WEST);

        JLabel secureLabel = new JLabel("Role: INSTITUTIONAL ADMINISTRATOR");
        secureLabel.setFont(CampusTheme.FONT_MICRO);
        secureLabel.setForeground(CampusTheme.PRIMARY);
        alertBanner.add(secureLabel, BorderLayout.EAST);

        add(alertBanner, BorderLayout.SOUTH);
    }

    private JPanel createMetricCard(String title, JLabel valueLabel, Color accentColor, CampusIcons.IconType iconType) {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBackground(CampusTheme.BG_CARD);
        card.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(CampusTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));

        // Top Accent Stripe
        JPanel stripe = new JPanel();
        stripe.setBackground(accentColor);
        stripe.setPreferredSize(new Dimension(card.getWidth(), 3));
        card.add(stripe, BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(4, 4));
        content.setOpaque(false);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(CampusTheme.FONT_SMALL_BOLD);
        lblTitle.setForeground(CampusTheme.TEXT_SECONDARY);
        titleRow.add(lblTitle, BorderLayout.WEST);

        JLabel iconLabel = new JLabel(CampusIcons.get(iconType, 16, accentColor));
        titleRow.add(iconLabel, BorderLayout.EAST);
        content.add(titleRow, BorderLayout.NORTH);

        valueLabel.setFont(CampusTheme.FONT_TITLE);
        valueLabel.setForeground(CampusTheme.TEXT_PRIMARY);
        content.add(valueLabel, BorderLayout.CENTER);

        card.add(content, BorderLayout.CENTER);
        return card;
    }

    public void loadDashboardData() {
        if (refreshButton != null) refreshButton.setEnabled(false);

        SwingUtils.executeAsync(
                this,
                () -> dashboardService.getDashboardData(currentUser),
                data -> {
                    if (refreshButton != null) refreshButton.setEnabled(true);
                    studentsStatLabel.setText(String.valueOf(data.getTotalStudents()));
                    facultyStatLabel.setText(String.valueOf(data.getTotalFaculty()));
                    coursesStatLabel.setText(String.valueOf(data.getTotalCourses()));
                    enrollmentsStatLabel.setText(String.valueOf(data.getActiveEnrollments()));
                    feesStatLabel.setText(SwingUtils.formatPaise(data.getTotalFeeCollectionPaise()));

                    // Populate Audit Table
                    auditModel.setRowCount(0);
                    if (data.getRecentAuditLogs() != null && !data.getRecentAuditLogs().isEmpty()) {
                        for (AuditLog log : data.getRecentAuditLogs()) {
                            auditModel.addRow(new Object[]{
                                    log.getId(),
                                    log.getTimestamp(),
                                    log.getAction(),
                                    log.getEntityType(),
                                    log.getNewValue() != null ? log.getNewValue() : "—"
                            });
                        }
                        auditCardLayout.show(auditBody, "TABLE");
                    } else {
                        auditCardLayout.show(auditBody, "EMPTY");
                    }

                    // Populate Enrollments Table
                    enrollmentsModel.setRowCount(0);
                    if (data.getRecentEnrollments() != null && !data.getRecentEnrollments().isEmpty()) {
                        for (Enrollment enr : data.getRecentEnrollments()) {
                            enrollmentsModel.addRow(new Object[]{
                                    enr.getId(),
                                    enr.getStudentId(),
                                    enr.getCourseId(),
                                    enr.getAcademicYear(),
                                    enr.getStatus()
                            });
                        }
                        enrollCardLayout.show(enrollBody, "TABLE");
                    } else {
                        enrollCardLayout.show(enrollBody, "EMPTY");
                    }
                },
                error -> {
                    if (refreshButton != null) refreshButton.setEnabled(true);
                    studentsStatLabel.setText("!");
                    facultyStatLabel.setText("!");
                    coursesStatLabel.setText("!");
                    enrollmentsStatLabel.setText("!");
                    feesStatLabel.setText("!");
                }
        );
    }
}
