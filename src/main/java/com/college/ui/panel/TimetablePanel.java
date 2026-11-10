package com.college.ui.panel;

import com.college.config.AppConfig;
import com.college.dao.CourseAssignmentDAO;
import com.college.dao.CourseDAO;
import com.college.dao.DepartmentDAO;
import com.college.dao.FacultyDAO;
import com.college.model.Course;
import com.college.model.CourseAssignment;
import com.college.model.Department;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.TimetableEntry;
import com.college.model.User;
import com.college.service.TimetableService;
import com.college.ui.components.CampusIcons;
import com.college.ui.components.PrimaryButton;
import com.college.ui.components.SecondaryButton;
import com.college.ui.components.StatusBadge;
import com.college.ui.theme.CampusTheme;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Institutional Academic Timetable & Scheduling Panel.
 *
 * <p>Supports comprehensive administrative timetable management with strict conflict checks
 * (faculty overlap, classroom occupancy, student cohort conflicts), weekly grid inspection,
 * and role-tailored schedules for faculty instructors and enrolled students.</p>
 */
public class TimetablePanel extends JPanel {

    private final User currentUser;
    private final TimetableService timetableService;
    private final CourseDAO courseDAO;
    private final FacultyDAO facultyDAO;
    private final DepartmentDAO departmentDAO;
    private final CourseAssignmentDAO assignmentDAO;

    // Admin UI elements
    private JTable timetableTable;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> sorter;

    private JComboBox<ComboItem> deptFilterCombo;
    private JComboBox<String> semFilterCombo;
    private JComboBox<String> dayFilterCombo;
    private JTextField academicYearField;

    private JComboBox<ComboItem> courseFormCombo;
    private JComboBox<ComboItem> facultyFormCombo;
    private JComboBox<ComboItem> deptFormCombo;
    private JComboBox<String> semFormCombo;
    private JComboBox<String> dayFormCombo;
    private JTextField startTimeField;
    private JTextField endTimeField;
    private JTextField classroomField;
    private JTextField yearFormField;

    private JButton createButton;
    private JButton updateButton;
    private JButton deleteButton;
    private JButton clearButton;
    private JButton refreshButton;
    private JButton exportButton;

    private Integer selectedTimetableId = null;
    private List<TimetableEntry> currentEntries = new ArrayList<>();

    // Weekly Grid Panel
    private JPanel gridCardPanel;

    public TimetablePanel(User currentUser) {
        this(currentUser, new TimetableService(), new CourseDAO(), new FacultyDAO(),
                new DepartmentDAO(), new CourseAssignmentDAO());
    }

    public TimetablePanel(User currentUser, TimetableService timetableService,
                          CourseDAO courseDAO, FacultyDAO facultyDAO,
                          DepartmentDAO departmentDAO, CourseAssignmentDAO assignmentDAO) {
        this.currentUser = currentUser;
        this.timetableService = timetableService;
        this.courseDAO = courseDAO;
        this.facultyDAO = facultyDAO;
        this.departmentDAO = departmentDAO;
        this.assignmentDAO = assignmentDAO;

        initUI();
        loadDropdownData();
        loadTimetables();
    }

    private void initUI() {
        setLayout(new BorderLayout(16, 16));
        setBackground(CampusTheme.BG_MAIN);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        // Top Header
        JPanel headerPanel = new JPanel(new BorderLayout(16, 8));
        headerPanel.setOpaque(false);

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 2, 2));
        titleBlock.setOpaque(false);

        String mainTitle = (currentUser.getRole() == Role.ADMIN)
                ? "Academic Timetable & Schedule Governance"
                : (currentUser.getRole() == Role.FACULTY ? "My Teaching Schedule & Lecture Timetable" : "My Weekly Class Timetable");
        JLabel titleLabel = new JLabel(mainTitle);
        titleLabel.setFont(CampusTheme.FONT_TITLE);
        titleLabel.setForeground(CampusTheme.NAVY);

        String subtitle = (currentUser.getRole() == Role.ADMIN)
                ? "Manage class schedules, eliminate room/faculty conflicts, and publish cohort timetables."
                : "Lecture slots, assigned classrooms, and weekly academic timetable.";
        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setFont(CampusTheme.FONT_SMALL);
        subtitleLabel.setForeground(CampusTheme.TEXT_MUTED);

        titleBlock.add(titleLabel);
        titleBlock.add(subtitleLabel);
        headerPanel.add(titleBlock, BorderLayout.WEST);

        // Header controls (Refresh and Export to Clipboard)
        JPanel topActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        topActions.setOpaque(false);

        exportButton = new SecondaryButton("Copy Schedule");
        exportButton.setIcon(CampusIcons.get(CampusIcons.IconType.REPORTS, 13, CampusTheme.TEXT_SECONDARY));
        exportButton.setIconTextGap(6);
        exportButton.addActionListener(e -> copyScheduleToClipboard());
        topActions.add(exportButton);

        refreshButton = new SecondaryButton("Refresh");
        refreshButton.setIcon(CampusIcons.get(CampusIcons.IconType.REFRESH, 13, CampusTheme.TEXT_SECONDARY));
        refreshButton.setIconTextGap(6);
        refreshButton.addActionListener(e -> loadTimetables());
        topActions.add(refreshButton);

        headerPanel.add(topActions, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Center Content Tabs (Table View & Weekly Visual Grid)
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(CampusTheme.FONT_BOLD);

        // Tab 1: Table List View
        JPanel tablePanel = new JPanel(new BorderLayout(12, 12));
        tablePanel.setBackground(CampusTheme.BG_MAIN);

        // Filter bar for Table
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        filterBar.setOpaque(false);

        filterBar.add(new JLabel("Dept:"));
        deptFilterCombo = new JComboBox<>();
        deptFilterCombo.addItem(new ComboItem(0, "All Departments"));
        deptFilterCombo.addActionListener(e -> applyFilters());
        filterBar.add(deptFilterCombo);

        filterBar.add(new JLabel("Sem:"));
        semFilterCombo = new JComboBox<>(new String[]{"All", "1", "2", "3", "4", "5", "6", "7", "8"});
        semFilterCombo.addActionListener(e -> applyFilters());
        filterBar.add(semFilterCombo);

        filterBar.add(new JLabel("Day:"));
        dayFilterCombo = new JComboBox<>(new String[]{"All", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"});
        dayFilterCombo.addActionListener(e -> applyFilters());
        filterBar.add(dayFilterCombo);

        filterBar.add(new JLabel("Session:"));
        academicYearField = new JTextField(AppConfig.DEFAULT_ACADEMIC_YEAR, 8);
        academicYearField.addActionListener(e -> loadTimetables());
        filterBar.add(academicYearField);

        tablePanel.add(filterBar, BorderLayout.NORTH);

        String[] cols = {"ID", "Course Code", "Course Title", "Faculty Instructor", "Department", "Sem", "Day", "Time Slot", "Classroom"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        timetableTable = SwingUtils.createStandardTable(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        timetableTable.setRowSorter(sorter);

        // Renderer for Day badge
        timetableTable.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                if (value != null) {
                    return StatusBadge.of(value.toString());
                }
                return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            }
        });

        timetableTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && timetableTable.getSelectedRow() != -1) {
                populateFormFromSelection();
            }
        });

        tablePanel.add(new JScrollPane(timetableTable), BorderLayout.CENTER);
        tabbedPane.addTab("Schedule List", tablePanel);

        // Tab 2: Weekly Schedule Visual Grid
        gridCardPanel = new JPanel(new GridLayout(1, 6, 8, 8));
        gridCardPanel.setBackground(CampusTheme.BG_MAIN);
        gridCardPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JScrollPane gridScroll = new JScrollPane(gridCardPanel);
        gridScroll.setBorder(BorderFactory.createEmptyBorder());
        tabbedPane.addTab("Weekly Schedule Grid", gridScroll);

        add(tabbedPane, BorderLayout.CENTER);

        // Bottom: Administrative CRUD Form (ADMIN only)
        if (currentUser.getRole() == Role.ADMIN) {
            JPanel southFormCard = createAdminFormCard();
            add(southFormCard, BorderLayout.SOUTH);
        }
    }

    private JPanel createAdminFormCard() {
        JPanel formCard = SwingUtils.createCardPanel("Timetable Scheduling & Slot Allocation");
        JPanel formGrid = new JPanel(new GridLayout(2, 4, 10, 8));
        formGrid.setOpaque(false);

        courseFormCombo = new JComboBox<>();
        facultyFormCombo = new JComboBox<>();
        deptFormCombo = new JComboBox<>();
        semFormCombo = new JComboBox<>(new String[]{"1", "2", "3", "4", "5", "6", "7", "8"});
        dayFormCombo = new JComboBox<>(new String[]{"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"});
        startTimeField = new JTextField("09:00");
        endTimeField = new JTextField("10:30");
        classroomField = new JTextField("LH-101");
        yearFormField = new JTextField(AppConfig.DEFAULT_ACADEMIC_YEAR);

        formGrid.add(createFieldBox("Course:", courseFormCombo));
        formGrid.add(createFieldBox("Faculty Instructor:", facultyFormCombo));
        formGrid.add(createFieldBox("Department:", deptFormCombo));
        formGrid.add(createFieldBox("Semester:", semFormCombo));

        formGrid.add(createFieldBox("Day of Week:", dayFormCombo));
        formGrid.add(createFieldBox("Start Time (HH:mm):", startTimeField));
        formGrid.add(createFieldBox("End Time (HH:mm):", endTimeField));
        formGrid.add(createFieldBox("Classroom / Room:", classroomField));

        formCard.add(formGrid, BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        btnRow.setOpaque(false);

        clearButton = SwingUtils.createSecondaryButton("Clear Form");
        clearButton.addActionListener(e -> clearForm());
        btnRow.add(clearButton);

        deleteButton = SwingUtils.createSecondaryButton("Delete Slot");
        deleteButton.setEnabled(false);
        deleteButton.addActionListener(e -> deleteSelectedSlot());
        btnRow.add(deleteButton);

        updateButton = SwingUtils.createSecondaryButton("Update Slot");
        updateButton.setEnabled(false);
        updateButton.addActionListener(e -> updateSelectedSlot());
        btnRow.add(updateButton);

        createButton = SwingUtils.createPrimaryButton("Create Timetable Slot");
        createButton.addActionListener(e -> createSlot());
        btnRow.add(createButton);

        formCard.add(btnRow, BorderLayout.SOUTH);
        return formCard;
    }

    private JPanel createFieldBox(String labelText, JComponent field) {
        JPanel box = new JPanel(new BorderLayout(0, 4));
        box.setOpaque(false);
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(CampusTheme.FONT_BOLD);
        lbl.setForeground(CampusTheme.TEXT_SECONDARY);
        box.add(lbl, BorderLayout.NORTH);
        box.add(field, BorderLayout.CENTER);
        return box;
    }

    private void loadDropdownData() {
        SwingUtils.executeAsync(
                this,
                () -> {
                    List<Department> depts = departmentDAO.findAll();
                    List<Course> courses = courseDAO.findAll();
                    List<Faculty> facultyList = facultyDAO.findAll();
                    return new Object[]{depts, courses, facultyList};
                },
                result -> {
                    List<Department> depts = (List<Department>) result[0];
                    List<Course> courses = (List<Course>) result[1];
                    List<Faculty> facultyList = (List<Faculty>) result[2];

                    if (deptFilterCombo != null) {
                        for (Department d : depts) {
                            deptFilterCombo.addItem(new ComboItem(d.getId(), d.getCode() + " - " + d.getName()));
                        }
                    }

                    if (deptFormCombo != null) {
                        deptFormCombo.removeAllItems();
                        for (Department d : depts) {
                            deptFormCombo.addItem(new ComboItem(d.getId(), d.getCode() + " - " + d.getName()));
                        }
                    }

                    if (courseFormCombo != null) {
                        courseFormCombo.removeAllItems();
                        for (Course c : courses) {
                            courseFormCombo.addItem(new ComboItem(c.getId(), c.getCourseCode() + " - " + c.getName()));
                        }
                    }

                    if (facultyFormCombo != null) {
                        facultyFormCombo.removeAllItems();
                        for (Faculty f : facultyList) {
                            facultyFormCombo.addItem(new ComboItem(f.getId(), f.getFullName() + " (" + f.getFacultyId() + ")"));
                        }
                    }
                },
                error -> SwingUtils.showError(this, "Failed to load scheduling reference data: " + error.getMessage())
        );
    }

    private void loadTimetables() {
        if (refreshButton != null) refreshButton.setEnabled(false);
        String year = (academicYearField != null && !academicYearField.getText().isBlank())
                ? academicYearField.getText().trim()
                : AppConfig.DEFAULT_ACADEMIC_YEAR;

        SwingUtils.executeAsync(
                this,
                () -> {
                    if (currentUser.getRole() == Role.ADMIN) {
                        return timetableService.getAllTimetables(currentUser, year);
                    } else if (currentUser.getRole() == Role.FACULTY) {
                        Faculty f = facultyDAO.findByUserId(currentUser.getId());
                        return f != null ? timetableService.getTimetablesByFaculty(currentUser, f.getId(), year) : new ArrayList<TimetableEntry>();
                    } else {
                        return timetableService.getTimetablesForStudent(currentUser, year);
                    }
                },
                entries -> {
                    if (refreshButton != null) refreshButton.setEnabled(true);
                    this.currentEntries = entries;
                    applyFilters();
                    renderWeeklyGrid(entries);
                },
                error -> {
                    if (refreshButton != null) refreshButton.setEnabled(true);
                    SwingUtils.showError(this, "Failed to load timetables: " + error.getMessage());
                }
        );
    }

    private void applyFilters() {
        if (currentEntries == null) return;

        ComboItem selectedDept = (deptFilterCombo != null) ? (ComboItem) deptFilterCombo.getSelectedItem() : null;
        String selectedSem = (semFilterCombo != null) ? (String) semFilterCombo.getSelectedItem() : "All";
        String selectedDay = (dayFilterCombo != null) ? (String) dayFilterCombo.getSelectedItem() : "All";

        List<TimetableEntry> filtered = currentEntries.stream().filter(e -> {
            if (selectedDept != null && selectedDept.id > 0 && e.getDepartmentId() != selectedDept.id) {
                return false;
            }
            if (selectedSem != null && !"All".equalsIgnoreCase(selectedSem) && !selectedSem.equalsIgnoreCase(e.getSemester())) {
                return false;
            }
            if (selectedDay != null && !"All".equalsIgnoreCase(selectedDay) && !selectedDay.equalsIgnoreCase(e.getDayOfWeek())) {
                return false;
            }
            return true;
        }).collect(Collectors.toList());

        tableModel.setRowCount(0);
        for (TimetableEntry e : filtered) {
            tableModel.addRow(new Object[]{
                    e.getId(),
                    e.getCourseCode(),
                    e.getCourseName(),
                    e.getFacultyName(),
                    e.getDepartmentCode(),
                    e.getSemester(),
                    e.getDayOfWeek(),
                    e.getTimeSlotDisplay(),
                    e.getClassroom()
            });
        }
    }

    private void renderWeeklyGrid(List<TimetableEntry> entries) {
        gridCardPanel.removeAll();
        String[] days = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"};
        String today = LocalDate.now().getDayOfWeek().name();

        for (String day : days) {
            JPanel dayCol = new JPanel();
            dayCol.setLayout(new BoxLayout(dayCol, BoxLayout.Y_AXIS));
            boolean isToday = day.equalsIgnoreCase(today);
            dayCol.setBackground(isToday ? new Color(254, 249, 235) : CampusTheme.BG_CARD);
            dayCol.setBorder(new CompoundBorder(
                    new LineBorder(isToday ? CampusTheme.TERRACOTTA : CampusTheme.BORDER, isToday ? 2 : 1, true),
                    new EmptyBorder(8, 8, 8, 8)
            ));

            JLabel dayHeader = new JLabel(day + (isToday ? " (Today)" : ""));
            dayHeader.setFont(CampusTheme.FONT_BOLD);
            dayHeader.setForeground(isToday ? CampusTheme.TERRACOTTA : CampusTheme.PRIMARY);
            dayHeader.setAlignmentX(Component.CENTER_ALIGNMENT);
            dayCol.add(dayHeader);
            dayCol.add(Box.createVerticalStrut(8));

            List<TimetableEntry> dayList = entries.stream()
                    .filter(e -> day.equalsIgnoreCase(e.getDayOfWeek()))
                    .sorted((a, b) -> a.getStartTime().compareTo(b.getStartTime()))
                    .collect(Collectors.toList());

            if (dayList.isEmpty()) {
                JLabel emptyLbl = new JLabel("No Classes", SwingConstants.CENTER);
                emptyLbl.setFont(CampusTheme.FONT_MICRO);
                emptyLbl.setForeground(CampusTheme.TEXT_MUTED);
                emptyLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
                dayCol.add(Box.createVerticalGlue());
                dayCol.add(emptyLbl);
                dayCol.add(Box.createVerticalGlue());
            } else {
                for (TimetableEntry te : dayList) {
                    JPanel slotCard = new JPanel(new BorderLayout(4, 2));
                    slotCard.setBackground(Color.WHITE);
                    slotCard.setBorder(new CompoundBorder(
                            new LineBorder(CampusTheme.BORDER, 1, true),
                            new EmptyBorder(6, 6, 6, 6)
                    ));
                    slotCard.setMaximumSize(new Dimension(Short.MAX_VALUE, 70));

                    JLabel timeLbl = new JLabel(te.getTimeSlotDisplay());
                    timeLbl.setFont(CampusTheme.FONT_MICRO);
                    timeLbl.setForeground(CampusTheme.PRIMARY);

                    JLabel courseLbl = new JLabel(te.getCourseCode() + ": " + te.getCourseName());
                    courseLbl.setFont(CampusTheme.FONT_SMALL_BOLD);

                    JLabel infoLbl = new JLabel("Room " + te.getClassroom() + " • " + te.getFacultyName());
                    infoLbl.setFont(CampusTheme.FONT_MICRO);
                    infoLbl.setForeground(CampusTheme.TEXT_SECONDARY);

                    slotCard.add(timeLbl, BorderLayout.NORTH);
                    slotCard.add(courseLbl, BorderLayout.CENTER);
                    slotCard.add(infoLbl, BorderLayout.SOUTH);

                    dayCol.add(slotCard);
                    dayCol.add(Box.createVerticalStrut(6));
                }
                dayCol.add(Box.createVerticalGlue());
            }

            gridCardPanel.add(dayCol);
        }

        gridCardPanel.revalidate();
        gridCardPanel.repaint();
    }

    private void populateFormFromSelection() {
        int viewRow = timetableTable.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = timetableTable.convertRowIndexToModel(viewRow);
        int pk = (Integer) tableModel.getValueAt(modelRow, 0);

        TimetableEntry e = currentEntries.stream().filter(it -> it.getId() == pk).findFirst().orElse(null);
        if (e == null) return;

        selectedTimetableId = e.getId();
        selectComboById(courseFormCombo, e.getCourseId());
        selectComboById(facultyFormCombo, e.getFacultyId());
        selectComboById(deptFormCombo, e.getDepartmentId());

        if (semFormCombo != null) semFormCombo.setSelectedItem(e.getSemester());
        if (dayFormCombo != null) dayFormCombo.setSelectedItem(e.getDayOfWeek());
        if (startTimeField != null) startTimeField.setText(e.getStartTime());
        if (endTimeField != null) endTimeField.setText(e.getEndTime());
        if (classroomField != null) classroomField.setText(e.getClassroom());

        if (updateButton != null) updateButton.setEnabled(true);
        if (deleteButton != null) deleteButton.setEnabled(true);
    }

    private void selectComboById(JComboBox<ComboItem> combo, int id) {
        if (combo == null) return;
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).id == id) {
                combo.setSelectedIndex(i);
                break;
            }
        }
    }

    private void clearForm() {
        selectedTimetableId = null;
        if (startTimeField != null) startTimeField.setText("09:00");
        if (endTimeField != null) endTimeField.setText("10:30");
        if (classroomField != null) classroomField.setText("LH-101");
        if (updateButton != null) updateButton.setEnabled(false);
        if (deleteButton != null) deleteButton.setEnabled(false);
        if (timetableTable != null) timetableTable.clearSelection();
    }

    private void createSlot() {
        TimetableEntry e = buildFromForm();
        if (e == null) return;

        createButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> timetableService.createTimetableEntry(currentUser, e),
                created -> {
                    createButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Timetable slot created successfully!");
                    clearForm();
                    loadTimetables();
                },
                error -> {
                    createButton.setEnabled(true);
                    SwingUtils.showError(this, "Scheduling Conflict: " + error.getMessage());
                }
        );
    }

    private void updateSelectedSlot() {
        if (selectedTimetableId == null) return;
        TimetableEntry e = buildFromForm();
        if (e == null) return;
        e.setId(selectedTimetableId);

        updateButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> timetableService.updateTimetableEntry(currentUser, e),
                updated -> {
                    updateButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Timetable slot updated successfully!");
                    clearForm();
                    loadTimetables();
                },
                error -> {
                    updateButton.setEnabled(true);
                    SwingUtils.showError(this, "Scheduling Conflict: " + error.getMessage());
                }
        );
    }

    private void deleteSelectedSlot() {
        if (selectedTimetableId == null) return;
        boolean confirm = SwingUtils.showConfirm(this, "Delete this timetable slot?", "Confirm Deletion");
        if (!confirm) return;

        deleteButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> timetableService.deleteTimetableEntry(currentUser, selectedTimetableId),
                deleted -> {
                    deleteButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Timetable slot deleted.");
                    clearForm();
                    loadTimetables();
                },
                error -> {
                    deleteButton.setEnabled(true);
                    SwingUtils.showError(this, "Failed to delete slot: " + error.getMessage());
                }
        );
    }

    private TimetableEntry buildFromForm() {
        ComboItem course = (ComboItem) courseFormCombo.getSelectedItem();
        ComboItem faculty = (ComboItem) facultyFormCombo.getSelectedItem();
        ComboItem dept = (ComboItem) deptFormCombo.getSelectedItem();
        String sem = (String) semFormCombo.getSelectedItem();
        String day = (String) dayFormCombo.getSelectedItem();
        String start = startTimeField.getText().trim();
        String end = endTimeField.getText().trim();
        String room = classroomField.getText().trim();
        String year = (yearFormField != null && !yearFormField.getText().isBlank()) ? yearFormField.getText().trim() : AppConfig.DEFAULT_ACADEMIC_YEAR;

        if (course == null || course.id <= 0) {
            SwingUtils.showWarning(this, "Please select a valid course.");
            return null;
        }
        if (faculty == null || faculty.id <= 0) {
            SwingUtils.showWarning(this, "Please select a faculty instructor.");
            return null;
        }
        if (dept == null || dept.id <= 0) {
            SwingUtils.showWarning(this, "Please select a department.");
            return null;
        }

        return new TimetableEntry(course.id, faculty.id, dept.id, sem, day, start, end, room, year);
    }

    private void copyScheduleToClipboard() {
        if (currentEntries == null || currentEntries.isEmpty()) {
            SwingUtils.showWarning(this, "No timetable entries available to copy.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("CAMPUS ERP ACADEMIC TIMETABLE\n");
        sb.append("========================================================================================\n");
        sb.append(String.format("%-10s | %-12s | %-8s | %-26s | %-20s | %s\n",
                "DAY", "TIME", "CODE", "COURSE NAME", "FACULTY", "ROOM"));
        sb.append("----------------------------------------------------------------------------------------\n");

        for (TimetableEntry e : currentEntries) {
            sb.append(String.format("%-10s | %-12s | %-8s | %-26s | %-20s | %s\n",
                    e.getDayOfWeek(),
                    e.getTimeSlotDisplay(),
                    e.getCourseCode(),
                    e.getCourseName(),
                    e.getFacultyName(),
                    e.getClassroom()));
        }
        sb.append("========================================================================================\n");

        StringSelection sel = new StringSelection(sb.toString());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
        SwingUtils.showInfo(this, "Timetable schedule copied to system clipboard!");
    }

    public static class ComboItem {
        public final int id;
        public final String label;
        public ComboItem(int id, String label) { this.id = id; this.label = label; }
        @Override public String toString() { return label; }
    }
}

