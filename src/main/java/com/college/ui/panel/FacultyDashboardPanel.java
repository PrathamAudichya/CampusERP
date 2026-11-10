package com.college.ui.panel;

import com.college.model.Course;
import com.college.model.CourseAssignment;
import com.college.model.Examination;
import com.college.model.Faculty;
import com.college.model.User;
import com.college.service.FacultyDashboardService;
import com.college.service.FacultyDashboardService.FacultyDashboardData;
import com.college.ui.components.CampusIcons;
import com.college.ui.components.EmptyStatePanel;
import com.college.ui.components.SecondaryButton;
import com.college.ui.theme.CampusTheme;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Faculty personal academic and workload dashboard panel for Campus ERP v2.0.5.
 */
public class FacultyDashboardPanel extends JPanel {

    private final User currentUser;
    private final FacultyDashboardService dashboardService;

    private JLabel welcomeLabel;
    private JLabel subtitleLabel;
    private JLabel coursesStatLabel;
    private JLabel studentsStatLabel;
    private JLabel examsStatLabel;

    private JTable workloadTable;
    private DefaultTableModel workloadModel;
    private JPanel workloadBody;
    private CardLayout workloadCardLayout;

    private JTable examsTable;
    private DefaultTableModel examsModel;
    private JPanel examsBody;
    private CardLayout examsCardLayout;

    private JButton refreshButton;

    public FacultyDashboardPanel(User currentUser) {
        this(currentUser, new FacultyDashboardService());
    }

    public FacultyDashboardPanel(User currentUser, FacultyDashboardService dashboardService) {
        this.currentUser = currentUser;
        this.dashboardService = dashboardService;
        initUI();
        loadDashboardData();
    }

    private void initUI() {
        setLayout(new BorderLayout(16, 16));
        setBackground(CampusTheme.BG_MAIN);
        setBorder(new EmptyBorder(20, 20, 20, 20));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JPanel titles = new JPanel(new GridLayout(2, 1, 2, 2));
        titles.setOpaque(false);

        welcomeLabel = new JLabel("Welcome to your Faculty Portal");
        welcomeLabel.setFont(CampusTheme.FONT_TITLE);
        welcomeLabel.setForeground(CampusTheme.TEXT_PRIMARY);

        subtitleLabel = new JLabel("Teaching assignments, course rosters, and scheduled student evaluations.");
        subtitleLabel.setFont(CampusTheme.FONT_SMALL);
        subtitleLabel.setForeground(CampusTheme.TEXT_SECONDARY);

        titles.add(welcomeLabel);
        titles.add(subtitleLabel);
        headerPanel.add(titles, BorderLayout.WEST);

        refreshButton = new SecondaryButton("Refresh Dashboard");
        refreshButton.setIcon(CampusIcons.get(CampusIcons.IconType.REFRESH, 14, CampusTheme.TEXT_SECONDARY));
        refreshButton.setIconTextGap(8);
        refreshButton.setPreferredSize(new Dimension(150, 36));
        refreshButton.addActionListener(e -> loadDashboardData());
        headerPanel.add(refreshButton, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Center: KPI Cards + Detail Tables
        JPanel centerPanel = new JPanel(new BorderLayout(16, 16));
        centerPanel.setOpaque(false);

        // KPI Metric Cards
        JPanel kpiGrid = new JPanel(new GridLayout(1, 3, 16, 16));
        kpiGrid.setOpaque(false);

        coursesStatLabel = new JLabel("—", SwingConstants.CENTER);
        studentsStatLabel = new JLabel("—", SwingConstants.CENTER);
        examsStatLabel = new JLabel("—", SwingConstants.CENTER);

        kpiGrid.add(createMetricCard("Assigned Courses", coursesStatLabel, CampusTheme.PRIMARY, CampusIcons.IconType.COURSES));
        kpiGrid.add(createMetricCard("Total Enrolled Students", studentsStatLabel, CampusTheme.SUCCESS, CampusIcons.IconType.STUDENTS));
        kpiGrid.add(createMetricCard("Scheduled Examinations", examsStatLabel, CampusTheme.TERRACOTTA, CampusIcons.IconType.EXAMINATIONS));

        centerPanel.add(kpiGrid, BorderLayout.NORTH);

        // Split tables: Course Workload on Left, Scheduled Exams on Right
        JPanel tablesPanel = new JPanel(new GridLayout(1, 2, 16, 16));
        tablesPanel.setOpaque(false);

        // Workload Table Card
        JPanel workloadCard = SwingUtils.createCardPanel("My Assigned Teaching Workload");
        String[] wCols = {"Course Code", "Title", "Semester", "Active Students"};
        workloadModel = new DefaultTableModel(wCols, 0);
        workloadTable = SwingUtils.createStandardTable(workloadModel);

        workloadCardLayout = new CardLayout();
        workloadBody = new JPanel(workloadCardLayout);
        workloadBody.setOpaque(false);
        workloadBody.add(new JScrollPane(workloadTable), "TABLE");
        workloadBody.add(new EmptyStatePanel(CampusIcons.IconType.COURSES, "No Teaching Assignments",
                "You are not currently assigned to any courses for the active academic session."), "EMPTY");
        workloadCard.add(workloadBody, BorderLayout.CENTER);
        tablesPanel.add(workloadCard);

        // Exams Table Card
        JPanel examsCard = SwingUtils.createCardPanel("Upcoming Scheduled Examinations");
        String[] eCols = {"Course", "Examination Name", "Date", "Type", "Max Marks"};
        examsModel = new DefaultTableModel(eCols, 0);
        examsTable = SwingUtils.createStandardTable(examsModel);

        examsCardLayout = new CardLayout();
        examsBody = new JPanel(examsCardLayout);
        examsBody.setOpaque(false);
        examsBody.add(new JScrollPane(examsTable), "TABLE");
        examsBody.add(new EmptyStatePanel(CampusIcons.IconType.EXAMINATIONS, "No Scheduled Examinations",
                "Upcoming exams, midterms, and finals for your courses will appear here."), "EMPTY");
        examsCard.add(examsBody, BorderLayout.CENTER);
        tablesPanel.add(examsCard);

        centerPanel.add(tablesPanel, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // Bottom Institutional System Health Banner
        JPanel alertBanner = new JPanel(new BorderLayout());
        alertBanner.setBackground(CampusTheme.BG_CARD);
        alertBanner.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(CampusTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)
        ));

        JLabel healthLabel = new JLabel("Faculty Portal • Academic Year 2026-2027 • Grading & Attendance Open");
        healthLabel.setIcon(CampusIcons.get(CampusIcons.IconType.CHECK, 14, CampusTheme.SUCCESS));
        healthLabel.setIconTextGap(8);
        healthLabel.setFont(CampusTheme.FONT_MICRO);
        healthLabel.setForeground(CampusTheme.TEXT_SECONDARY);
        alertBanner.add(healthLabel, BorderLayout.WEST);

        JLabel secureLabel = new JLabel("Role: ACADEMIC FACULTY");
        secureLabel.setFont(CampusTheme.FONT_MICRO);
        secureLabel.setForeground(CampusTheme.INFO);
        alertBanner.add(secureLabel, BorderLayout.EAST);

        add(alertBanner, BorderLayout.SOUTH);
    }

    private JPanel createMetricCard(String title, JLabel valueLabel, Color accentColor, CampusIcons.IconType iconType) {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBackground(CampusTheme.BG_CARD);
        card.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(CampusTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
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
        refreshButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> dashboardService.getDashboardData(currentUser, "2026-2027"),
                data -> {
                    refreshButton.setEnabled(true);
                    renderDashboard(data);
                },
                error -> refreshButton.setEnabled(true)
        );
    }

    private void renderDashboard(FacultyDashboardData data) {
        Faculty f = data.getFacultyProfile();
        if (f != null) {
            welcomeLabel.setText("Welcome, Prof. " + f.getFirstName() + " " + f.getLastName());
            String dept = (f.getDepartmentName() != null) ? f.getDepartmentName() : "Department #" + f.getDepartmentId();
            String sch = (f.getSchoolName() != null) ? f.getSchoolName() + " • " : "";
            subtitleLabel.setText("ROLE: FACULTY • ID: " + f.getFacultyId() + " • " + sch + dept + " • " + (f.getSpecialization() != null ? f.getSpecialization() : "Academic Faculty"));
        }

        // Stats calculation
        int assignedCount = (data.getAssignments() != null) ? data.getAssignments().size() : 0;
        coursesStatLabel.setText(String.valueOf(assignedCount));

        int totalStudents = 0;
        Map<Integer, Integer> enrollmentCounts = data.getActiveEnrollmentCounts();
        if (enrollmentCounts != null) {
            for (Integer cnt : enrollmentCounts.values()) {
                totalStudents += (cnt != null ? cnt : 0);
            }
        }
        studentsStatLabel.setText(String.valueOf(totalStudents));

        int examCount = 0;
        Map<Integer, List<Examination>> examMap = data.getScheduledExaminations();
        if (examMap != null) {
            for (List<Examination> list : examMap.values()) {
                examCount += (list != null ? list.size() : 0);
            }
        }
        examsStatLabel.setText(String.valueOf(examCount));

        // Populate Workload Table
        workloadModel.setRowCount(0);
        Map<Integer, Course> catalog = data.getCourseCatalog();
        if (data.getAssignments() != null && !data.getAssignments().isEmpty()) {
            for (CourseAssignment a : data.getAssignments()) {
                Course c = catalog.get(a.getCourseId());
                String code = (c != null) ? c.getCourseCode() : "Course #" + a.getCourseId();
                String title = (c != null) ? c.getName() : "Course Title";
                String sem = (c != null) ? c.getSemester() : "1";
                int count = enrollmentCounts != null ? enrollmentCounts.getOrDefault(a.getCourseId(), 0) : 0;
                workloadModel.addRow(new Object[]{code, title, "Semester " + sem, count + " enrolled"});
            }
            workloadCardLayout.show(workloadBody, "TABLE");
        } else {
            workloadCardLayout.show(workloadBody, "EMPTY");
        }

        // Populate Exams Table
        examsModel.setRowCount(0);
        boolean hasExams = false;
        if (examMap != null && !examMap.isEmpty()) {
            for (Map.Entry<Integer, List<Examination>> entry : examMap.entrySet()) {
                Course c = catalog.get(entry.getKey());
                String cName = (c != null) ? c.getCourseCode() : "Course #" + entry.getKey();
                for (Examination ex : entry.getValue()) {
                    hasExams = true;
                    examsModel.addRow(new Object[]{
                            cName,
                            ex.getExamName(),
                            ex.getExamDate(),
                            ex.getExamType(),
                            ex.getTotalMarks()
                    });
                }
            }
        }
        if (hasExams) {
            examsCardLayout.show(examsBody, "TABLE");
        } else {
            examsCardLayout.show(examsBody, "EMPTY");
        }
    }
}
