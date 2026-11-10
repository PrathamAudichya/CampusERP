package com.college.service;

import com.college.config.DBConnection;
import com.college.dao.AttendanceDAO;
import com.college.dao.AttendanceSessionDAO;
import com.college.dao.CourseAssignmentDAO;
import com.college.dao.CourseDAO;
import com.college.dao.EnrollmentDAO;
import com.college.dao.FacultyDAO;
import com.college.dao.StudentDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.ValidationException;
import com.college.model.AttendanceCorrection;
import com.college.model.AttendanceSession;
import com.college.model.AttendanceSubmission;
import com.college.model.Course;
import com.college.model.CourseAssignment;
import com.college.model.Enrollment;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.User;

import java.io.Serializable;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service orchestrating faculty attendance session creation, secure 6-digit numeric verification codes,
 * real-time student check-in, countdown timers, manual override reviews, and automated ledger finalization.
 */
public class AttendanceSessionService {

    private final AttendanceSessionDAO sessionDAO;
    private final AttendanceDAO attendanceDAO;
    private final EnrollmentDAO enrollmentDAO;
    private final StudentDAO studentDAO;
    private final FacultyDAO facultyDAO;
    private final CourseDAO courseDAO;
    private final CourseAssignmentDAO courseAssignmentDAO;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();
    private Clock clock = Clock.systemDefaultZone();

    public AttendanceSessionService() {
        this(new AttendanceSessionDAO(), new AttendanceDAO(), new EnrollmentDAO(),
                new StudentDAO(), new FacultyDAO(), new CourseDAO(), new CourseAssignmentDAO(), new AuditService());
    }

    public AttendanceSessionService(Clock clock) {
        this(new AttendanceSessionDAO(), new AttendanceDAO(), new EnrollmentDAO(),
                new StudentDAO(), new FacultyDAO(), new CourseDAO(), new CourseAssignmentDAO(), new AuditService(), clock);
    }

    public AttendanceSessionService(AttendanceSessionDAO sessionDAO, AttendanceDAO attendanceDAO,
                                    EnrollmentDAO enrollmentDAO, StudentDAO studentDAO,
                                    FacultyDAO facultyDAO, AuditService auditService) {
        this(sessionDAO, attendanceDAO, enrollmentDAO, studentDAO, facultyDAO,
                new CourseDAO(), new CourseAssignmentDAO(), auditService);
    }

    public AttendanceSessionService(AttendanceSessionDAO sessionDAO, AttendanceDAO attendanceDAO,
                                    EnrollmentDAO enrollmentDAO, StudentDAO studentDAO,
                                    FacultyDAO facultyDAO, CourseDAO courseDAO,
                                    CourseAssignmentDAO courseAssignmentDAO, AuditService auditService) {
        this(sessionDAO, attendanceDAO, enrollmentDAO, studentDAO, facultyDAO,
                courseDAO, courseAssignmentDAO, auditService, Clock.systemDefaultZone());
    }

    public AttendanceSessionService(AttendanceSessionDAO sessionDAO, AttendanceDAO attendanceDAO,
                                    EnrollmentDAO enrollmentDAO, StudentDAO studentDAO,
                                    FacultyDAO facultyDAO, CourseDAO courseDAO,
                                    CourseAssignmentDAO courseAssignmentDAO, AuditService auditService,
                                    Clock clock) {
        this.sessionDAO = sessionDAO;
        this.attendanceDAO = attendanceDAO;
        this.enrollmentDAO = enrollmentDAO;
        this.studentDAO = studentDAO;
        this.facultyDAO = facultyDAO;
        this.courseDAO = courseDAO;
        this.courseAssignmentDAO = courseAssignmentDAO;
        this.auditService = auditService;
        this.clock = (clock != null) ? clock : Clock.systemDefaultZone();
    }

    public void setClock(Clock clock) {
        this.clock = (clock != null) ? clock : Clock.systemDefaultZone();
    }

    public Clock getClock() {
        return clock;
    }

    /**
     * Opens an attendance check-in session with default session number "1".
     */
    public AttendanceSession openSession(User currentUser, int courseId, String academicYear, int expiryMinutes) {
        return openSession(currentUser, courseId, academicYear, expiryMinutes, "1");
    }

    /**
     * Opens an attendance check-in session with a secure 6-digit numeric verification code and countdown expiry.
     * Enforces faculty course assignment authorization, active course validation, and duplicate open session prevention.
     */
    public AttendanceSession openSession(User currentUser, int courseId, String academicYear, int expiryMinutes, String sessionNumber) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY);

        if (academicYear == null || academicYear.trim().isEmpty()) {
            throw new ValidationException("Academic year cannot be empty.");
        }

        Course course = courseDAO.findById(courseId);
        if (course == null || !course.isActive()) {
            throw new ValidationException("Course does not exist or is inactive (ID: " + courseId + ").");
        }

        int facultyId = 1;
        if (currentUser.getRole() == Role.FACULTY) {
            Faculty f = facultyDAO.findByUserId(currentUser.getId());
            if (f == null || !f.isActive()) {
                throw new ValidationException("Faculty profile not found or inactive for user ID " + currentUser.getId());
            }
            facultyId = f.getId();
            CourseAssignment ca = courseAssignmentDAO.findActiveByCourseAndFaculty(courseId, facultyId, academicYear.trim());
            if (ca == null) {
                throw new ValidationException(String.format(
                        "Faculty %s is not assigned to course %s (%s) for academic year %s.",
                        f.getFacultyId(), course.getCourseCode(), course.getName(), academicYear.trim()));
            }
        }

        if (expiryMinutes < 1 || expiryMinutes > 60) {
            expiryMinutes = 10;
        }

        String sessionDate = LocalDate.now(clock.getZone()).toString();

        // Check if an active open session already exists for this course today
        AttendanceSession existing = sessionDAO.findOpenSessionByCourseAndDate(courseId, sessionDate);
        if (existing != null) {
            try {
                Instant exp = Instant.parse(existing.getExpiresAt());
                if (!Instant.now(clock).isBefore(exp)) {
                    sessionDAO.updateStatus(existing.getId(), "CLOSED");
                } else {
                    throw new ValidationException("An active attendance session is already OPEN for this course today (Session #"
                            + existing.getId() + ", Code: " + existing.getSessionCode() + "). Please close or finalize it first.");
                }
            } catch (Exception e) {
                if (e instanceof ValidationException) throw (ValidationException) e;
            }
        }

        // Generate 6-digit numeric verification code preserving leading zeroes (000000 - 999999)
        String sessionCode = String.format("%06d", secureRandom.nextInt(1000000));
        String expiresAt = Instant.now(clock).plus(Duration.ofMinutes(expiryMinutes)).toString();
        String sessNum = (sessionNumber != null && !sessionNumber.trim().isEmpty()) ? sessionNumber.trim() : "1";

        AttendanceSession session = new AttendanceSession(courseId, facultyId, sessionDate, sessionCode,
                academicYear.trim(), expiresAt, "OPEN", sessNum);

        AttendanceSession created = sessionDAO.createSession(session);

        auditService.log(currentUser.getId(), "OPEN_ATTENDANCE_SESSION", "ATTENDANCE_SESSION", created.getId(),
                null, "Opened session code: " + sessionCode + " for Course #" + courseId + " (Expires in " + expiryMinutes + " min)");

        return created;
    }

    /**
     * Regenerates a new 6-digit verification code for an open session, invalidating previous codes.
     * Prohibited after a session is finalized or closed.
     */
    public String regenerateCode(User currentUser, int sessionId) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY);

        AttendanceSession session = sessionDAO.findById(sessionId);
        if (session == null) {
            throw new ValidationException("Attendance session not found.");
        }

        if ("FINALIZED".equalsIgnoreCase(session.getStatus())) {
            throw new ValidationException("Cannot regenerate verification code: session is already FINALIZED.");
        }
        if ("CLOSED".equalsIgnoreCase(session.getStatus())) {
            throw new ValidationException("Cannot regenerate verification code: session is CLOSED.");
        }

        try {
            Instant expiry = Instant.parse(session.getExpiresAt());
            if (!Instant.now(clock).isBefore(expiry)) {
                sessionDAO.updateStatus(sessionId, "CLOSED");
                throw new ValidationException("Cannot regenerate verification code: session has expired.");
            }
        } catch (Exception e) {
            if (e instanceof ValidationException) throw (ValidationException) e;
        }

        if (currentUser.getRole() == Role.FACULTY) {
            Faculty f = facultyDAO.findByUserId(currentUser.getId());
            if (f == null || (!currentUser.getRole().equals(Role.ADMIN) && session.getFacultyId() != f.getId())) {
                throw new AuthenticationException("Faculty can only manage attendance sessions assigned to their account.");
            }
        }

        String newCode = String.format("%06d", secureRandom.nextInt(1000000));
        sessionDAO.regenerateCode(sessionId, newCode);

        auditService.log(currentUser.getId(), "REGENERATE_SESSION_CODE", "ATTENDANCE_SESSION", sessionId,
                session.getSessionCode(), newCode);

        return newCode;
    }

    /**
     * Processes a student's self-service attendance check-in via 6-digit session code.
     * Enforces rate-limiting, expiry, enrollment, duplicate check, and identity resolution.
     */
    public AttendanceSubmission checkIn(User currentUser, String sessionCode) {
        AuthService.requireRole(currentUser, Role.STUDENT);

        if (!currentUser.isActive()) {
            throw new ValidationException("ACCOUNT INACTIVE: Your user account is inactive. Please contact administration.");
        }

        Student student = studentDAO.findByUserId(currentUser.getId());
        if (student == null || !student.isActive()) {
            throw new ValidationException("ACCOUNT INACTIVE: Student profile not found or inactive.");
        }

        if (sessionCode == null || !sessionCode.trim().matches("^[0-9]{6}$")) {
            throw new ValidationException("INVALID CODE: Must be a 6-digit numeric verification code.");
        }

        String code = sessionCode.trim();
        AttendanceSession session = sessionDAO.findOpenSessionByCode(code);
        if (session == null) {
            throw new ValidationException("INVALID CODE: No active attendance session found for verification code: " + code);
        }

        return processStudentCheckIn(currentUser, student, session, code);
    }

    /**
     * Processes student check-in targeted to a specific active session.
     */
    public AttendanceSubmission checkInToSession(User currentUser, int sessionId, String candidateCode) {
        AuthService.requireRole(currentUser, Role.STUDENT);

        if (!currentUser.isActive()) {
            throw new ValidationException("ACCOUNT INACTIVE: Your user account is inactive. Please contact administration.");
        }

        Student student = studentDAO.findByUserId(currentUser.getId());
        if (student == null || !student.isActive()) {
            throw new ValidationException("ACCOUNT INACTIVE: Student profile not found or inactive.");
        }

        AttendanceSession session = sessionDAO.findById(sessionId);
        if (session == null) {
            throw new ValidationException("Attendance session not found.");
        }

        int failed = sessionDAO.getFailedAttempts(sessionId, student.getId());
        if (failed >= 5) {
            throw new ValidationException("MAXIMUM ATTEMPTS EXCEEDED: You have reached the maximum allowed verification attempts (5) for this session. Contact your instructor.");
        }

        if (candidateCode == null || !candidateCode.trim().matches("^[0-9]{6}$")) {
            sessionDAO.recordFailedAttempt(sessionId, student.getId());
            throw new ValidationException("INVALID CODE: Must be a 6-digit numeric verification code.");
        }

        String code = candidateCode.trim();
        if (!code.equals(session.getSessionCode())) {
            sessionDAO.recordFailedAttempt(sessionId, student.getId());
            throw new ValidationException("INVALID CODE: Verification code does not match the active session.");
        }

        return processStudentCheckIn(currentUser, student, session, code);
    }

    private AttendanceSubmission processStudentCheckIn(User currentUser, Student student, AttendanceSession session, String code) {
        // Rate-limiting check
        int failed = sessionDAO.getFailedAttempts(session.getId(), student.getId());
        if (failed >= 5) {
            throw new ValidationException("MAXIMUM ATTEMPTS EXCEEDED: You have reached the maximum allowed verification attempts (5) for this session. Contact your instructor.");
        }

        // Status checks
        if ("FINALIZED".equalsIgnoreCase(session.getStatus())) {
            throw new ValidationException("SESSION CLOSED: Attendance session has already been finalized.");
        }
        if (!"OPEN".equalsIgnoreCase(session.getStatus())) {
            throw new ValidationException("SESSION CLOSED: Attendance check-in is closed for this session.");
        }

        // Expiration check against SQLite authoritative timestamp (expired when now >= expiresAt)
        try {
            Instant expiry = Instant.parse(session.getExpiresAt());
            if (!Instant.now(clock).isBefore(expiry)) {
                sessionDAO.updateStatus(session.getId(), "CLOSED");
                throw new ValidationException("SESSION EXPIRED: Attendance check-in session has expired. Please contact your instructor.");
            }
        } catch (Exception e) {
            if (e instanceof ValidationException) throw (ValidationException) e;
        }

        // Check active enrollment in the course
        Enrollment enrollment = enrollmentDAO.findByStudentCourseYear(student.getId(), session.getCourseId(), session.getAcademicYear());
        if (enrollment == null || !"ACTIVE".equalsIgnoreCase(enrollment.getStatus())) {
            throw new ValidationException("NOT ENROLLED: You are not actively enrolled in this course (" + session.getCourseId() + ").");
        }

        // Check if already checked in
        if (sessionDAO.hasStudentSubmitted(session.getId(), student.getId())) {
            throw new ValidationException("ALREADY SUBMITTED: You have already checked in for this attendance session.");
        }

        AttendanceSubmission sub = new AttendanceSubmission(session.getId(), student.getId(), enrollment.getId(), code, "ACCEPTED");
        AttendanceSubmission recorded = sessionDAO.recordSubmission(sub);
        sessionDAO.resetFailedAttempts(session.getId(), student.getId());

        auditService.log(currentUser.getId(), "STUDENT_ATTENDANCE_CHECKIN", "ATTENDANCE_SUBMISSION", recorded.getId(),
                null, "Student " + student.getStudentId() + " successfully checked into session #" + session.getId());

        return recorded;
    }

    /**
     * Closes an active attendance check-in session.
     */
    public void closeSession(User currentUser, int sessionId) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY);
        AttendanceSession session = sessionDAO.findById(sessionId);
        if (session == null) throw new ValidationException("Session not found.");
        if ("FINALIZED".equalsIgnoreCase(session.getStatus())) {
            throw new ValidationException("Session is already finalized.");
        }
        sessionDAO.updateStatus(sessionId, "CLOSED");
        auditService.log(currentUser.getId(), "CLOSE_ATTENDANCE_SESSION", "ATTENDANCE_SESSION", sessionId,
                session.getStatus(), "CLOSED");
    }

    /**
     * Finalizes attendance session without manual overrides:
     * - Accepted submissions -> PRESENT
     * - Non-submitted enrolled students -> ABSENT
     */
    public int[] finalizeSession(User currentUser, int sessionId) {
        return finalizeSession(currentUser, sessionId, null);
    }

    /**
     * Finalizes attendance session with optional manual status overrides and mandatory justification remarks.
     *
     * @return int array [presentCount, absentCount, lateCount, totalEnrolled]
     */
    public int[] finalizeSession(User currentUser, int sessionId, Map<Integer, AttendanceCorrection> corrections) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY);

        AttendanceSession session = sessionDAO.findById(sessionId);
        if (session == null) throw new ValidationException("Session not found.");

        if ("FINALIZED".equalsIgnoreCase(session.getStatus())) {
            throw new ValidationException("Session is already finalized.");
        }

        if (currentUser.getRole() == Role.FACULTY) {
            Faculty f = facultyDAO.findByUserId(currentUser.getId());
            if (f != null && session.getFacultyId() != f.getId()) {
                CourseAssignment ca = courseAssignmentDAO.findActiveByCourseAndFaculty(session.getCourseId(), f.getId(), session.getAcademicYear());
                if (ca == null) {
                    throw new AuthenticationException("Faculty can only finalize attendance sessions for their assigned courses.");
                }
            }
        }

        // Validate mandatory remarks on all overrides where status differs from default check-in / initially proposed status
        List<AttendanceSubmission> initialSubmissions = sessionDAO.findSubmissionsBySession(sessionId);
        Set<Integer> checkedInStudents = new HashSet<>();
        for (AttendanceSubmission sub : initialSubmissions) {
            if ("ACCEPTED".equalsIgnoreCase(sub.getStatus())) {
                checkedInStudents.add(sub.getStudentId());
            }
        }

        if (corrections != null) {
            for (AttendanceCorrection c : corrections.values()) {
                if (c == null) continue;
                String baseProposed = (c.getInitialStatus() != null && !c.getInitialStatus().trim().isEmpty())
                        ? c.getInitialStatus().trim().toUpperCase()
                        : (checkedInStudents.contains(c.getStudentId()) ? "PRESENT" : "ABSENT");
                String targetStatus = (c.getStatus() != null) ? c.getStatus().trim().toUpperCase() : "";
                if (!baseProposed.equalsIgnoreCase(targetStatus)) {
                    if (c.getRemarks() == null || c.getRemarks().trim().isEmpty()) {
                        throw new ValidationException(String.format(
                                "Mandatory remarks/reason required for manual attendance override on student ID %d (Transition: %s -> %s)",
                                c.getStudentId(), baseProposed, targetStatus));
                    }
                }
            }
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                List<Enrollment> enrollments = enrollmentDAO.findActiveByCourseAndYear(conn, session.getCourseId(), session.getAcademicYear());

                int presentCount = 0;
                int absentCount = 0;
                int lateCount = 0;

                for (Enrollment enr : enrollments) {
                    String finalStatus;
                    String finalRemarks;

                    AttendanceCorrection correction = (corrections != null) ? corrections.get(enr.getStudentId()) : null;

                    if (correction != null && correction.getStatus() != null) {
                        finalStatus = correction.getStatus().trim().toUpperCase();
                        finalRemarks = (correction.getRemarks() != null && !correction.getRemarks().trim().isEmpty())
                                ? correction.getRemarks().trim() : "Faculty manual entry";
                    } else if (checkedInStudents.contains(enr.getStudentId())) {
                        finalStatus = "PRESENT";
                        finalRemarks = "Self-Check-In Verified";
                    } else {
                        finalStatus = "ABSENT";
                        finalRemarks = "Auto-Marked Absent (No Check-In)";
                    }

                    if ("PRESENT".equalsIgnoreCase(finalStatus)) presentCount++;
                    else if ("LATE".equalsIgnoreCase(finalStatus)) lateCount++;
                    else absentCount++;

                    attendanceDAO.upsert(conn, enr.getId(), session.getSessionDate(), finalStatus,
                            finalRemarks, session.getSessionCode());
                }

                String nowStr = Instant.now(clock).toString();
                sessionDAO.updateFinalized(conn, sessionId, nowStr, currentUser.getId());

                auditService.log(conn, currentUser.getId(), "FINALIZE_ATTENDANCE_SESSION", "ATTENDANCE_SESSION", sessionId,
                        session.getStatus(), String.format("Finalized session #%d (Present: %d, Absent: %d, Late: %d, Total: %d)",
                                sessionId, presentCount, absentCount, lateCount, enrollments.size()));

                conn.commit();
                return new int[]{presentCount, absentCount, lateCount, enrollments.size()};
            } catch (Exception e) {
                conn.rollback();
                if (e instanceof ValidationException) throw (ValidationException) e;
                throw new ValidationException("Failed to finalize attendance session: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new ValidationException("Database connection error: " + e.getMessage());
        }
    }

    /**
     * Retrieves currently active, open sessions for courses the authenticated student is actively enrolled in.
     */
    public List<ActiveSessionInfo> getActiveSessionsForStudent(User currentUser) {
        AuthService.requireRole(currentUser, Role.STUDENT);
        Student student = studentDAO.findByUserId(currentUser.getId());
        if (student == null) return List.of();

        List<AttendanceSession> sessions = sessionDAO.findActiveSessionsForStudent(student.getId());
        List<ActiveSessionInfo> result = new ArrayList<>();

        for (AttendanceSession s : sessions) {
            // Check expiry
            try {
                Instant expiry = Instant.parse(s.getExpiresAt());
                if (!Instant.now(clock).isBefore(expiry)) {
                    sessionDAO.updateStatus(s.getId(), "CLOSED");
                    continue;
                }
                long remainingSec = Math.max(0, Duration.between(Instant.now(clock), expiry).getSeconds());
                Course c = courseDAO.findById(s.getCourseId());
                Faculty f = facultyDAO.findById(s.getFacultyId());
                boolean submitted = sessionDAO.hasStudentSubmitted(s.getId(), student.getId());

                String courseName = (c != null) ? c.getName() : "Course #" + s.getCourseId();
                String courseCode = (c != null) ? c.getCourseCode() : "—";
                String facultyName = (f != null) ? f.getFullName() : "Faculty #" + s.getFacultyId();

                result.add(new ActiveSessionInfo(s, courseName, courseCode, facultyName, remainingSec, submitted));
            } catch (Exception ignored) {}
        }
        return result;
    }

    /**
     * Assembles the review roster for an attendance session.
     */
    public List<SessionRosterEntry> getSessionRoster(User currentUser, int sessionId) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY);
        AttendanceSession session = sessionDAO.findById(sessionId);
        if (session == null) throw new ValidationException("Session not found.");

        List<Enrollment> enrollments = enrollmentDAO.findActiveByCourseAndYear(session.getCourseId(), session.getAcademicYear());
        List<AttendanceSubmission> submissions = sessionDAO.findSubmissionsBySession(sessionId);

        Map<Integer, AttendanceSubmission> subMap = new HashMap<>();
        for (AttendanceSubmission sub : submissions) {
            subMap.put(sub.getStudentId(), sub);
        }

        List<SessionRosterEntry> roster = new ArrayList<>();
        for (Enrollment enr : enrollments) {
            Student s = studentDAO.findById(enr.getStudentId());
            AttendanceSubmission sub = subMap.get(enr.getStudentId());
            boolean checkedIn = (sub != null && "ACCEPTED".equalsIgnoreCase(sub.getStatus()));
            String checkInTime = checkedIn ? sub.getSubmissionTime() : "—";
            String subStatus = checkedIn ? "CHECKED IN" : "NOT SUBMITTED";
            String proposedStatus = checkedIn ? "PRESENT" : "ABSENT";

            roster.add(new SessionRosterEntry(
                    enr.getId(),
                    enr.getStudentId(),
                    s != null ? s.getStudentId() : "—",
                    s != null ? s.getFullName() : "Student #" + enr.getStudentId(),
                    checkInTime,
                    subStatus,
                    proposedStatus,
                    proposedStatus,
                    checkedIn ? "Self-Check-In Verified" : ""
            ));
        }
        return roster;
    }

    public List<AttendanceSession> getFacultySessions(User currentUser, int facultyId) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY);
        return sessionDAO.findSessionsByFaculty(facultyId);
    }

    public List<AttendanceSession> getSessionsByCourse(User currentUser, int courseId, String academicYear) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY);
        return sessionDAO.findSessionsByCourseAndYear(courseId, academicYear);
    }

    public List<AttendanceSubmission> getSessionSubmissions(User currentUser, int sessionId) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY);
        return sessionDAO.findSubmissionsBySession(sessionId);
    }

    public AttendanceSession getSessionById(int sessionId) {
        return sessionDAO.findById(sessionId);
    }

    // --- DTO Classes ---

    public static class ActiveSessionInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private final AttendanceSession session;
        private final String courseName;
        private final String courseCode;
        private final String facultyName;
        private final long remainingSeconds;
        private final boolean alreadySubmitted;

        public ActiveSessionInfo(AttendanceSession session, String courseName, String courseCode,
                                 String facultyName, long remainingSeconds, boolean alreadySubmitted) {
            this.session = session;
            this.courseName = courseName;
            this.courseCode = courseCode;
            this.facultyName = facultyName;
            this.remainingSeconds = remainingSeconds;
            this.alreadySubmitted = alreadySubmitted;
        }

        public AttendanceSession getSession() { return session; }
        public String getCourseName() { return courseName; }
        public String getCourseCode() { return courseCode; }
        public String getFacultyName() { return facultyName; }
        public long getRemainingSeconds() { return remainingSeconds; }
        public boolean isAlreadySubmitted() { return alreadySubmitted; }
    }

    public static class SessionRosterEntry implements Serializable {
        private static final long serialVersionUID = 1L;

        private final int enrollmentId;
        private final int studentId;
        private final String studentRegId;
        private final String studentName;
        private final String checkInTime;
        private final String submissionStatus;
        private final String proposedStatus;
        private String currentStatus;
        private String remarks;

        public SessionRosterEntry(int enrollmentId, int studentId, String studentRegId, String studentName,
                                  String checkInTime, String submissionStatus, String proposedStatus,
                                  String currentStatus, String remarks) {
            this.enrollmentId = enrollmentId;
            this.studentId = studentId;
            this.studentRegId = studentRegId;
            this.studentName = studentName;
            this.checkInTime = checkInTime;
            this.submissionStatus = submissionStatus;
            this.proposedStatus = proposedStatus;
            this.currentStatus = currentStatus;
            this.remarks = remarks;
        }

        public int getEnrollmentId() { return enrollmentId; }
        public int getStudentId() { return studentId; }
        public String getStudentRegId() { return studentRegId; }
        public String getStudentName() { return studentName; }
        public String getCheckInTime() { return checkInTime; }
        public String getSubmissionStatus() { return submissionStatus; }
        public String getProposedStatus() { return proposedStatus; }
        public String getCurrentStatus() { return currentStatus; }
        public void setCurrentStatus(String currentStatus) { this.currentStatus = currentStatus; }
        public String getRemarks() { return remarks; }
        public void setRemarks(String remarks) { this.remarks = remarks; }
    }
}
