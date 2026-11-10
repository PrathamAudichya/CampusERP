package com.college.ui.panel;

import com.college.model.Attendance;
import com.college.model.Course;
import com.college.model.Enrollment;
import com.college.model.Mark;
import com.college.model.Student;
import com.college.model.User;
import com.college.service.AttendanceService;
import com.college.service.AttendanceSessionService;
import com.college.service.StudentDashboardService;
import com.college.service.StudentDashboardService.StudentDashboardData;
import com.college.ui.components.CampusIcons;
import com.college.ui.components.EmptyStatePanel;
import com.college.ui.components.PrimaryButton;
import com.college.ui.components.SecondaryButton;
import com.college.ui.dialog.StudentCheckInDialog;
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
 * Student personal academic and financial dashboard panel for Campus ERP v2.0.5.
 */
public class StudentDashboardPanel extends JPanel {

    private final User currentUser;
    private final StudentDashboardService dashboardService;

    private JLabel welcomeLabel;
    private JLabel subtitleLabel;
    private JLabel attendanceStatLabel;
    private JLabel coursesStatLabel;
    private JLabel feesPaidStatLabel;
    private JLabel feesDueStatLabel;
    private JLabel academicBannerLabel;

    private JTable coursesTable;
    private DefaultTableModel coursesModel;
    private JPanel coursesBody;
    private CardLayout coursesCardLayout;

    private JTable marksTable;
    private DefaultTableModel marksModel;
    private JPanel marksBody;
    private CardLayout marksCardLayout;

    private JButton refreshButton;

    public StudentDashboardPanel(User currentUser) {
        this(currentUser, new StudentDashboardService());
    }

    public StudentDashboardPanel(User currentUser, StudentDashboardService dashboardService) {
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

        welcomeLabel = new JLabel("Welcome to your Student Portal");
        welcomeLabel.setFont(CampusTheme.FONT_TITLE);
        welcomeLabel.setForeground(CampusTheme.TEXT_PRIMARY);

        subtitleLabel = new JLabel("Course enrollments, attendance tracking, and published examination results.");
        subtitleLabel.setFont(CampusTheme.FONT_SMALL);
        subtitleLabel.setForeground(CampusTheme.TEXT_SECONDARY);

        titles.add(welcomeLabel);
        titles.add(subtitleLabel);
        headerPanel.add(titles, BorderLayout.WEST);

        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerActions.setOpaque(false);

        JButton markAttBtn = new PrimaryButton("MARK ATTENDANCE");
        markAttBtn.setIcon(CampusIcons.get(CampusIcons.IconType.CHECK, 14, Color.WHITE));
        markAttBtn.setIconTextGap(8);
        markAttBtn.setPreferredSize(new Dimension(170, 36));
        markAttBtn.setToolTipText("Open interactive check-in to enter 6-digit session verification code");
        markAttBtn.addActionListener(e -> {
            Window parent = SwingUtilities.getWindowAncestor(this);
            StudentCheckInDialog dlg = new StudentCheckInDialog(parent, currentUser, new AttendanceSessionService(), this::loadDashboardData);
            dlg.setVisible(true);
        });
        headerActions.add(markAttBtn);

        refreshButton = new SecondaryButton("Refresh Dashboard");
        refreshButton.setIcon(CampusIcons.get(CampusIcons.IconType.REFRESH, 14, CampusTheme.TEXT_SECONDARY));
        refreshButton.setIconTextGap(8);
        refreshButton.setPreferredSize(new Dimension(150, 36));
        refreshButton.addActionListener(e -> loadDashboardData());
        headerActions.add(refreshButton);
        headerPanel.add(headerActions, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Center: KPI Cards + Detail Tables
        JPanel centerPanel = new JPanel(new BorderLayout(16, 16));
        centerPanel.setOpaque(false);

        // KPI Metric Cards
        JPanel kpiGrid = new JPanel(new GridLayout(1, 4, 14, 14));
        kpiGrid.setOpaque(false);

        attendanceStatLabel = new JLabel("No Classes", SwingConstants.CENTER);
        coursesStatLabel = new JLabel("0 Enrolled", SwingConstants.CENTER);
        feesPaidStatLabel = new JLabel("₹0.00", SwingConstants.CENTER);
        feesDueStatLabel = new JLabel("₹0.00", SwingConstants.CENTER);

        kpiGrid.add(createMetricCard("Overall Attendance", attendanceStatLabel, CampusTheme.PRIMARY, CampusIcons.IconType.ATTENDANCE));
        kpiGrid.add(createMetricCard("Enrolled Courses", coursesStatLabel, CampusTheme.TERRACOTTA, CampusIcons.IconType.COURSES));
        kpiGrid.add(createMetricCard("Total Fees Paid", feesPaidStatLabel, CampusTheme.SUCCESS, CampusIcons.IconType.FEES));
        kpiGrid.add(createMetricCard("Outstanding Dues", feesDueStatLabel, CampusTheme.DANGER, CampusIcons.IconType.FEES));

        centerPanel.add(kpiGrid, BorderLayout.NORTH);

        // Split tables: Enrolled Courses on Left, Marks on Right
        JPanel tablesPanel = new JPanel(new GridLayout(1, 2, 16, 16));
        tablesPanel.setOpaque(false);

        // Enrolled Courses Table Card
        JPanel courseCard = SwingUtils.createCardPanel("My Enrolled Courses & Attendance");
        String[] cCols = {"Course Code", "Title", "Semester", "Attendance %"};
        coursesModel = new DefaultTableModel(cCols, 0);
        coursesTable = SwingUtils.createStandardTable(coursesModel);

        coursesCardLayout = new CardLayout();
        coursesBody = new JPanel(coursesCardLayout);
        coursesBody.setOpaque(false);
        coursesBody.add(new JScrollPane(coursesTable), "TABLE");
        coursesBody.add(new EmptyStatePanel(CampusIcons.IconType.COURSES, "No Enrolled Courses Found",
                "You are not registered in any active courses for this academic year."), "EMPTY");
        courseCard.add(coursesBody, BorderLayout.CENTER);
        tablesPanel.add(courseCard);

        // Marks Table Card
        JPanel marksCard = SwingUtils.createCardPanel("Recent Examination Results");
        String[] mCols = {"Exam / Assessment", "Score", "Grade"};
        marksModel = new DefaultTableModel(mCols, 0);
        marksTable = SwingUtils.createStandardTable(marksModel);

        marksCardLayout = new CardLayout();
        marksBody = new JPanel(marksCardLayout);
        marksBody.setOpaque(false);
        marksBody.add(new JScrollPane(marksTable), "TABLE");
        marksBody.add(new EmptyStatePanel(CampusIcons.IconType.MARKS, "No Examination Results Available",
                "Official grades and examination marks will appear here once published by faculty."), "EMPTY");
        marksCard.add(marksBody, BorderLayout.CENTER);
        tablesPanel.add(marksCard);

        centerPanel.add(tablesPanel, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // Bottom Institutional System Health Banner
        JPanel alertBanner = new JPanel(new BorderLayout());
        alertBanner.setBackground(CampusTheme.BG_CARD);
        alertBanner.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(CampusTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)
        ));

        JLabel healthLabel = new JLabel("Student Portal • Minimum 75% Attendance Required for Examination Eligibility");
        healthLabel.setIcon(CampusIcons.get(CampusIcons.IconType.CHECK, 14, CampusTheme.SUCCESS));
        healthLabel.setIconTextGap(8);
        healthLabel.setFont(CampusTheme.FONT_MICRO);
        healthLabel.setForeground(CampusTheme.TEXT_SECONDARY);
        alertBanner.add(healthLabel, BorderLayout.WEST);

        academicBannerLabel = new JLabel("Role: ENROLLED STUDENT");
        academicBannerLabel.setFont(CampusTheme.FONT_MICRO);
        academicBannerLabel.setForeground(CampusTheme.TERRACOTTA);
        alertBanner.add(academicBannerLabel, BorderLayout.EAST);

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
                error -> {
                    refreshButton.setEnabled(true);
                    SwingUtils.showError(this, "Failed to load dashboard metrics: " + error.getMessage());
                }
        );
    }

    private void renderDashboard(StudentDashboardData data) {
        Student s = data.getStudentProfile();
        if (s != null) {
            welcomeLabel.setText("Welcome, " + s.getFirstName() + " " + s.getLastName());
            String prog = (s.getProgramName() != null) ? s.getProgramName() + " (" + s.getProgramCode() + ")" : "General Studies";
            String dept = (s.getDepartmentName() != null) ? s.getDepartmentName() : "Department #" + s.getDepartmentId();
            String cohort = (s.getCohortCode() != null) ? "Cohort: " + s.getCohortCode() : "Class of " + (s.getExpectedGraduationYear() != null ? s.getExpectedGraduationYear() : "2030");
            subtitleLabel.setText("ROLE: STUDENT • ID: " + s.getStudentId() + " • " + prog + " • " + dept + " • Sem " + (s.getSemester() != null ? s.getSemester() : "1") + " • " + cohort);
            if (academicBannerLabel != null) {
                String sch = s.getSchoolName() != null ? s.getSchoolName() : "Faculty of Engineering & Technology";
                String deg = s.getDegree() != null ? s.getDegree() : "Degree";
                String grad = s.getExpectedGraduationYear() != null ? "Grad: " + s.getExpectedGraduationYear() : "Grad: 2030";
                academicBannerLabel.setText(sch + " • " + deg + " • " + grad);
            }
        }

        // Attendance KPI
        boolean hasAttendance = data.getAttendanceByEnrollment() != null && !data.getAttendanceByEnrollment().isEmpty();
        if (hasAttendance) {
            double attPct = data.getOverallAttendancePercentage();
            attendanceStatLabel.setText(String.format("%.1f%%", attPct));
            attendanceStatLabel.setForeground(attPct >= 75.0 ? CampusTheme.SUCCESS : CampusTheme.DANGER);
        } else {
            attendanceStatLabel.setText("No Classes");
            attendanceStatLabel.setForeground(CampusTheme.TEXT_MUTED);
        }

        // Courses KPI
        int cCount = data.getEnrollments() != null ? data.getEnrollments().size() : 0;
        coursesStatLabel.setText(cCount == 0 ? "0 Enrolled" : cCount + (cCount == 1 ? " Course" : " Courses"));

        // Fees KPIs
        feesPaidStatLabel.setText(SwingUtils.formatPaise(data.getTotalFeesPaidPaise()));
        long dues = data.getTotalOutstandingPaise();
        feesDueStatLabel.setText(SwingUtils.formatPaise(dues));
        feesDueStatLabel.setForeground(dues > 0 ? CampusTheme.DANGER : CampusTheme.SUCCESS);

        // Populate Courses Table
        coursesModel.setRowCount(0);
        Map<Integer, Course> catalog = data.getCourseCatalog();
        Map<Integer, List<Attendance>> attMap = data.getAttendanceByEnrollment();

        if (data.getEnrollments() != null && !data.getEnrollments().isEmpty()) {
            for (Enrollment enr : data.getEnrollments()) {
                Course c = catalog.get(enr.getCourseId());
                String code = (c != null) ? c.getCourseCode() : "C#" + enr.getCourseId();
                String title = (c != null) ? c.getName() : "Course";
                String sem = (c != null) ? c.getSemester() : "1";

                List<Attendance> attList = attMap.get(enr.getId());
                String attStr = "100.0%";
                if (attList != null && !attList.isEmpty()) {
                    double pct = AttendanceService.calculatePercentage(attList);
                    long presentCount = attList.stream().filter(a -> "PRESENT".equalsIgnoreCase(a.getStatus())).count();
                    long lateCount = attList.stream().filter(a -> "LATE".equalsIgnoreCase(a.getStatus())).count();
                    attStr = String.format("%.1f%% (P:%d, L:%d, Total:%d)", pct, presentCount, lateCount, attList.size());
                }

                coursesModel.addRow(new Object[]{code, title, "Semester " + sem, attStr});
            }
            coursesCardLayout.show(coursesBody, "TABLE");
        } else {
            coursesCardLayout.show(coursesBody, "EMPTY");
        }

        // Populate Marks Table
        marksModel.setRowCount(0);
        if (data.getMarks() != null && !data.getMarks().isEmpty()) {
            for (Mark m : data.getMarks()) {
                marksModel.addRow(new Object[]{
                        "Exam #" + m.getExaminationId(),
                        m.getMarksObtained(),
                        m.getGrade()
                });
            }
            marksCardLayout.show(marksBody, "TABLE");
        } else {
            marksCardLayout.show(marksBody, "EMPTY");
        }
    }
}
