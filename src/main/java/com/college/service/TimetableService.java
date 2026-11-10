package com.college.service;

import com.college.config.AppConfig;
import com.college.config.DBConnection;
import com.college.dao.CourseAssignmentDAO;
import com.college.dao.CourseDAO;
import com.college.dao.DepartmentDAO;
import com.college.dao.FacultyDAO;
import com.college.dao.StudentDAO;
import com.college.dao.TimetableDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.ValidationException;
import com.college.model.Course;
import com.college.model.CourseAssignment;
import com.college.model.Department;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.TimetableEntry;
import com.college.model.User;
import com.college.util.ValidationUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Service managing academic timetables, schedule conflict detection,
 * faculty workload verification, and cohort overlap validation.
 */
public class TimetableService {

    private static final Set<String> VALID_DAYS = Set.of(
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
    );
    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private final TimetableDAO timetableDAO;
    private final CourseDAO courseDAO;
    private final FacultyDAO facultyDAO;
    private final DepartmentDAO departmentDAO;
    private final CourseAssignmentDAO courseAssignmentDAO;
    private final StudentDAO studentDAO;
    private final AuditService auditService;

    public TimetableService() {
        this(new TimetableDAO(), new CourseDAO(), new FacultyDAO(),
             new DepartmentDAO(), new CourseAssignmentDAO(), new StudentDAO(), new AuditService());
    }

    public TimetableService(TimetableDAO timetableDAO, CourseDAO courseDAO, FacultyDAO facultyDAO,
                            DepartmentDAO departmentDAO, CourseAssignmentDAO courseAssignmentDAO,
                            StudentDAO studentDAO, AuditService auditService) {
        this.timetableDAO = timetableDAO;
        this.courseDAO = courseDAO;
        this.facultyDAO = facultyDAO;
        this.departmentDAO = departmentDAO;
        this.courseAssignmentDAO = courseAssignmentDAO;
        this.studentDAO = studentDAO;
        this.auditService = auditService;
    }

    /**
     * Creates a new timetable entry after validating against time ordering and scheduling conflicts (ADMIN only).
     */
    public TimetableEntry createTimetableEntry(User currentUser, TimetableEntry entry) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        validateTimetableInput(entry);

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Verify entities exist and are active
                verifyReferencedEntities(conn, entry);

                // Detect conflicts (faculty, classroom, cohort)
                checkSchedulingConflicts(conn, entry, 0);

                TimetableEntry saved = timetableDAO.insert(conn, entry);
                auditService.log(conn, currentUser.getId(), "CREATE_TIMETABLE", "TIMETABLE", saved.getId(),
                        null, String.format("Scheduled %s (%s %s-%s, Room %s)",
                                saved.getCourseCode(), saved.getDayOfWeek(), saved.getStartTime(), saved.getEndTime(), saved.getClassroom()));

                conn.commit();
                return saved;
            } catch (Throwable ex) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                if (ex instanceof ValidationException) {
                    throw (ValidationException) ex;
                } else if (ex instanceof AuthenticationException) {
                    throw (AuthenticationException) ex;
                } else if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                } else if (ex instanceof Error) {
                    throw (Error) ex;
                } else {
                    throw new RuntimeException("Unexpected error during timetable creation", ex);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Updates an existing timetable entry with full conflict re-validation (ADMIN only).
     */
    public TimetableEntry updateTimetableEntry(User currentUser, TimetableEntry entry) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        if (entry == null || entry.getId() <= 0) {
            throw new ValidationException("Invalid timetable entry ID for update.");
        }
        validateTimetableInput(entry);

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                TimetableEntry existing = timetableDAO.findById(conn, entry.getId());
                if (existing == null) {
                    throw new ValidationException("Timetable entry ID " + entry.getId() + " not found.");
                }

                verifyReferencedEntities(conn, entry);
                checkSchedulingConflicts(conn, entry, entry.getId());

                boolean ok = timetableDAO.update(conn, entry);
                if (!ok) {
                    throw new ValidationException("Failed to update timetable record.");
                }

                auditService.log(conn, currentUser.getId(), "UPDATE_TIMETABLE", "TIMETABLE", entry.getId(),
                        String.format("%s %s-%s", existing.getDayOfWeek(), existing.getStartTime(), existing.getEndTime()),
                        String.format("%s %s-%s", entry.getDayOfWeek(), entry.getStartTime(), entry.getEndTime()));

                conn.commit();
                return timetableDAO.findById(conn, entry.getId());
            } catch (Throwable ex) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                if (ex instanceof ValidationException) {
                    throw (ValidationException) ex;
                } else if (ex instanceof AuthenticationException) {
                    throw (AuthenticationException) ex;
                } else if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                } else if (ex instanceof Error) {
                    throw (Error) ex;
                } else {
                    throw new RuntimeException("Unexpected error during timetable update", ex);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a timetable entry (ADMIN only).
     */
    public boolean deleteTimetableEntry(User currentUser, int id) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        TimetableEntry existing = timetableDAO.findById(id);
        if (existing == null) {
            throw new ValidationException("Timetable entry ID " + id + " not found.");
        }
        boolean deleted = timetableDAO.delete(id);
        if (deleted) {
            auditService.log(currentUser.getId(), "DELETE_TIMETABLE", "TIMETABLE", id,
                    String.format("%s (%s %s-%s)", existing.getCourseCode(), existing.getDayOfWeek(), existing.getStartTime(), existing.getEndTime()),
                    "DELETED");
        }
        return deleted;
    }

    /**
     * Retrieves all timetables for a given academic year.
     */
    public List<TimetableEntry> getAllTimetables(User currentUser, String academicYear) {
        if (currentUser == null) throw new AuthenticationException("Authentication required.");
        String year = (academicYear != null && !academicYear.isBlank()) ? academicYear.trim() : AppConfig.DEFAULT_ACADEMIC_YEAR;
        return timetableDAO.findByAcademicYear(year);
    }

    /**
     * Retrieves timetables for a specific department and semester cohort.
     */
    public List<TimetableEntry> getTimetablesByDepartmentAndSemester(User currentUser, int departmentId, String semester, String academicYear) {
        if (currentUser == null) throw new AuthenticationException("Authentication required.");
        String year = (academicYear != null && !academicYear.isBlank()) ? academicYear.trim() : AppConfig.DEFAULT_ACADEMIC_YEAR;
        return timetableDAO.findByDepartmentAndSemester(departmentId, semester, year);
    }

    /**
     * Retrieves teaching schedule for a specific faculty member.
     */
    public List<TimetableEntry> getTimetablesByFaculty(User currentUser, int facultyId, String academicYear) {
        if (currentUser == null) throw new AuthenticationException("Authentication required.");
        String year = (academicYear != null && !academicYear.isBlank()) ? academicYear.trim() : AppConfig.DEFAULT_ACADEMIC_YEAR;
        return timetableDAO.findByFaculty(facultyId, year);
    }

    /**
     * Retrieves class schedule for the authenticated student based on their enrolled cohort.
     */
    public List<TimetableEntry> getTimetablesForStudent(User currentUser, String academicYear) {
        AuthService.requireRole(currentUser, Role.STUDENT);
        Student s = studentDAO.findByUserId(currentUser.getId());
        if (s == null) {
            return new ArrayList<>();
        }
        String year = (academicYear != null && !academicYear.isBlank()) ? academicYear.trim() : AppConfig.DEFAULT_ACADEMIC_YEAR;
        return timetableDAO.findByDepartmentAndSemester(s.getDepartmentId(), s.getSemester(), year);
    }

    private void validateTimetableInput(TimetableEntry entry) {
        if (entry == null) {
            throw new ValidationException("Timetable entry cannot be null.");
        }
        if (entry.getCourseId() <= 0) {
            throw new ValidationException("A valid course selection is required.");
        }
        if (entry.getFacultyId() <= 0) {
            throw new ValidationException("A valid faculty instructor selection is required.");
        }
        if (entry.getDepartmentId() <= 0) {
            throw new ValidationException("A valid department selection is required.");
        }
        ValidationUtil.requireNonEmpty(entry.getSemester(), "Semester");
        ValidationUtil.requireNonEmpty(entry.getClassroom(), "Classroom");
        ValidationUtil.requireNonEmpty(entry.getAcademicYear(), "Academic year");

        if (entry.getDayOfWeek() == null || !VALID_DAYS.contains(entry.getDayOfWeek().trim().toUpperCase())) {
            throw new ValidationException("Invalid day of week: " + entry.getDayOfWeek() + ". Must be MONDAY through SUNDAY.");
        }
        entry.setDayOfWeek(entry.getDayOfWeek().trim().toUpperCase());

        ValidationUtil.requireNonEmpty(entry.getStartTime(), "Start time");
        ValidationUtil.requireNonEmpty(entry.getEndTime(), "End time");

        String start = entry.getStartTime().trim();
        String end = entry.getEndTime().trim();

        if (!TIME_PATTERN.matcher(start).matches()) {
            throw new ValidationException("Start time must be in 24-hour HH:mm format (e.g. 09:00).");
        }
        if (!TIME_PATTERN.matcher(end).matches()) {
            throw new ValidationException("End time must be in 24-hour HH:mm format (e.g. 10:30).");
        }
        if (start.compareTo(end) >= 0) {
            throw new ValidationException("Start time (" + start + ") must be strictly earlier than end time (" + end + ").");
        }
        entry.setStartTime(start);
        entry.setEndTime(end);
        entry.setClassroom(entry.getClassroom().trim());
        entry.setAcademicYear(entry.getAcademicYear().trim());
    }

    private void verifyReferencedEntities(Connection conn, TimetableEntry entry) {
        Course course = courseDAO.findById(conn, entry.getCourseId());
        if (course == null || !course.isActive()) {
            throw new ValidationException("Selected course is invalid or inactive.");
        }

        Faculty faculty = facultyDAO.findById(conn, entry.getFacultyId());
        if (faculty == null || !faculty.isActive()) {
            throw new ValidationException("Selected faculty instructor is invalid or inactive.");
        }

        Department department = departmentDAO.findById(conn, entry.getDepartmentId());
        if (department == null || !department.isActive()) {
            throw new ValidationException("Selected department is invalid or inactive.");
        }

        // Verify active course assignment for academic year
        CourseAssignment ca = courseAssignmentDAO.findActiveByCourseAndFaculty(
                conn, entry.getCourseId(), entry.getFacultyId(), entry.getAcademicYear());
        if (ca == null) {
            throw new ValidationException(String.format(
                    "Faculty instructor (%s) is not assigned to course %s (%s) for academic year %s. Assign the course to faculty before scheduling.",
                    faculty.getFullName(), course.getCourseCode(), course.getName(), entry.getAcademicYear()));
        }
    }

    private void checkSchedulingConflicts(Connection conn, TimetableEntry target, int excludeId) {
        List<TimetableEntry> dayEntries = timetableDAO.findByDayAndYear(conn, target.getDayOfWeek(), target.getAcademicYear());

        for (TimetableEntry existing : dayEntries) {
            if (existing.getId() == excludeId) {
                continue;
            }

            // Interval overlap check: [S1, E1) overlaps [S2, E2) <=> S1 < E2 and S2 < E1
            if (timesOverlap(target.getStartTime(), target.getEndTime(), existing.getStartTime(), existing.getEndTime())) {
                // 1. Faculty Conflict
                if (existing.getFacultyId() == target.getFacultyId()) {
                    throw new ValidationException(String.format(
                            "Faculty Conflict: Prof. %s is already scheduled to teach %s in Room %s on %s from %s to %s.",
                            existing.getFacultyName(), existing.getCourseCode(), existing.getClassroom(),
                            existing.getDayOfWeek(), existing.getStartTime(), existing.getEndTime()));
                }

                // 2. Classroom Conflict
                if (existing.getClassroom().equalsIgnoreCase(target.getClassroom())) {
                    throw new ValidationException(String.format(
                            "Classroom Conflict: Room %s is already occupied by %s on %s from %s to %s.",
                            target.getClassroom(), existing.getCourseCode(), existing.getDayOfWeek(),
                            existing.getStartTime(), existing.getEndTime()));
                }

                // 3. Cohort Overlap Conflict (Same Department and Semester)
                if (existing.getDepartmentId() == target.getDepartmentId()
                        && existing.getSemester().equalsIgnoreCase(target.getSemester())) {
                    throw new ValidationException(String.format(
                            "Student Cohort Conflict: Department %s Semester %s already has %s scheduled on %s from %s to %s.",
                            existing.getDepartmentCode(), existing.getSemester(), existing.getCourseCode(),
                            existing.getDayOfWeek(), existing.getStartTime(), existing.getEndTime()));
                }
            }
        }
    }

    public static boolean timesOverlap(String startA, String endA, String startB, String endB) {
        return startA.compareTo(endB) < 0 && startB.compareTo(endA) < 0;
    }
}

