package com.college.service;

import com.college.config.DBConnection;
import com.college.dao.AcademicProgramDAO;
import com.college.dao.DepartmentDAO;
import com.college.dao.StudentCohortDAO;
import com.college.dao.StudentDAO;
import com.college.dao.UserDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.DataAccessException;
import com.college.exception.ValidationException;
import com.college.model.AcademicProgram;
import com.college.model.Department;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.StudentCohort;
import com.college.model.User;
import com.college.util.PasswordUtil;
import com.college.util.ValidationUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Service managing student lifecycles, transactional student+user creation, and profile lookups.
 */
public class StudentService {

    private final StudentDAO studentDAO;
    private final UserDAO userDAO;
    private final DepartmentDAO departmentDAO;
    private final AcademicProgramDAO programDAO;
    private final StudentCohortDAO cohortDAO;
    private final AuditService auditService;
    private final AuthService authService;

    public StudentService() {
        this.studentDAO = new StudentDAO();
        this.userDAO = new UserDAO();
        this.departmentDAO = new DepartmentDAO();
        this.programDAO = new AcademicProgramDAO();
        this.cohortDAO = new StudentCohortDAO();
        this.auditService = new AuditService();
        this.authService = new AuthService(this.userDAO, this.auditService);
    }

    public StudentService(StudentDAO studentDAO, UserDAO userDAO,
                          DepartmentDAO departmentDAO, AuditService auditService) {
        this(studentDAO, userDAO, departmentDAO, new AcademicProgramDAO(), new StudentCohortDAO(), auditService, new AuthService(userDAO, auditService));
    }

    public StudentService(StudentDAO studentDAO, UserDAO userDAO,
                          DepartmentDAO departmentDAO, AuditService auditService,
                          AuthService authService) {
        this(studentDAO, userDAO, departmentDAO, new AcademicProgramDAO(), new StudentCohortDAO(), auditService, authService);
    }

    public StudentService(StudentDAO studentDAO, UserDAO userDAO,
                          DepartmentDAO departmentDAO, AcademicProgramDAO programDAO,
                          StudentCohortDAO cohortDAO, AuditService auditService,
                          AuthService authService) {
        this.studentDAO = studentDAO;
        this.userDAO = userDAO;
        this.departmentDAO = departmentDAO;
        this.programDAO = programDAO != null ? programDAO : new AcademicProgramDAO();
        this.cohortDAO = cohortDAO != null ? cohortDAO : new StudentCohortDAO();
        this.auditService = auditService;
        this.authService = authService;
    }

    /**
     * Value object encapsulating student creation result including one-time temporary password.
     */
    public static class StudentCreationResult {
        private final Student student;
        private final String username;
        private final String temporaryPassword;

        public StudentCreationResult(Student student, String temporaryPassword) {
            this(student, student != null ? student.getStudentId() : null, temporaryPassword);
        }

        public StudentCreationResult(Student student, String username, String temporaryPassword) {
            this.student = student;
            this.username = username;
            this.temporaryPassword = temporaryPassword;
        }

        public Student getStudent() {
            return student;
        }

        public String getUsername() {
            return username != null ? username : (student != null ? student.getStudentId() : null);
        }

        public String getTemporaryPassword() {
            return temporaryPassword;
        }
    }

    /**
     * Atomically creates a Student profile and linked User account with a cryptographically
     * secure random temporary password (ADMIN only).
     * The student ID is used as the login username, and must_change_password is set to 1.
     *
     * @param currentUser authenticated administrator
     * @param student     student profile details
     * @return StudentCreationResult containing the persisted student, username, and temporary password
     */
    public StudentCreationResult createStudentWithAutoAccount(User currentUser, Student student) {
        return createStudentWithAutoAccount(currentUser, student, student != null ? student.getStudentId() : null);
    }

    /**
     * Atomically creates a Student profile and linked User account with an auto-generated
     * institutional username in AA123456789012 format (ADMIN only).
     *
     * @param currentUser authenticated administrator
     * @param student     student profile details
     * @return StudentCreationResult containing the persisted student, institutional username, and temporary password
     */
    public StudentCreationResult createStudentWithInstitutionalAccount(User currentUser, Student student) {
        return createStudentWithAutoAccount(currentUser, student, null);
    }

    /**
     * Atomically creates a Student profile and linked User account with custom or generated username.
     *
     * @param currentUser    authenticated administrator
     * @param student        student profile details
     * @param customUsername optional custom username; if null or empty, an institutional AA999999999999 username is generated
     * @return StudentCreationResult containing the persisted student, username, and temporary password
     */
    public StudentCreationResult createStudentWithAutoAccount(User currentUser, Student student, String customUsername) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        validateStudentInput(student);

        String studentIdNumber = student.getStudentId().trim();
        String email = student.getEmail().trim().toLowerCase();
        String temporaryPassword = PasswordUtil.generateTemporaryPassword();

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Validate department within transaction
                Department dept = departmentDAO.findById(conn, student.getDepartmentId());
                if (dept == null || !dept.isActive()) {
                    throw new ValidationException("Department ID " + student.getDepartmentId() + " does not exist or is inactive.");
                }

                // Check natural identifier conflicts within transaction
                Student existingById = studentDAO.findByStudentId(conn, studentIdNumber);
                if (existingById != null) {
                    if (existingById.isActive()) {
                        throw new ValidationException("Student ID already exists: " + studentIdNumber);
                    } else {
                        throw new ValidationException("Student with ID " + studentIdNumber + " exists but is inactive. Reactivate the student instead.");
                    }
                }

                Student existingByEmail = studentDAO.findByEmail(conn, email);
                if (existingByEmail != null) {
                    if (existingByEmail.isActive()) {
                        throw new ValidationException("Student email already exists: " + email);
                    } else {
                        throw new ValidationException("Student with email " + email + " exists but is inactive. Reactivate the student instead.");
                    }
                }

                String username = (customUsername != null && !customUsername.trim().isEmpty())
                        ? customUsername.trim()
                        : com.college.util.UsernameUtil.generateUniqueUsername(conn);

                User existingUser = userDAO.findByUsername(conn, username);
                if (existingUser != null) {
                    throw new ValidationException("Username " + username + " already exists in the system.");
                }

                // Step 1: Create linked user account with generated temporary password
                String passwordHash = PasswordUtil.hashPassword(temporaryPassword);
                User user = new User(username, passwordHash, Role.STUDENT);
                user.setMustChangePassword(true);
                user.setActive(true);
                User savedUser = userDAO.insert(conn, user);

                // Step 2: Create student entity referencing saved user
                student.setUserId(savedUser.getId());
                student.setStudentId(studentIdNumber);
                student.setEmail(email);
                student.setActive(true);
                if (student.getAdmissionDate() == null || student.getAdmissionDate().trim().isEmpty()) {
                    student.setAdmissionDate(java.time.LocalDate.now().toString());
                }
                Student savedStudent = studentDAO.insert(conn, student);

                // Step 3: Transactional audit log
                auditService.log(conn, currentUser.getId(), "CREATE_STUDENT_ACCOUNT", "STUDENT", savedStudent.getId(),
                        null, "Created student " + studentIdNumber + " (" + savedStudent.getFullName() + ") with username " + username + " linked to user ID " + savedUser.getId() + " (must_change_password=1)");

                conn.commit();
                return new StudentCreationResult(savedStudent, username, temporaryPassword);
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
                } else if (ex instanceof DataAccessException) {
                    throw (DataAccessException) ex;
                } else if (ex instanceof SQLException) {
                    throw new DataAccessException("Database operation failed: " + ex.getMessage(), (SQLException) ex);
                } else if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                } else if (ex instanceof Error) {
                    throw (Error) ex;
                } else {
                    throw new RuntimeException("Unexpected error during student creation", ex);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Atomically creates a Student profile and linked User account (ADMIN only).
     *
     * @param currentUser     authenticated user (ADMIN only)
     * @param student         student entity containing profile details
     * @param initialPassword plaintext password for initial student login (if null/blank, auto-generates temporary password)
     * @return persisted Student entity with generated ID and user_id populated
     */
    public Student createStudent(User currentUser, Student student, String initialPassword) {
        if (initialPassword == null || initialPassword.trim().isEmpty()) {
            return createStudentWithAutoAccount(currentUser, student).getStudent();
        }
        AuthService.requireRole(currentUser, Role.ADMIN);
        validateStudentInput(student);
        ValidationUtil.requireNonEmpty(initialPassword, "Initial password");
        if (initialPassword.length() < 6) {
            throw new ValidationException("Initial password must be at least 6 characters long.");
        }

        String studentIdNumber = student.getStudentId().trim();
        String email = student.getEmail().trim().toLowerCase();
        String username = studentIdNumber;

        // Two-table atomic creation within single connection transaction
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Validate department within transaction
                Department dept = departmentDAO.findById(conn, student.getDepartmentId());
                if (dept == null || !dept.isActive()) {
                    throw new ValidationException("Department ID " + student.getDepartmentId() + " does not exist or is inactive.");
                }

                // Check natural identifier conflicts within transaction
                Student existingById = studentDAO.findByStudentId(conn, studentIdNumber);
                if (existingById != null) {
                    if (existingById.isActive()) {
                        throw new ValidationException("Student ID already exists: " + studentIdNumber);
                    } else {
                        throw new ValidationException("Student with ID " + studentIdNumber + " exists but is inactive. Reactivate the student instead.");
                    }
                }

                Student existingByEmail = studentDAO.findByEmail(conn, email);
                if (existingByEmail != null) {
                    if (existingByEmail.isActive()) {
                        throw new ValidationException("Student email already exists: " + email);
                    } else {
                        throw new ValidationException("Student with email " + email + " exists but is inactive. Reactivate the student instead.");
                    }
                }

                User existingUser = userDAO.findByUsername(conn, username);
                if (existingUser != null) {
                    throw new ValidationException("Username " + username + " already exists in the system.");
                }

                // Step 1: Create linked user account
                String passwordHash = PasswordUtil.hashPassword(initialPassword);
                User user = new User(username, passwordHash, Role.STUDENT);
                user.setMustChangePassword(true);
                user.setActive(true);
                User savedUser = userDAO.insert(conn, user);

                // Step 2: Create student entity referencing saved user
                student.setUserId(savedUser.getId());
                student.setStudentId(studentIdNumber);
                student.setEmail(email);
                student.setActive(true);
                if (student.getAdmissionDate() == null || student.getAdmissionDate().trim().isEmpty()) {
                    student.setAdmissionDate(java.time.LocalDate.now().toString());
                }
                Student savedStudent = studentDAO.insert(conn, student);

                // Step 3: Transactional audit log
                auditService.log(conn, currentUser.getId(), "CREATE_STUDENT", "STUDENT", savedStudent.getId(),
                        null, "Created student " + studentIdNumber + " (" + savedStudent.getFullName() + ") linked to user ID " + savedUser.getId());

                conn.commit();
                return savedStudent;
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
                } else if (ex instanceof DataAccessException) {
                    throw (DataAccessException) ex;
                } else if (ex instanceof SQLException) {
                    throw new DataAccessException("Database operation failed: " + ex.getMessage(), (SQLException) ex);
                } else if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                } else if (ex instanceof Error) {
                    throw (Error) ex;
                } else {
                    throw new RuntimeException("Unexpected error during student creation", ex);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Updates an existing student record (ADMIN or own student profile).
     */
    public Student updateStudent(User currentUser, Student student) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        if (student == null || student.getId() <= 0) {
            throw new ValidationException("Valid student record required.");
        }

        Student existing = studentDAO.findById(student.getId());
        if (existing == null) {
            throw new ValidationException("Student " + student.getId() + " not found.");
        }

        // Authorization: Admin or own profile
        if (currentUser.getRole() != Role.ADMIN && currentUser.getId() != existing.getUserId()) {
            throw new AuthenticationException("Unauthorized to modify this student profile.");
        }

        validateStudentInput(student);

        // Department check
        Department dept = departmentDAO.findById(student.getDepartmentId());
        if (dept == null || !dept.isActive()) {
            throw new ValidationException("Department ID " + student.getDepartmentId() + " does not exist or is inactive.");
        }

        // Email uniqueness check
        String email = student.getEmail().trim().toLowerCase();
        Student otherEmail = studentDAO.findByEmail(email);
        if (otherEmail != null && otherEmail.getId() != student.getId()) {
            throw new ValidationException("Email address " + email + " is already in use by another student.");
        }

        student.setEmail(email);
        student.setUserId(existing.getUserId()); // Preserve linked user_id immutably
        studentDAO.update(student);

        auditService.log(currentUser.getId(), "UPDATE_STUDENT", "STUDENT", student.getId(),
                existing.getFullName(), student.getFullName());
        return student;
    }

    /**
     * Soft-deactivates both student and linked user records within a single transaction (ADMIN only).
     */
    public void deleteStudent(User currentUser, int studentId) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        Student student = studentDAO.findById(studentId);
        if (student == null) {
            throw new ValidationException("Student " + studentId + " not found.");
        }
        if (!student.isActive()) {
            throw new ValidationException("Student " + student.getStudentId() + " is already inactive.");
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                studentDAO.setActive(conn, studentId, false);
                userDAO.setActive(conn, student.getUserId(), false);

                auditService.log(conn, currentUser.getId(), "DEACTIVATE_STUDENT", "STUDENT", studentId,
                        "active=1", "active=0 (Student: " + student.getStudentId() + ")");

                conn.commit();
            } catch (Throwable ex) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                } else if (ex instanceof Error) {
                    throw (Error) ex;
                } else {
                    throw new DataAccessException(ex.getMessage(), (SQLException) ex);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Reactivates both student and linked user records within a single transaction (ADMIN only).
     */
    public Student reactivateStudent(User currentUser, int studentId) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        Student student = studentDAO.findById(studentId);
        if (student == null) {
            throw new ValidationException("Student " + studentId + " not found.");
        }
        if (student.isActive()) {
            throw new ValidationException("Student " + student.getStudentId() + " is already active.");
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                studentDAO.setActive(conn, studentId, true);
                userDAO.setActive(conn, student.getUserId(), true);

                auditService.log(conn, currentUser.getId(), "REACTIVATE_STUDENT", "STUDENT", studentId,
                        "active=0", "active=1 (Student: " + student.getStudentId() + ")");

                conn.commit();
                student.setActive(true);
                return student;
            } catch (Throwable ex) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                } else if (ex instanceof Error) {
                    throw (Error) ex;
                } else {
                    throw new DataAccessException(ex.getMessage(), (SQLException) ex);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public Student getStudentById(User currentUser, int id) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY, Role.STUDENT);
        Student student = studentDAO.findById(id);
        if (student == null) {
            return null;
        }
        // Role authorization check
        if (currentUser.getRole() == Role.STUDENT && currentUser.getId() != student.getUserId()) {
            throw new AuthenticationException("Students can only view their own student profile.");
        }
        return student;
    }

    public Student getStudentByStudentId(User currentUser, String studentId) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY, Role.STUDENT);
        ValidationUtil.requireNonEmpty(studentId, "Student ID");
        Student student = studentDAO.findByStudentId(studentId.trim());
        if (student == null) {
            return null;
        }
        if (currentUser.getRole() == Role.STUDENT && currentUser.getId() != student.getUserId()) {
            throw new AuthenticationException("Students can only view their own student profile.");
        }
        return student;
    }

    public List<Student> getAllActiveStudents(User currentUser) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY);
        return studentDAO.findAllActive();
    }

    public List<Student> getStudentsByDepartment(User currentUser, int departmentId) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY);
        return studentDAO.findByDepartment(departmentId);
    }

    /**
     * Resets a student's password to a newly generated temporary password and forces
     * password change on next login (ADMIN only).
     *
     * @param currentUser authenticated administrator
     * @param studentId   primary key ID of the student record
     * @return the newly generated temporary password (for one-time display to administrator)
     */
    public String resetStudentPassword(User currentUser, int studentId) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        ValidationUtil.requirePositiveId(studentId, "Student ID");

        Student student = studentDAO.findById(studentId);
        if (student == null) {
            throw new ValidationException("Student " + studentId + " not found.");
        }
        if (!student.isActive()) {
            throw new ValidationException("Cannot reset credentials for inactive student: " + student.getStudentId());
        }

        String temporaryPassword = PasswordUtil.generateTemporaryPassword();
        String passwordHash = PasswordUtil.hashPassword(temporaryPassword);

        userDAO.updatePassword(student.getUserId(), passwordHash, true);

        auditService.log(currentUser.getId(), "RESET_STUDENT_PASSWORD", "STUDENT", studentId,
                null, "Password reset by admin for student " + student.getStudentId() + " (user ID " + student.getUserId() + "); must_change_password=1");

        return temporaryPassword;
    }

    /**
     * Updates the username of a student's linked user account (ADMIN only).
     *
     * @param currentUser authenticated administrator
     * @param studentId   primary key ID of the student record
     * @param newUsername new username (normalized and uniqueness validated)
     */
    public void updateStudentUsername(User currentUser, int studentId, String newUsername) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        ValidationUtil.requirePositiveId(studentId, "Student ID");
        ValidationUtil.requireNonEmpty(newUsername, "Username");

        String normalizedUsername = newUsername.trim();
        if (normalizedUsername.length() < 3 || normalizedUsername.length() > 50) {
            throw new ValidationException("Username must be between 3 and 50 characters.");
        }

        Student student = studentDAO.findById(studentId);
        if (student == null) {
            throw new ValidationException("Student " + studentId + " not found.");
        }

        User user = userDAO.findById(student.getUserId());
        if (user == null) {
            throw new ValidationException("Linked user account not found for student " + student.getStudentId());
        }

        if (normalizedUsername.equalsIgnoreCase(user.getUsername())) {
            return;
        }

        User existingUser = userDAO.findByUsername(normalizedUsername);
        if (existingUser != null) {
            throw new ValidationException("Username '" + normalizedUsername + "' is already in use.");
        }

        userDAO.updateUsername(student.getUserId(), normalizedUsername);

        auditService.log(currentUser.getId(), "UPDATE_STUDENT_USERNAME", "STUDENT", studentId,
                user.getUsername(), normalizedUsername);
    }

    /**
     * Forces a student to change their password on next login (ADMIN only).
     *
     * @param currentUser authenticated administrator
     * @param studentId   primary key ID of the student record
     */
    public void forcePasswordChange(User currentUser, int studentId) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        ValidationUtil.requirePositiveId(studentId, "Student ID");

        Student student = studentDAO.findById(studentId);
        if (student == null) {
            throw new ValidationException("Student " + studentId + " not found.");
        }

        userDAO.setMustChangePassword(student.getUserId(), true);

        auditService.log(currentUser.getId(), "FORCE_PASSWORD_CHANGE", "STUDENT", studentId,
                null, "Mandatory password change enforced for student " + student.getStudentId());
    }

    /**
     * Changes the authenticated student's password after verifying their current password.
     * Enforces enterprise password complexity and validates that student record exists.
     *
     * @param currentUser     authenticated student
     * @param currentPassword existing plaintext password
     * @param newPassword     new plaintext password
     */
    public void changeOwnPassword(User currentUser, String currentPassword, String newPassword) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        if (currentUser.getRole() != Role.STUDENT) {
            throw new AuthenticationException("Only student accounts can use this self-service endpoint.");
        }
        Student student = studentDAO.findByUserId(currentUser.getId());
        if (student == null) {
            throw new ValidationException("No student record found for active account.");
        }
        authService.changePassword(currentUser, currentPassword, newPassword);
    }

    /**
     * Retrieves the linked user account for a student (ADMIN only).
     *
     * @param currentUser authenticated administrator
     * @param studentId   primary key ID of the student record
     * @return User account entity or null
     */
    public User getLinkedUserForStudent(User currentUser, int studentId) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        Student student = studentDAO.findById(studentId);
        if (student == null) {
            return null;
        }
        return userDAO.findById(student.getUserId());
    }

    /**
     * Updates a student's assigned academic programme, cohort, admission year, and graduation year (ADMIN only).
     * Enforces cohort-to-programme relationship consistency.
     */
    public void assignProgramAndCohort(User currentUser, int studentId, Integer programId, Integer cohortId,
                                       String admissionYear, String expectedGraduationYear) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        Student student = studentDAO.findById(studentId);
        if (student == null) {
            throw new ValidationException("Student " + studentId + " not found.");
        }
        if (cohortId != null && cohortId > 0) {
            StudentCohort cohort = cohortDAO.findById(cohortId);
            if (cohort == null) {
                throw new ValidationException("Student cohort ID " + cohortId + " does not exist.");
            }
            if (programId != null && programId > 0 && cohort.getProgramId() != programId) {
                throw new ValidationException("Cohort '" + cohort.getCohortCode() + "' belongs to programme ID "
                        + cohort.getProgramId() + ", which does not match student's programme ID " + programId + ".");
            }
        }
        studentDAO.updateProgramAndCohort(studentId, programId, cohortId, admissionYear, expectedGraduationYear);
        auditService.log(currentUser.getId(), "ASSIGN_PROGRAM_COHORT", "STUDENT", studentId,
                null, "Assigned program: " + programId + ", cohort: " + cohortId);
    }

    private void validateStudentInput(Student student) {
        if (student == null) {
            throw new ValidationException("Student entity cannot be null.");
        }
        ValidationUtil.requireNonEmpty(student.getStudentId(), "Student ID number");
        ValidationUtil.requireNonEmpty(student.getFirstName(), "First name");
        ValidationUtil.requireNonEmpty(student.getLastName(), "Last name");
        ValidationUtil.requireNonEmpty(student.getEmail(), "Email address");
        ValidationUtil.requireNonEmpty(student.getSemester(), "Semester");
        if (student.getDepartmentId() <= 0) {
            throw new ValidationException("Department ID must be a positive integer.");
        }
        if (!isValidEmail(student.getEmail())) {
            throw new ValidationException("Invalid email format: " + student.getEmail());
        }
        if (student.getDateOfBirth() != null && !student.getDateOfBirth().trim().isEmpty()) {
            ValidationUtil.requireValidIsoDate(student.getDateOfBirth().trim(), "Date of birth");
        }
        if (student.getProgramId() != null && student.getProgramId() > 0) {
            AcademicProgram prog = programDAO.findById(student.getProgramId());
            if (prog != null && prog.getTotalSemesters() > 0) {
                try {
                    int sem = Integer.parseInt(student.getSemester().trim());
                    if (sem < 1 || sem > prog.getTotalSemesters()) {
                        throw new ValidationException("Semester " + sem + " is invalid for programme '" + prog.getCode() +
                                "' (Maximum allowed semesters: " + prog.getTotalSemesters() + ").");
                    }
                } catch (NumberFormatException nfe) {
                    throw new ValidationException("Semester must be a valid integer: " + student.getSemester());
                }
            }
        }
        if (student.getCohortId() != null && student.getCohortId() > 0) {
            StudentCohort cohort = cohortDAO.findById(student.getCohortId());
            if (cohort == null) {
                throw new ValidationException("Student cohort ID " + student.getCohortId() + " does not exist.");
            }
            if (student.getProgramId() != null && student.getProgramId() > 0) {
                if (cohort.getProgramId() != student.getProgramId()) {
                    throw new ValidationException("Cohort '" + cohort.getCohortCode() + "' belongs to programme ID "
                            + cohort.getProgramId() + ", which does not match student's programme ID " + student.getProgramId() + ".");
                }
            }
        }
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }
}
