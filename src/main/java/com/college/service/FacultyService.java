package com.college.service;

import com.college.config.DBConnection;
import com.college.dao.DepartmentDAO;
import com.college.dao.FacultyDAO;
import com.college.dao.UserDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.DataAccessException;
import com.college.exception.ValidationException;
import com.college.model.Department;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.User;
import com.college.util.PasswordUtil;
import com.college.util.ValidationUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Service managing faculty lifecycles, transactional faculty+user creation, and profile lookups.
 */
public class FacultyService {

    private final FacultyDAO facultyDAO;
    private final UserDAO userDAO;
    private final DepartmentDAO departmentDAO;
    private final AuditService auditService;

    public FacultyService() {
        this.facultyDAO = new FacultyDAO();
        this.userDAO = new UserDAO();
        this.departmentDAO = new DepartmentDAO();
        this.auditService = new AuditService();
    }

    public FacultyService(FacultyDAO facultyDAO, UserDAO userDAO,
                          DepartmentDAO departmentDAO, AuditService auditService) {
        this.facultyDAO = facultyDAO;
        this.userDAO = userDAO;
        this.departmentDAO = departmentDAO;
        this.auditService = auditService;
    }

    /**
     * Value object encapsulating faculty creation result including credentials.
     */
    public static class FacultyCreationResult {
        private final Faculty faculty;
        private final String username;
        private final String temporaryPassword;

        public FacultyCreationResult(Faculty faculty, String username, String temporaryPassword) {
            this.faculty = faculty;
            this.username = username;
            this.temporaryPassword = temporaryPassword;
        }

        public Faculty getFaculty() {
            return faculty;
        }

        public String getUsername() {
            return username;
        }

        public String getTemporaryPassword() {
            return temporaryPassword;
        }
    }

    /**
     * Atomically creates a Faculty profile and linked User account (ADMIN only).
     *
     * @param currentUser     authenticated user (ADMIN only)
     * @param faculty         faculty entity containing profile details
     * @param initialPassword plaintext password for initial faculty login
     * @return persisted Faculty entity with generated ID and user_id populated
     */
    public Faculty createFaculty(User currentUser, Faculty faculty, String initialPassword) {
        FacultyCreationResult res = createFacultyWithCredentials(currentUser, faculty, initialPassword, faculty != null ? faculty.getFacultyId() : null);
        return res.getFaculty();
    }

    /**
     * Atomically creates a Faculty profile and linked User account with auto-generated or specified credentials (ADMIN only).
     * If initialPassword is null or blank, a secure random temporary password is automatically generated.
     * If customUsername is null or blank, a unique institutional username in AA123456789012 format is generated.
     *
     * @param currentUser     authenticated administrator
     * @param faculty         faculty entity containing profile details
     * @param initialPassword optional initial password; if null or blank, auto-generates temporary password
     * @param customUsername  optional custom username; if null or blank, auto-generates institutional AA... username
     * @return FacultyCreationResult containing persisted faculty, assigned username, and temporary password
     */
    public FacultyCreationResult createFacultyWithCredentials(User currentUser, Faculty faculty, String initialPassword, String customUsername) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        validateFacultyInput(faculty);

        String tempPassToUse = (initialPassword != null && !initialPassword.trim().isEmpty())
                ? initialPassword.trim()
                : PasswordUtil.generateTemporaryPassword();

        if (tempPassToUse.length() < 6) {
            throw new ValidationException("Initial password must be at least 6 characters long.");
        }

        String facultyIdNumber = faculty.getFacultyId().trim();
        String email = faculty.getEmail().trim().toLowerCase();

        // Two-table atomic creation within single connection transaction
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Validate department within transaction
                Department dept = departmentDAO.findById(conn, faculty.getDepartmentId());
                if (dept == null || !dept.isActive()) {
                    throw new ValidationException("Department ID " + faculty.getDepartmentId() + " does not exist or is inactive.");
                }

                // Check natural identifier conflicts within transaction
                Faculty existingById = facultyDAO.findByFacultyId(conn, facultyIdNumber);
                if (existingById != null) {
                    if (existingById.isActive()) {
                        throw new ValidationException("Faculty ID already exists: " + facultyIdNumber);
                    } else {
                        throw new ValidationException("Faculty with ID " + facultyIdNumber + " exists but is inactive. Reactivate the faculty instead.");
                    }
                }

                Faculty existingByEmail = facultyDAO.findByEmail(conn, email);
                if (existingByEmail != null) {
                    if (existingByEmail.isActive()) {
                        throw new ValidationException("Faculty email already exists: " + email);
                    } else {
                        throw new ValidationException("Faculty with email " + email + " exists but is inactive. Reactivate the faculty instead.");
                    }
                }

                String username = (customUsername != null && !customUsername.trim().isEmpty())
                        ? customUsername.trim()
                        : com.college.util.UsernameUtil.generateUniqueUsername(conn);

                User existingUser = userDAO.findByUsername(conn, username);
                if (existingUser != null) {
                    throw new ValidationException("Username " + username + " already exists in the system.");
                }

                // Step 1: Create linked user account
                String passwordHash = PasswordUtil.hashPassword(tempPassToUse);
                User user = new User(username, passwordHash, Role.FACULTY);
                user.setMustChangePassword(true);
                user.setActive(true);
                User savedUser = userDAO.insert(conn, user);

                // Step 2: Create faculty entity referencing saved user
                faculty.setUserId(savedUser.getId());
                faculty.setFacultyId(facultyIdNumber);
                faculty.setEmail(email);
                faculty.setActive(true);
                if (faculty.getJoinDate() == null || faculty.getJoinDate().trim().isEmpty()) {
                    faculty.setJoinDate(java.time.LocalDate.now().toString());
                }
                Faculty savedFaculty = facultyDAO.insert(conn, faculty);

                // Step 3: Transactional audit log
                auditService.log(conn, currentUser.getId(), "CREATE_FACULTY", "FACULTY", savedFaculty.getId(),
                        null, "Created faculty " + facultyIdNumber + " (" + savedFaculty.getFullName() + ") with username " + username + " linked to user ID " + savedUser.getId());

                conn.commit();
                return new FacultyCreationResult(savedFaculty, username, tempPassToUse);
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
                    throw new RuntimeException("Unexpected error during faculty creation", ex);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Updates an existing faculty profile (ADMIN or own faculty profile).
     */
    public Faculty updateFaculty(User currentUser, Faculty faculty) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        if (faculty == null || faculty.getId() <= 0) {
            throw new ValidationException("Valid faculty record required.");
        }

        Faculty existing = facultyDAO.findById(faculty.getId());
        if (existing == null) {
            throw new ValidationException("Faculty " + faculty.getId() + " not found.");
        }

        // Authorization: Admin or own profile
        if (currentUser.getRole() != Role.ADMIN && currentUser.getId() != existing.getUserId()) {
            throw new AuthenticationException("Unauthorized to modify this faculty profile.");
        }

        validateFacultyInput(faculty);

        // Department check
        Department dept = departmentDAO.findById(faculty.getDepartmentId());
        if (dept == null || !dept.isActive()) {
            throw new ValidationException("Department ID " + faculty.getDepartmentId() + " does not exist or is inactive.");
        }

        // Email uniqueness check
        String email = faculty.getEmail().trim().toLowerCase();
        Faculty otherEmail = facultyDAO.findByEmail(email);
        if (otherEmail != null && otherEmail.getId() != faculty.getId()) {
            throw new ValidationException("Email address " + email + " is already in use by another faculty member.");
        }

        faculty.setEmail(email);
        faculty.setUserId(existing.getUserId()); // Preserve linked user_id immutably
        facultyDAO.update(faculty);

        auditService.log(currentUser.getId(), "UPDATE_FACULTY", "FACULTY", faculty.getId(),
                existing.getFullName(), faculty.getFullName());
        return faculty;
    }

    /**
     * Soft-deactivates both faculty and linked user records within a single transaction (ADMIN only).
     */
    public void deleteFaculty(User currentUser, int facultyId) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        Faculty faculty = facultyDAO.findById(facultyId);
        if (faculty == null) {
            throw new ValidationException("Faculty " + facultyId + " not found.");
        }
        if (!faculty.isActive()) {
            throw new ValidationException("Faculty " + faculty.getFacultyId() + " is already inactive.");
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                facultyDAO.setActive(conn, facultyId, false);
                userDAO.setActive(conn, faculty.getUserId(), false);

                auditService.log(conn, currentUser.getId(), "DEACTIVATE_FACULTY", "FACULTY", facultyId,
                        "active=1", "active=0 (Faculty: " + faculty.getFacultyId() + ")");

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
     * Reactivates both faculty and linked user records within a single transaction (ADMIN only).
     */
    public Faculty reactivateFaculty(User currentUser, int facultyId) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        Faculty faculty = facultyDAO.findById(facultyId);
        if (faculty == null) {
            throw new ValidationException("Faculty " + facultyId + " not found.");
        }
        if (faculty.isActive()) {
            throw new ValidationException("Faculty " + faculty.getFacultyId() + " is already active.");
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                facultyDAO.setActive(conn, facultyId, true);
                userDAO.setActive(conn, faculty.getUserId(), true);

                auditService.log(conn, currentUser.getId(), "REACTIVATE_FACULTY", "FACULTY", facultyId,
                        "active=0", "active=1 (Faculty: " + faculty.getFacultyId() + ")");

                conn.commit();
                faculty.setActive(true);
                return faculty;
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

    public Faculty getFacultyById(User currentUser, int id) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY, Role.STUDENT);
        Faculty faculty = facultyDAO.findById(id);
        if (faculty == null) {
            return null;
        }
        if (currentUser.getRole() == Role.STUDENT) {
            throw new AuthenticationException("Students cannot view detailed faculty records.");
        }
        if (currentUser.getRole() == Role.FACULTY && currentUser.getId() != faculty.getUserId()) {
            throw new AuthenticationException("Faculty members can only view their own faculty profile.");
        }
        return faculty;
    }

    public Faculty getFacultyByFacultyId(User currentUser, String facultyId) {
        AuthService.requireRole(currentUser, Role.ADMIN, Role.FACULTY, Role.STUDENT);
        ValidationUtil.requireNonEmpty(facultyId, "Faculty ID");
        Faculty faculty = facultyDAO.findByFacultyId(facultyId.trim());
        if (faculty == null) {
            return null;
        }
        if (currentUser.getRole() == Role.STUDENT) {
            throw new AuthenticationException("Students cannot view detailed faculty records.");
        }
        if (currentUser.getRole() == Role.FACULTY && currentUser.getId() != faculty.getUserId()) {
            throw new AuthenticationException("Faculty members can only view their own faculty profile.");
        }
        return faculty;
    }

    public List<Faculty> getAllActiveFaculty(User currentUser) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        return facultyDAO.findAllActive();
    }

    public List<Faculty> getFacultyByDepartment(User currentUser, int departmentId) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        return facultyDAO.findByDepartment(departmentId);
    }

    /**
     * Retrieves the linked user account for a faculty member.
     *
     * @param currentUser authenticated administrator or the faculty member themselves
     * @param facultyId   primary key ID of the faculty record
     * @return User account or null if not linked
     */
    public User getLinkedUserForFaculty(User currentUser, int facultyId) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        ValidationUtil.requirePositiveId(facultyId, "Faculty ID");
        Faculty faculty = facultyDAO.findById(facultyId);
        if (faculty == null) {
            throw new ValidationException("Faculty " + facultyId + " not found.");
        }
        if (currentUser.getRole() != Role.ADMIN && currentUser.getId() != faculty.getUserId()) {
            throw new AuthenticationException("Unauthorized to view user account for this faculty member.");
        }
        if (faculty.getUserId() <= 0) {
            return null;
        }
        return userDAO.findById(faculty.getUserId());
    }

    /**
     * Updates a faculty member's login username with uniqueness validation and audit logging (ADMIN only).
     *
     * @param currentUser authenticated administrator
     * @param facultyId   primary key ID of the faculty record
     * @param newUsername new username (normalized and uniqueness validated)
     */
    public void updateFacultyUsername(User currentUser, int facultyId, String newUsername) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        ValidationUtil.requirePositiveId(facultyId, "Faculty ID");
        ValidationUtil.requireNonEmpty(newUsername, "Username");

        String normalizedUsername = newUsername.trim();
        if (normalizedUsername.length() < 3 || normalizedUsername.length() > 50) {
            throw new ValidationException("Username must be between 3 and 50 characters.");
        }

        Faculty faculty = facultyDAO.findById(facultyId);
        if (faculty == null) {
            throw new ValidationException("Faculty " + facultyId + " not found.");
        }

        User user = userDAO.findById(faculty.getUserId());
        if (user == null) {
            throw new ValidationException("Linked user account not found for faculty " + faculty.getFacultyId());
        }

        if (normalizedUsername.equalsIgnoreCase(user.getUsername())) {
            return;
        }

        User existingUser = userDAO.findByUsername(normalizedUsername);
        if (existingUser != null) {
            throw new ValidationException("Username '" + normalizedUsername + "' is already in use.");
        }

        userDAO.updateUsername(faculty.getUserId(), normalizedUsername);

        auditService.log(currentUser.getId(), "UPDATE_FACULTY_USERNAME", "FACULTY", facultyId,
                user.getUsername(), normalizedUsername);
    }

    private void validateFacultyInput(Faculty faculty) {
        if (faculty == null) {
            throw new ValidationException("Faculty entity cannot be null.");
        }
        ValidationUtil.requireNonEmpty(faculty.getFacultyId(), "Faculty ID number");
        ValidationUtil.requireNonEmpty(faculty.getFirstName(), "First name");
        ValidationUtil.requireNonEmpty(faculty.getLastName(), "Last name");
        ValidationUtil.requireNonEmpty(faculty.getEmail(), "Email address");
        if (faculty.getDepartmentId() <= 0) {
            throw new ValidationException("Department ID must be a positive integer.");
        }
        if (!isValidEmail(faculty.getEmail())) {
            throw new ValidationException("Invalid email format: " + faculty.getEmail());
        }
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }
}
