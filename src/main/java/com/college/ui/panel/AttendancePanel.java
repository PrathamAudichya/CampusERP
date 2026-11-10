package com.college.ui.panel;

import com.college.dao.FacultyDAO;
import com.college.dao.StudentDAO;
import com.college.model.Attendance;
import com.college.model.AttendanceSession;
import com.college.model.AttendanceSubmission;
import com.college.model.Course;
import com.college.model.CourseAssignment;
import com.college.model.Enrollment;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.User;
import com.college.service.AttendanceService;
import com.college.service.AttendanceService.AttendanceEntry;
import com.college.service.AttendanceSessionService;
import com.college.service.CourseAssignmentService;
import com.college.service.CourseService;
import com.college.service.EnrollmentService;
import com.college.ui.components.CampusIcons;
import com.college.ui.components.PrimaryButton;
import com.college.ui.components.SecondaryButton;
import com.college.ui.dialog.ReviewAttendanceDialog;
import com.college.ui.dialog.StudentCheckInDialog;
import com.college.ui.theme.CampusTheme;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.RowFilter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Attendance recording and inspection panel supporting Admin/Faculty batch marking and Student self-history.
 */
public class AttendancePanel extends JPanel {

    private final User currentUser;
    private final AttendanceService attendanceService;
    private final CourseService courseService;
    private final EnrollmentService enrollmentService;
    private final CourseAssignmentService assignmentService;
    private final StudentDAO studentDAO;
    private final FacultyDAO facultyDAO;
    private final AttendanceSessionService sessionService;

    private JComboBox<CourseItem> courseCombo;
    private JTextField dateField;
    private JTextField academicYearField;
    private JTextField sessionNumberField;
    private JTable rosterTable;
    private DefaultTableModel rosterModel;
    private TableRowSorter<DefaultTableModel> rosterSorter;
    private JTextField rosterSearchField;
    private JButton loadRosterButton;
    private JButton submitBatchButton;
    private JButton markAllPresentButton;
    private JButton markSelectedPresentBtn;
    private JButton markSelectedAbsentBtn;
    private JButton markSelectedLateBtn;
    private JButton verifyDobButton;
    private JLabel sessionCodeLabel;
    private JLabel rosterSummaryLabel;
    private String currentSessionCode;

    private JTable historyTable;
    private DefaultTableModel historyModel;
    private JLabel studentSummaryLabel;
    private JLabel historySummaryLabel;

    private final Map<Integer, String> studentNameMap = new HashMap<>();
    private final Map<Integer, Integer> enrollmentToStudentIdMap = new HashMap<>();

    public AttendancePanel(User currentUser) {
        this(currentUser, new AttendanceService(), new CourseService(), new EnrollmentService(),
                new CourseAssignmentService(), new StudentDAO(), new FacultyDAO(), new AttendanceSessionService());
    }

    public AttendancePanel(User currentUser, AttendanceService attendanceService, CourseService courseService,
                           EnrollmentService enrollmentService, CourseAssignmentService assignmentService,
                           StudentDAO studentDAO, FacultyDAO facultyDAO) {
        this(currentUser, attendanceService, courseService, enrollmentService, assignmentService,
                studentDAO, facultyDAO, new AttendanceSessionService());
    }

    public AttendancePanel(User currentUser, AttendanceService attendanceService, CourseService courseService,
                           EnrollmentService enrollmentService, CourseAssignmentService assignmentService,
                           StudentDAO studentDAO, FacultyDAO facultyDAO, AttendanceSessionService sessionService) {
        this.currentUser = currentUser;
        this.attendanceService = attendanceService;
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
        this.assignmentService = assignmentService;
        this.studentDAO = studentDAO;
        this.facultyDAO = facultyDAO;
        this.sessionService = sessionService;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(12, 12));
        setBackground(SwingUtils.BG_LIGHT);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        currentSessionCode = attendanceService.generateSessionCode();

        if (currentUser.getRole() == Role.STUDENT) {
            initStudentView();
        } else {
            initAdminFacultyView();
        }
    }

    private void initStudentView() {
        JPanel headerPanel = new JPanel(new BorderLayout(8, 8));
        headerPanel.setOpaque(false);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JLabel titleLabel = new JLabel("My Attendance Records");
        titleLabel.setFont(SwingUtils.TITLE_FONT);
        titleLabel.setForeground(SwingUtils.PRIMARY_COLOR);
        titleRow.add(titleLabel, BorderLayout.WEST);

        JButton refreshBtn = SwingUtils.createSecondaryButton("Refresh");
        refreshBtn.addActionListener(e -> loadStudentAttendance());
        titleRow.add(refreshBtn, BorderLayout.EAST);
        headerPanel.add(titleRow, BorderLayout.NORTH);

        JPanel cardsContainer = new JPanel();
        cardsContainer.setLayout(new BoxLayout(cardsContainer, BoxLayout.Y_AXIS));
        cardsContainer.setOpaque(false);

        // Self-Service Check-In Card
        JPanel checkInCard = SwingUtils.createCardPanel("Live Attendance Check-In (Enter 6-Digit Code)");
        JPanel checkInRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        checkInRow.setOpaque(false);
        JLabel codePrompt = new JLabel("Session Verification Code:");
        codePrompt.setFont(CampusTheme.FONT_BOLD);
        checkInRow.add(codePrompt);

        JTextField checkInField = new JTextField(8);
        checkInField.setFont(new Font("Consolas", Font.BOLD, 18));
        checkInRow.add(checkInField);

        JButton submitCheckInBtn = SwingUtils.createPrimaryButton("Submit Check-In");
        submitCheckInBtn.addActionListener(e -> {
            String code = checkInField.getText().trim();
            if (code.isEmpty()) {
                SwingUtils.showWarning(this, "Please enter the 6-digit session code provided by your instructor.");
                return;
            }
            try {
                AttendanceSubmission sub = sessionService.checkIn(currentUser, code);
                SwingUtils.showInfo(this, "✓ Check-In SUCCESSFUL!\n\n" +
                        "Session Verification Code: " + code + "\n" +
                        "Submission Time: " + sub.getSubmissionTime() + "\n" +
                        "Status: CHECKED IN (Provisional — pending faculty finalization)");
                checkInField.setText("");
                loadStudentAttendance();
            } catch (Exception ex) {
                SwingUtils.showWarning(this, ex.getMessage());
            }
        });
        checkInRow.add(submitCheckInBtn);

        JButton openDialogBtn = SwingUtils.createSecondaryButton("Select Active Session");
        openDialogBtn.addActionListener(e -> {
            Window parent = SwingUtilities.getWindowAncestor(this);
            StudentCheckInDialog dlg = new StudentCheckInDialog(parent, currentUser, sessionService, this::loadStudentAttendance);
            dlg.setVisible(true);
        });
        checkInRow.add(openDialogBtn);

        checkInCard.add(checkInRow, BorderLayout.CENTER);

        cardsContainer.add(checkInCard);
        cardsContainer.add(Box.createVerticalStrut(8));

        JPanel metricCard = SwingUtils.createCardPanel("Attendance Summary & Examination Eligibility");
        studentSummaryLabel = new JLabel("Calculating attendance metrics...");
        studentSummaryLabel.setFont(SwingUtils.BOLD_FONT);
        studentSummaryLabel.setForeground(SwingUtils.PRIMARY_COLOR);
        metricCard.add(studentSummaryLabel, BorderLayout.CENTER);
        cardsContainer.add(metricCard);

        headerPanel.add(cardsContainer, BorderLayout.SOUTH);

        add(headerPanel, BorderLayout.NORTH);

        String[] columns = {"Course", "Date", "Status", "Remarks"};
        historyModel = new DefaultTableModel(columns, 0);
        historyTable = SwingUtils.createStandardTable(historyModel);

        JScrollPane scrollPane = new JScrollPane(historyTable);
        add(scrollPane, BorderLayout.CENTER);

        loadStudentAttendance();
    }

    private void loadStudentAttendance() {
        SwingUtils.executeAsync(
                this,
                () -> {
                    Student s = studentDAO.findByUserId(currentUser.getId());
                    if (s == null) return new Object[]{List.<Object[]>of(), List.<Attendance>of()};

                    List<Enrollment> enrollments = enrollmentService.getEnrollmentsByStudent(currentUser, s.getId());
                    List<Object[]> rows = new ArrayList<>();
                    List<Attendance> allRecords = new ArrayList<>();
                    for (Enrollment enr : enrollments) {
                        Course c = courseService.getCourseById(currentUser, enr.getCourseId());
                        String courseName = (c != null) ? c.getCourseCode() + " - " + c.getName() : "Course #" + enr.getCourseId();
                        List<Attendance> records = attendanceService.getAttendanceByEnrollment(currentUser, enr.getId());
                        allRecords.addAll(records);
                        for (Attendance att : records) {
                            rows.add(new Object[]{courseName, att.getAttendanceDate(), att.getStatus(), att.getRemarks() != null ? att.getRemarks() : ""});
                        }
                    }
                    return new Object[]{rows, allRecords};
                },
                resultObj -> {
                    @SuppressWarnings("unchecked")
                    List<Object[]> rows = (List<Object[]>) ((Object[]) resultObj)[0];
                    @SuppressWarnings("unchecked")
                    List<Attendance> allRecords = (List<Attendance>) ((Object[]) resultObj)[1];

                    historyModel.setRowCount(0);
                    for (Object[] r : rows) {
                        historyModel.addRow(r);
                    }

                    if (studentSummaryLabel != null) {
                        if (allRecords.isEmpty()) {
                            studentSummaryLabel.setText("Total Sessions: 0   |   Attendance: N/A   |   Status: No sessions recorded yet");
                            studentSummaryLabel.setForeground(SwingUtils.MUTED_COLOR);
                        } else {
                            double pct = AttendanceService.calculatePercentage(allRecords);
                            int total = allRecords.size();
                            int present = (int) allRecords.stream().filter(a -> "PRESENT".equalsIgnoreCase(a.getStatus())).count();
                            int late = (int) allRecords.stream().filter(a -> "LATE".equalsIgnoreCase(a.getStatus())).count();
                            int absent = (int) allRecords.stream().filter(a -> "ABSENT".equalsIgnoreCase(a.getStatus())).count();
                            boolean eligible = pct >= 75.0;
                            String statusText = eligible ? "ELIGIBLE FOR EXAMINATIONS (>= 75.0%)" : "DEFAULTER SHORTAGE WARNING (< 75.0%)";
                            studentSummaryLabel.setText(String.format(
                                    "Total Sessions: %d   |   Present: %d   |   Late (0.5x): %d   |   Absent: %d   |   Attendance: %.1f%%   |   Status: %s",
                                    total, present, late, absent, pct, statusText
                            ));
                            studentSummaryLabel.setForeground(eligible ? SwingUtils.SUCCESS_COLOR : SwingUtils.DANGER_COLOR);
                        }
                    }
                }
        );
    }

    private void initAdminFacultyView() {
        JTabbedPane tabbedPane = new JTabbedPane();

        // Tab 1: Batch Attendance Entry
        JPanel markTab = new JPanel(new BorderLayout(12, 12));
        markTab.setBackground(SwingUtils.BG_LIGHT);
        markTab.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel topSection = new JPanel();
        topSection.setLayout(new BoxLayout(topSection, BoxLayout.Y_AXIS));
        topSection.setOpaque(false);

        // Control Bar (Row 1): Course, Year, Date, Load
        JPanel controlBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        controlBar.setOpaque(false);

        controlBar.add(new JLabel("Course:"));
        courseCombo = new JComboBox<>();
        controlBar.add(courseCombo);

        controlBar.add(new JLabel("Academic Year:"));
        academicYearField = new JTextField("2026-2027", 8);
        controlBar.add(academicYearField);

        controlBar.add(new JLabel("Date (YYYY-MM-DD):"));
        dateField = new JTextField(LocalDate.now().toString(), 9);
        controlBar.add(dateField);

        controlBar.add(new JLabel("Session #:"));
        sessionNumberField = new JTextField("1", 4);
        controlBar.add(sessionNumberField);

        loadRosterButton = SwingUtils.createPrimaryButton("Load Roster");
        loadRosterButton.addActionListener(e -> loadCourseRoster());
        controlBar.add(loadRosterButton);

        topSection.add(controlBar);

        // Session Code & Quick Actions Bar (Row 2)
        JPanel sessionBar = new JPanel(new BorderLayout(8, 4));
        sessionBar.setBackground(CampusTheme.BG_CARD);
        sessionBar.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(CampusTheme.BORDER, 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));

        JPanel sessionLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        sessionLeft.setOpaque(false);

        JLabel codeTitle = new JLabel("Session Verification Code:");
        codeTitle.setFont(CampusTheme.FONT_BOLD);
        codeTitle.setForeground(CampusTheme.TEXT_SECONDARY);
        sessionLeft.add(codeTitle);

        sessionCodeLabel = new JLabel(currentSessionCode);
        sessionCodeLabel.setFont(new Font("Consolas", Font.BOLD, 18));
        sessionCodeLabel.setForeground(CampusTheme.PRIMARY);
        sessionLeft.add(sessionCodeLabel);

        JButton regenCodeBtn = SwingUtils.createSecondaryButton("Regenerate");
        regenCodeBtn.setIcon(CampusIcons.get(CampusIcons.IconType.REFRESH, 12, CampusTheme.TEXT_SECONDARY));
        regenCodeBtn.addActionListener(e -> {
            currentSessionCode = attendanceService.generateSessionCode();
            sessionCodeLabel.setText(currentSessionCode);
        });
        sessionLeft.add(regenCodeBtn);

        JButton projectCodeBtn = SwingUtils.createSecondaryButton("Show Code");
        projectCodeBtn.addActionListener(e -> projectSessionCodeDialog());
        sessionLeft.add(projectCodeBtn);

        JButton openSessionBtn = SwingUtils.createPrimaryButton("Open Timed Check-In");
        openSessionBtn.setToolTipText("Launch 6-digit verification code with live student submission monitor & countdown");
        openSessionBtn.addActionListener(e -> showOpenSessionDialog());
        sessionLeft.add(openSessionBtn);

        sessionBar.add(sessionLeft, BorderLayout.WEST);

        // Search & Filter Box
        JPanel sessionRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        sessionRight.setOpaque(false);
        JLabel searchLbl = new JLabel("Search Roster:");
        searchLbl.setFont(CampusTheme.FONT_BOLD);
        sessionRight.add(searchLbl);

        rosterSearchField = new JTextField(14);
        rosterSearchField.setToolTipText("Filter by Student ID or Name");
        rosterSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { filterRoster(); }
            @Override
            public void removeUpdate(DocumentEvent e) { filterRoster(); }
            @Override
            public void changedUpdate(DocumentEvent e) { filterRoster(); }
        });
        sessionRight.add(rosterSearchField);
        sessionBar.add(sessionRight, BorderLayout.EAST);

        topSection.add(Box.createVerticalStrut(4));
        topSection.add(sessionBar);

        // Actions Row (Row 3): Quick Mark Buttons & DOB Verification
        JPanel actionsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actionsRow.setOpaque(false);

        markAllPresentButton = SwingUtils.createSecondaryButton("Mark All Present");
        markAllPresentButton.addActionListener(e -> markAllStatus("PRESENT"));
        actionsRow.add(markAllPresentButton);

        markSelectedPresentBtn = SwingUtils.createSecondaryButton("Mark Selected Present");
        markSelectedPresentBtn.addActionListener(e -> markSelectedStatus("PRESENT"));
        actionsRow.add(markSelectedPresentBtn);

        markSelectedAbsentBtn = SwingUtils.createSecondaryButton("Mark Selected Absent");
        markSelectedAbsentBtn.addActionListener(e -> markSelectedStatus("ABSENT"));
        actionsRow.add(markSelectedAbsentBtn);

        markSelectedLateBtn = SwingUtils.createSecondaryButton("Mark Selected Late");
        markSelectedLateBtn.addActionListener(e -> markSelectedStatus("LATE"));
        actionsRow.add(markSelectedLateBtn);

        verifyDobButton = SwingUtils.createSecondaryButton("Verify Student DOB");
        verifyDobButton.setIcon(CampusIcons.get(CampusIcons.IconType.CHECK, 13, CampusTheme.TEXT_SECONDARY));
        verifyDobButton.addActionListener(e -> openVerifyDobDialog());
        actionsRow.add(verifyDobButton);

        topSection.add(actionsRow);

        markTab.add(topSection, BorderLayout.NORTH);

        // Editable table for batch entry (5 columns)
        String[] cols = {"Enrollment ID", "Student ID", "Student Name", "Attendance Status", "Remarks"};
        rosterModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 3 || column == 4;
            }
        };
        rosterModel.addTableModelListener(e -> updateRosterSummary());

        rosterTable = new JTable(rosterModel);
        rosterTable.setFont(SwingUtils.REGULAR_FONT);
        rosterTable.setRowHeight(25);
        rosterTable.getTableHeader().setFont(SwingUtils.HEADER_FONT);
        rosterSorter = new TableRowSorter<>(rosterModel);
        rosterTable.setRowSorter(rosterSorter);

        // Setup dropdown editor for status
        JComboBox<String> statusDropdown = new JComboBox<>(new String[]{"PRESENT", "ABSENT", "LATE"});
        rosterTable.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(statusDropdown));

        JScrollPane scrollPane = new JScrollPane(rosterTable);
        markTab.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new BorderLayout(12, 6));
        bottomBar.setOpaque(false);

        rosterSummaryLabel = new JLabel("Roster Summary: No students loaded.");
        rosterSummaryLabel.setFont(CampusTheme.FONT_SMALL_BOLD);
        rosterSummaryLabel.setForeground(CampusTheme.TEXT_SECONDARY);
        bottomBar.add(rosterSummaryLabel, BorderLayout.WEST);

        submitBatchButton = SwingUtils.createPrimaryButton("Submit Batch Attendance");
        submitBatchButton.setEnabled(false);
        submitBatchButton.addActionListener(e -> submitBatchAttendance());
        bottomBar.add(submitBatchButton, BorderLayout.EAST);

        markTab.add(bottomBar, BorderLayout.SOUTH);
        tabbedPane.addTab("Mark Attendance", markTab);

        // Tab 2: Attendance History by Course
        JPanel historyTab = new JPanel(new BorderLayout(12, 12));
        historyTab.setBackground(SwingUtils.BG_LIGHT);
        historyTab.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel histControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        histControls.setOpaque(false);

        JButton viewHistBtn = SwingUtils.createPrimaryButton("View Attendance for Selected Course & Date");
        viewHistBtn.addActionListener(e -> loadCourseAttendanceHistory());
        histControls.add(viewHistBtn);

        historySummaryLabel = new JLabel("");
        historySummaryLabel.setFont(SwingUtils.BOLD_FONT);
        histControls.add(historySummaryLabel);
        historyTab.add(histControls, BorderLayout.NORTH);

        String[] hCols = {"Attendance ID", "Enrollment ID", "Date", "Status", "Remarks"};
        historyModel = new DefaultTableModel(hCols, 0);
        historyTable = SwingUtils.createStandardTable(historyModel);
        historyTab.add(new JScrollPane(historyTable), BorderLayout.CENTER);

        tabbedPane.addTab("Attendance History", historyTab);

        add(tabbedPane, BorderLayout.CENTER);

        loadAssignedCourses();
    }

    private void loadAssignedCourses() {
        String year = (academicYearField != null) ? academicYearField.getText().trim() : "2026-2027";
        SwingUtils.executeAsync(
                this,
                () -> {
                    if (currentUser.getRole() == Role.FACULTY) {
                        Faculty f = facultyDAO.findByUserId(currentUser.getId());
                        if (f == null) return List.<Course>of();
                        List<CourseAssignment> assignments = assignmentService.getActiveAssignmentsByFacultyAndYear(currentUser, f.getId(), year);
                        List<Course> list = new ArrayList<>();
                        for (CourseAssignment a : assignments) {
                            Course c = courseService.getCourseById(currentUser, a.getCourseId());
                            if (c != null && c.isActive()) list.add(c);
                        }
                        return list;
                    } else {
                        return courseService.getAllActiveCourses(currentUser);
                    }
                },
                courses -> {
                    courseCombo.removeAllItems();
                    for (Course c : courses) {
                        courseCombo.addItem(new CourseItem(c.getId(), c.getCourseCode() + " - " + c.getName()));
                    }
                }
        );
    }

    private void filterRoster() {
        if (rosterSorter == null) return;
        String query = rosterSearchField.getText().trim();
        if (query.isEmpty()) {
            rosterSorter.setRowFilter(null);
        } else {
            rosterSorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(query)));
        }
    }

    private void markAllStatus(String status) {
        for (int i = 0; i < rosterModel.getRowCount(); i++) {
            rosterModel.setValueAt(status, i, 3);
        }
        updateRosterSummary();
    }

    private void markSelectedStatus(String status) {
        int[] selectedRows = rosterTable.getSelectedRows();
        if (selectedRows.length == 0) {
            SwingUtils.showWarning(this, "Please select at least one student row.");
            return;
        }
        for (int viewRow : selectedRows) {
            int modelRow = rosterTable.convertRowIndexToModel(viewRow);
            rosterModel.setValueAt(status, modelRow, 3);
        }
        updateRosterSummary();
    }

    private void updateRosterSummary() {
        int total = rosterModel.getRowCount();
        if (total == 0) {
            if (rosterSummaryLabel != null) {
                rosterSummaryLabel.setText("Roster Summary: No students loaded.");
            }
            return;
        }
        int present = 0;
        int absent = 0;
        int late = 0;
        for (int i = 0; i < total; i++) {
            String st = (String) rosterModel.getValueAt(i, 3);
            if ("PRESENT".equalsIgnoreCase(st)) present++;
            else if ("ABSENT".equalsIgnoreCase(st)) absent++;
            else if ("LATE".equalsIgnoreCase(st)) late++;
        }
        int unmarked = total - (present + absent + late);
        if (rosterSummaryLabel != null) {
            rosterSummaryLabel.setText(String.format(
                    "Roster Summary: Total: %d  |  Present: %d  |  Late: %d  |  Absent: %d%s",
                    total, present, late, absent,
                    unmarked > 0 ? "  |  Unmarked: " + unmarked : ""
            ));
        }
    }

    private void openVerifyDobDialog() {
        int selectedRow = rosterTable.getSelectedRow();
        if (selectedRow < 0) {
            SwingUtils.showWarning(this, "Please select a student row to verify Date of Birth.");
            return;
        }
        int modelRow = rosterTable.convertRowIndexToModel(selectedRow);
        int enrId = (Integer) rosterModel.getValueAt(modelRow, 0);
        String studentNaturalId = (String) rosterModel.getValueAt(modelRow, 1);
        String studentName = (String) rosterModel.getValueAt(modelRow, 2);
        Integer studentId = enrollmentToStudentIdMap.get(enrId);

        if (studentId == null) {
            SwingUtils.showWarning(this, "Student record link not found for selected enrollment.");
            return;
        }

        String dobInput = JOptionPane.showInputDialog(
                this,
                "Verify Date of Birth for " + studentName + " (" + studentNaturalId + ")\n" +
                "Enter DOB (YYYY-MM-DD):",
                "Student DOB Verification",
                JOptionPane.QUESTION_MESSAGE
        );

        if (dobInput == null || dobInput.trim().isEmpty()) {
            return;
        }

        boolean matches = attendanceService.verifyStudentDob(studentId, dobInput.trim());
        if (matches) {
            JOptionPane.showMessageDialog(
                    this,
                    "✓ Date of Birth VERIFIED successfully against institutional records.",
                    "DOB Match Confirmed",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } else {
            JOptionPane.showMessageDialog(
                    this,
                    "✗ Date of Birth DOES NOT MATCH institutional records for this student.",
                    "DOB Mismatch Warning",
                    JOptionPane.WARNING_MESSAGE
            );
        }
    }

    private void projectSessionCodeDialog() {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "Session Verification Code", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setSize(440, 260);
        dlg.setLocationRelativeTo(this);
        dlg.setResizable(false);
        dlg.setLayout(new BorderLayout());
        dlg.getContentPane().setBackground(CampusTheme.BG_CARD);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(24, 24, 16, 24));

        JLabel title = new JLabel("ATTENDANCE SESSION CODE");
        title.setFont(CampusTheme.FONT_TITLE);
        title.setForeground(CampusTheme.TEXT_SECONDARY);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(14));

        JLabel codeDisplay = new JLabel(currentSessionCode, SwingConstants.CENTER);
        codeDisplay.setFont(new Font("Consolas", Font.BOLD, 46));
        codeDisplay.setForeground(CampusTheme.PRIMARY);
        codeDisplay.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(codeDisplay);
        content.add(Box.createVerticalStrut(10));

        JLabel sub = new JLabel("Students: Verify this code with instructor during class session.", SwingConstants.CENTER);
        sub.setFont(CampusTheme.FONT_SMALL);
        sub.setForeground(CampusTheme.TEXT_MUTED);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(sub);

        dlg.add(content, BorderLayout.CENTER);

        JPanel bot = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bot.setOpaque(false);
        JButton close = new PrimaryButton("Close");
        close.setPreferredSize(new Dimension(100, 34));
        close.addActionListener(e -> dlg.dispose());
        bot.add(close);
        dlg.add(bot, BorderLayout.SOUTH);

        dlg.setVisible(true);
    }

    private void showOpenSessionDialog() {
        CourseItem sel = (CourseItem) courseCombo.getSelectedItem();
        if (sel == null) {
            SwingUtils.showWarning(this, "Please select a course first.");
            return;
        }
        String year = academicYearField.getText().trim();
        String sessNum = (sessionNumberField != null && !sessionNumberField.getText().trim().isEmpty())
                ? sessionNumberField.getText().trim() : "1";

        String[] options = {"5 Minutes", "10 Minutes", "15 Minutes", "30 Minutes"};
        String choice = (String) JOptionPane.showInputDialog(this,
                "Select session check-in window duration for " + sel.name + "\n(Class Session #" + sessNum + "):",
                "Open Check-In Session",
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                "10 Minutes");
        if (choice == null) return;
        int mins = Integer.parseInt(choice.split(" ")[0]);

        try {
            AttendanceSession session = sessionService.openSession(currentUser, sel.id, year, mins, sessNum);
            currentSessionCode = session.getSessionCode();
            sessionCodeLabel.setText(currentSessionCode);

            Window parent = SwingUtilities.getWindowAncestor(this);
            LiveSessionMonitorDialog dlg = new LiveSessionMonitorDialog(parent, session, sessionService, currentUser, this::loadCourseRoster);
            dlg.setVisible(true);
        } catch (Exception ex) {
            SwingUtils.showWarning(this, "Failed to open session: " + ex.getMessage());
        }
    }

    public static class LiveSessionMonitorDialog extends JDialog {
        private final AttendanceSession session;
        private final AttendanceSessionService sessionService;
        private final User currentUser;
        private final Runnable onFinalize;
        private final javax.swing.Timer countdownTimer;
        private final JLabel countdownLabel;
        private final JLabel countsLabel;
        private final DefaultTableModel subModel;
        private final JLabel statusLabel;
        private final JLabel codeLbl;

        public LiveSessionMonitorDialog(Window parent, AttendanceSession session, AttendanceSessionService sessionService,
                                      User currentUser, Runnable onFinalize) {
            super(parent, "Live Attendance Check-In Monitor — " + session.getSessionCode(), ModalityType.APPLICATION_MODAL);
            this.session = session;
            this.sessionService = sessionService;
            this.currentUser = currentUser;
            this.onFinalize = onFinalize;
            setSize(680, 560);
            setLocationRelativeTo(parent);
            setLayout(new BorderLayout(14, 14));
            getContentPane().setBackground(CampusTheme.BG_CARD);

            JPanel north = new JPanel(new BorderLayout(6, 6));
            north.setOpaque(false);
            north.setBorder(BorderFactory.createEmptyBorder(16, 20, 8, 20));

            JLabel title = new JLabel("STUDENT ATTENDANCE CHECK-IN ACTIVE", SwingConstants.CENTER);
            title.setFont(CampusTheme.FONT_TITLE);
            title.setForeground(CampusTheme.PRIMARY);
            north.add(title, BorderLayout.NORTH);

            // Large 6-digit verification code display
            codeLbl = new JLabel(session.getSessionCode(), SwingConstants.CENTER);
            codeLbl.setFont(new Font("Consolas", Font.BOLD, 54));
            codeLbl.setForeground(CampusTheme.PRIMARY);
            north.add(codeLbl, BorderLayout.CENTER);

            JPanel northSub = new JPanel(new GridLayout(2, 1, 4, 4));
            northSub.setOpaque(false);

            countdownLabel = new JLabel("Calculating remaining time...", SwingConstants.CENTER);
            countdownLabel.setFont(CampusTheme.FONT_BOLD);
            countdownLabel.setForeground(CampusTheme.PRIMARY);
            northSub.add(countdownLabel);

            countsLabel = new JLabel("Enrolled: 0   |   Checked In: 0   |   Pending: 0", SwingConstants.CENTER);
            countsLabel.setFont(CampusTheme.FONT_SMALL_BOLD);
            countsLabel.setForeground(CampusTheme.TEXT_SECONDARY);
            northSub.add(countsLabel);

            north.add(northSub, BorderLayout.SOUTH);
            add(north, BorderLayout.NORTH);

            statusLabel = new JLabel("Session is OPEN for student submissions.");
            statusLabel.setFont(CampusTheme.FONT_SMALL);

            Instant expiry = Instant.parse(session.getExpiresAt());
            countdownTimer = new javax.swing.Timer(1000, null);
            countdownTimer.addActionListener(e -> {
                Instant now = Instant.now();
                if (now.isAfter(expiry)) {
                    countdownLabel.setText("SESSION EXPIRED — Check-in closed");
                    countdownLabel.setForeground(SwingUtils.DANGER_COLOR);
                    statusLabel.setText("Session has expired. Review and finalize attendance to record results.");
                } else {
                    long sec = Duration.between(now, expiry).getSeconds();
                    long m = sec / 60;
                    long s = sec % 60;
                    countdownLabel.setText(String.format("Time Remaining: %02d:%02d", m, s));
                }
            });

            String[] cols = {"Student ID", "Status", "Check-In Time"};
            subModel = new DefaultTableModel(cols, 0);
            JTable subTable = SwingUtils.createStandardTable(subModel);
            JScrollPane sp = new JScrollPane(subTable);
            sp.setBorder(BorderFactory.createTitledBorder("Checked-In Students (Live)"));
            add(sp, BorderLayout.CENTER);

            JPanel south = new JPanel(new BorderLayout(8, 8));
            south.setOpaque(false);
            south.setBorder(BorderFactory.createEmptyBorder(8, 20, 16, 20));

            south.add(statusLabel, BorderLayout.NORTH);

            JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            btnBar.setOpaque(false);

            JButton copyCodeBtn = SwingUtils.createSecondaryButton("Copy Code");
            copyCodeBtn.addActionListener(e -> {
                StringSelection selCode = new StringSelection(session.getSessionCode());
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selCode, null);
                statusLabel.setText("Session code " + session.getSessionCode() + " copied to clipboard.");
                statusLabel.setForeground(SwingUtils.SUCCESS_COLOR);
            });
            btnBar.add(copyCodeBtn);

            JButton regenBtn = SwingUtils.createSecondaryButton("Regenerate Code");
            regenBtn.setIcon(CampusIcons.get(CampusIcons.IconType.REFRESH, 12, CampusTheme.TEXT_SECONDARY));
            regenBtn.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Regenerating verification code will invalidate code " + session.getSessionCode() + " immediately.\nProceed?",
                        "Regenerate Code", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    try {
                        String newCode = sessionService.regenerateCode(currentUser, session.getId());
                        session.setSessionCode(newCode);
                        codeLbl.setText(newCode);
                        statusLabel.setText("Verification code regenerated: " + newCode + " (Previous code invalidated).");
                        statusLabel.setForeground(CampusTheme.PRIMARY);
                    } catch (Exception ex) {
                        SwingUtils.showWarning(this, "Cannot regenerate code: " + ex.getMessage());
                    }
                }
            });
            btnBar.add(regenBtn);

            JButton closeBtn = SwingUtils.createSecondaryButton("Close Check-In");
            closeBtn.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Close this check-in session now? Students will no longer be able to submit.",
                        "Close Session", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    try {
                        sessionService.closeSession(currentUser, session.getId());
                        statusLabel.setText("Session is CLOSED for new student check-ins. Ready for review and finalization.");
                        statusLabel.setForeground(SwingUtils.DANGER_COLOR);
                    } catch (Exception ex) {
                        SwingUtils.showWarning(this, ex.getMessage());
                    }
                }
            });
            btnBar.add(closeBtn);

            JButton refreshBtn = SwingUtils.createSecondaryButton("Refresh");
            refreshBtn.addActionListener(e -> reloadSubmissions());
            btnBar.add(refreshBtn);

            JButton reviewFinalizeBtn = SwingUtils.createPrimaryButton("Review & Finalize");
            reviewFinalizeBtn.addActionListener(e -> {
                Window owner = SwingUtilities.getWindowAncestor(this);
                ReviewAttendanceDialog reviewDlg = new ReviewAttendanceDialog(owner, session, sessionService, currentUser, () -> {
                    countdownTimer.stop();
                    if (onFinalize != null) onFinalize.run();
                    dispose();
                });
                reviewDlg.setVisible(true);
            });
            btnBar.add(reviewFinalizeBtn);

            south.add(btnBar, BorderLayout.SOUTH);
            add(south, BorderLayout.SOUTH);

            countdownTimer.start();

            reloadSubmissions();
        }

        private void reloadSubmissions() {
            List<AttendanceSubmission> subs = sessionService.getSessionSubmissions(currentUser, session.getId());
            subModel.setRowCount(0);
            for (AttendanceSubmission s : subs) {
                subModel.addRow(new Object[]{s.getStudentId(), s.getStatus(), s.getSubmissionTime()});
            }
            int checkedIn = subs.size();
            countsLabel.setText(String.format("Checked In: %d Students", checkedIn));
        }
    }

    private void loadCourseRoster() {
        CourseItem sel = (CourseItem) courseCombo.getSelectedItem();
        String year = academicYearField.getText().trim();
        if (sel == null) {
            SwingUtils.showWarning(this, "Please select a course.");
            return;
        }

        loadRosterButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> {
                    List<Enrollment> enrollments = enrollmentService.getEnrollmentsByCourseAndYear(currentUser, sel.id, year);
                    List<Object[]> rows = new ArrayList<>();
                    enrollmentToStudentIdMap.clear();
                    for (Enrollment enr : enrollments) {
                        if ("ACTIVE".equalsIgnoreCase(enr.getStatus())) {
                            Student s = studentDAO.findById(enr.getStudentId());
                            enrollmentToStudentIdMap.put(enr.getId(), enr.getStudentId());
                            rows.add(new Object[]{
                                    enr.getId(),
                                    s != null ? s.getStudentId() : "—",
                                    s != null ? s.getFullName() : "Student #" + enr.getStudentId(),
                                    "PRESENT",
                                    ""
                            });
                        }
                    }
                    return rows;
                },
                rows -> {
                    loadRosterButton.setEnabled(true);
                    rosterModel.setRowCount(0);
                    for (Object[] row : rows) {
                        rosterModel.addRow(row);
                    }
                    submitBatchButton.setEnabled(rosterModel.getRowCount() > 0);
                    updateRosterSummary();
                    if (rosterModel.getRowCount() == 0) {
                        SwingUtils.showInfo(this, "No active enrolled students found for course in " + year);
                    }
                },
                error -> loadRosterButton.setEnabled(true)
        );
    }

    private void submitBatchAttendance() {
        CourseItem sel = (CourseItem) courseCombo.getSelectedItem();
        String year = academicYearField.getText().trim();
        String dateStr = dateField.getText().trim();

        if (sel == null || rosterModel.getRowCount() == 0) return;

        List<AttendanceEntry> entries = new ArrayList<>();
        for (int i = 0; i < rosterModel.getRowCount(); i++) {
            int enrId = (Integer) rosterModel.getValueAt(i, 0);
            String status = (String) rosterModel.getValueAt(i, 3);
            String remarks = (String) rosterModel.getValueAt(i, 4);
            entries.add(new AttendanceEntry(enrId, status, remarks != null ? remarks : ""));
        }

        submitBatchButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> {
                    attendanceService.markAttendanceBatch(currentUser, sel.id, year, dateStr, entries, currentSessionCode);
                    return entries.size();
                },
                count -> {
                    submitBatchButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Batch attendance successfully saved for " + count + " students (Session Code: " + currentSessionCode + ").");
                },
                error -> submitBatchButton.setEnabled(true)
        );
    }

    private void loadCourseAttendanceHistory() {
        CourseItem sel = (CourseItem) courseCombo.getSelectedItem();
        String dateStr = dateField.getText().trim();
        if (sel == null) return;

        SwingUtils.executeAsync(
                this,
                () -> attendanceService.getAttendanceByCourseAndDate(currentUser, sel.id, dateStr),
                records -> {
                    historyModel.setRowCount(0);
                    for (Attendance a : records) {
                        historyModel.addRow(new Object[]{
                                a.getId(),
                                a.getEnrollmentId(),
                                a.getAttendanceDate(),
                                a.getStatus(),
                                a.getRemarks() != null ? a.getRemarks() : ""
                        });
                    }
                    if (historySummaryLabel != null) {
                        if (records.isEmpty()) {
                            historySummaryLabel.setText("No records found for selected date.");
                            historySummaryLabel.setForeground(SwingUtils.MUTED_COLOR);
                        } else {
                            int total = records.size();
                            int present = (int) records.stream().filter(a -> "PRESENT".equalsIgnoreCase(a.getStatus())).count();
                            int late = (int) records.stream().filter(a -> "LATE".equalsIgnoreCase(a.getStatus())).count();
                            int absent = (int) records.stream().filter(a -> "ABSENT".equalsIgnoreCase(a.getStatus())).count();
                            double rate = AttendanceService.calculatePercentage(records);
                            historySummaryLabel.setText(String.format(
                                    "Total: %d | Present: %d | Late: %d | Absent: %d | Rate: %.1f%%",
                                    total, present, late, absent, rate
                            ));
                            historySummaryLabel.setForeground(rate >= 75.0 ? SwingUtils.SUCCESS_COLOR : SwingUtils.DANGER_COLOR);
                        }
                    }
                }
        );
    }

    public static class CourseItem {
        public final int id;
        public final String name;
        public CourseItem(int id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }
}
