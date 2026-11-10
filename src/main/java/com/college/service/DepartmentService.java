package com.college.service;

import com.college.dao.DepartmentDAO;
import com.college.exception.ValidationException;
import com.college.model.Department;
import com.college.model.Role;
import com.college.model.User;
import com.college.util.ValidationUtil;

import java.util.List;

/**
 * Service managing academic departments and soft-deletion reactivation rules.
 */
public class DepartmentService {

    private final DepartmentDAO departmentDAO;
    private final AuditService auditService;

    public DepartmentService() {
        this.departmentDAO = new DepartmentDAO();
        this.auditService = new AuditService();
    }

    public DepartmentService(DepartmentDAO departmentDAO, AuditService auditService) {
        this.departmentDAO = departmentDAO;
        this.auditService = auditService;
    }

    /**
     * Creates a new department or reactivates an existing inactive department with the same code.
     *
     * @param currentUser authenticated user (ADMIN only)
     * @param code        department code (e.g. 'CS', 'EC')
     * @param name        department name
     * @return persisted or reactivated Department
     */
    public Department createDepartment(User currentUser, String code, String name) {
        return createDepartment(currentUser, code, name, "School of Engineering", null, null, null, null);
    }

    public Department createDepartment(User currentUser, Department dept) {
        if (dept == null) throw new ValidationException("Department cannot be null.");
        return createDepartment(currentUser, dept.getCode(), dept.getName(), dept.getSchool(),
                dept.getDescription(), dept.getHeadOfDepartment(), dept.getContactEmail(), dept.getContactPhone());
    }

    /**
     * Creates a new department with full institutional metadata or reactivates an inactive department.
     */
    public Department createDepartment(User currentUser, String code, String name, String school,
                                       String description, String headOfDepartment,
                                       String contactEmail, String contactPhone) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        ValidationUtil.requireNonEmpty(code, "Department code");
        ValidationUtil.requireNonEmpty(name, "Department name");

        String trimmedCode = code.trim().toUpperCase();
        String trimmedName = name.trim();
        String trimmedSchool = (school != null && !school.trim().isEmpty()) ? school.trim() : "School of Engineering";

        if (contactEmail != null && !contactEmail.trim().isEmpty()) {
            if (!contactEmail.trim().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                throw new ValidationException("Invalid contact email format: " + contactEmail);
            }
        }

        Department existing = departmentDAO.findByCode(trimmedCode);
        if (existing != null) {
            if (existing.isActive()) {
                throw new ValidationException("Department code already exists: " + trimmedCode);
            }
            existing.setName(trimmedName);
            existing.setSchool(trimmedSchool);
            existing.setDescription(description);
            existing.setHeadOfDepartment(headOfDepartment);
            existing.setContactEmail(contactEmail);
            existing.setContactPhone(contactPhone);
            existing.setActive(true);
            departmentDAO.update(existing);
            departmentDAO.setActive(existing.getId(), true);

            auditService.log(currentUser.getId(), "REACTIVATE_DEPARTMENT", "DEPARTMENT", existing.getId(),
                    "active=0", "active=1 (Code: " + trimmedCode + ", Name: " + trimmedName + ", School: " + trimmedSchool + ")");
            return existing;
        }

        Department newDept = new Department(0, trimmedCode, trimmedName, trimmedSchool,
                description, headOfDepartment, contactEmail, contactPhone, true, null);
        Department saved = departmentDAO.insert(newDept);

        auditService.log(currentUser.getId(), "CREATE_DEPARTMENT", "DEPARTMENT", saved.getId(),
                null, "Created department: " + trimmedCode + " - " + trimmedName + " (" + trimmedSchool + ")");
        return saved;
    }

    /**
     * Updates an existing department's name or code.
     */
    public Department updateDepartment(User currentUser, Department dept) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        if (dept == null || dept.getId() <= 0) {
            throw new ValidationException("Valid department ID required.");
        }
        ValidationUtil.requireNonEmpty(dept.getCode(), "Department code");
        ValidationUtil.requireNonEmpty(dept.getName(), "Department name");

        Department current = departmentDAO.findById(dept.getId());
        if (current == null) {
            throw new ValidationException("Department " + dept.getId() + " not found.");
        }

        String trimmedCode = dept.getCode().trim().toUpperCase();
        Department other = departmentDAO.findByCode(trimmedCode);
        if (other != null && other.getId() != dept.getId()) {
            throw new ValidationException("Department code " + trimmedCode + " is already in use by department ID " + other.getId());
        }

        if (dept.getContactEmail() != null && !dept.getContactEmail().trim().isEmpty()) {
            if (!dept.getContactEmail().trim().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                throw new ValidationException("Invalid contact email format: " + dept.getContactEmail());
            }
        }

        dept.setCode(trimmedCode);
        dept.setName(dept.getName().trim());
        if (dept.getSchool() != null && !dept.getSchool().trim().isEmpty()) {
            dept.setSchool(dept.getSchool().trim());
        }
        departmentDAO.update(dept);

        auditService.log(currentUser.getId(), "UPDATE_DEPARTMENT", "DEPARTMENT", dept.getId(),
                current.getName(), dept.getName());
        return dept;
    }

    /**
     * Soft-deletes / deactivates a department.
     * Enforces institutional business rules: prevents deactivation if active programmes, faculty,
     * students, or courses are associated with the department.
     */
    public void deleteDepartment(User currentUser, int departmentId) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        Department dept = departmentDAO.findById(departmentId);
        if (dept == null) {
            throw new ValidationException("Department " + departmentId + " not found.");
        }
        if (!dept.isActive()) {
            throw new ValidationException("Department " + dept.getCode() + " is already inactive.");
        }

        int activeProg = departmentDAO.countAssociatedPrograms(departmentId);
        int activeFac = departmentDAO.countAssociatedFaculty(departmentId);
        int activeStu = departmentDAO.countAssociatedStudents(departmentId);
        int activeCourse = departmentDAO.countAssociatedCourses(departmentId);

        if (activeProg > 0 || activeFac > 0 || activeStu > 0 || activeCourse > 0) {
            throw new ValidationException(String.format(
                    "Cannot deactivate department '%s': active institutional records exist " +
                    "(%d programmes, %d faculty, %d students, %d courses). " +
                    "Reassign or deactivate associated records before deactivating the department.",
                    dept.getCode(), activeProg, activeFac, activeStu, activeCourse));
        }

        departmentDAO.setActive(departmentId, false);
        auditService.log(currentUser.getId(), "DEACTIVATE_DEPARTMENT", "DEPARTMENT", departmentId,
                "active=1", "active=0 (Code: " + dept.getCode() + ")");
    }

    public void deactivateDepartment(User currentUser, int departmentId) {
        deleteDepartment(currentUser, departmentId);
    }

    /**
     * Explicitly reactivates a soft-deleted department.
     */
    public Department reactivateDepartment(User currentUser, int departmentId) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        Department dept = departmentDAO.findById(departmentId);
        if (dept == null) {
            throw new ValidationException("Department " + departmentId + " not found.");
        }
        if (dept.isActive()) {
            throw new ValidationException("Department " + dept.getCode() + " is already active.");
        }
        departmentDAO.setActive(departmentId, true);
        dept.setActive(true);
        auditService.log(currentUser.getId(), "REACTIVATE_DEPARTMENT", "DEPARTMENT", departmentId,
                "active=0", "active=1 (Code: " + dept.getCode() + ")");
        return dept;
    }

    public Department getDepartmentById(User currentUser, int id) {
        if (currentUser == null) {
            throw new ValidationException("Authentication required.");
        }
        return departmentDAO.findById(id);
    }

    public Department getDepartmentByCode(User currentUser, String code) {
        if (currentUser == null) {
            throw new ValidationException("Authentication required.");
        }
        ValidationUtil.requireNonEmpty(code, "Department code");
        return departmentDAO.findByCode(code.trim().toUpperCase());
    }

    public List<Department> getAllActiveDepartments(User currentUser) {
        if (currentUser == null) {
            throw new ValidationException("Authentication required.");
        }
        return departmentDAO.findAllActive();
    }

    public List<Department> getAllDepartments(User currentUser) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        return departmentDAO.findAll();
    }

    public List<Department> getDepartmentsBySchool(User currentUser, String school) {
        if (currentUser == null) {
            throw new ValidationException("Authentication required.");
        }
        if (school == null || school.trim().isEmpty() || "ALL".equalsIgnoreCase(school.trim())) {
            return departmentDAO.findAll();
        }
        return departmentDAO.findBySchool(school.trim());
    }

    public List<String> getAllSchools(User currentUser) {
        if (currentUser == null) {
            throw new ValidationException("Authentication required.");
        }
        return departmentDAO.findAllSchools();
    }

    public Department getDepartmentDetailsWithStats(User currentUser, int departmentId) {
        if (currentUser == null) {
            throw new ValidationException("Authentication required.");
        }
        Department dept = departmentDAO.findById(departmentId);
        if (dept == null) {
            throw new ValidationException("Department not found (ID: " + departmentId + ")");
        }
        dept.setProgramCount(departmentDAO.countAssociatedPrograms(departmentId));
        dept.setFacultyCount(departmentDAO.countAssociatedFaculty(departmentId));
        dept.setStudentCount(departmentDAO.countAssociatedStudents(departmentId));
        dept.setCourseCount(departmentDAO.countAssociatedCourses(departmentId));
        return dept;
    }
}

