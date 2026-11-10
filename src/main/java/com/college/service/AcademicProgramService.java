package com.college.service;

import com.college.dao.AcademicProgramDAO;
import com.college.dao.DepartmentDAO;
import com.college.dao.ProgrammeSemesterDAO;
import com.college.dao.StudentDAO;
import com.college.exception.ValidationException;
import com.college.model.AcademicProgram;
import com.college.model.Department;
import com.college.model.ProgrammeSemester;
import com.college.model.Role;
import com.college.model.User;
import com.college.util.ValidationUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Service managing Academic Programmes, degrees, qualification levels,
 * duration-to-semester automatic calculation, and research milestone structures.
 */
public class AcademicProgramService {

    private final AcademicProgramDAO programDAO;
    private final ProgrammeSemesterDAO semesterDAO;
    private final DepartmentDAO departmentDAO;
    private final StudentDAO studentDAO;
    private final AuditService auditService;

    public AcademicProgramService() {
        this(new AcademicProgramDAO(), new ProgrammeSemesterDAO(), new DepartmentDAO(), new StudentDAO(), new AuditService());
    }

    public AcademicProgramService(AcademicProgramDAO programDAO, ProgrammeSemesterDAO semesterDAO,
                                  DepartmentDAO departmentDAO, StudentDAO studentDAO, AuditService auditService) {
        this.programDAO = programDAO;
        this.semesterDAO = semesterDAO;
        this.departmentDAO = departmentDAO;
        this.studentDAO = studentDAO;
        this.auditService = auditService;
    }

    /**
     * Calculates default semester count from duration in years.
     * 1 yr = 2 sem, 2 yrs = 4 sem, 3 yrs = 6 sem, 4 yrs = 8 sem, 5 yrs = 10 sem.
     */
    public int calculateDefaultSemesterCount(int durationYears) {
        if (durationYears <= 0) {
            throw new ValidationException("Duration in years must be at least 1.");
        }
        return durationYears * 2;
    }

    /**
     * Generates default semester definitions based on duration and research status.
     */
    public List<ProgrammeSemester> generateDefaultSemesters(int durationYears, boolean isResearch) {
        int totalSemesters = calculateDefaultSemesterCount(durationYears);
        List<ProgrammeSemester> list = new ArrayList<>();

        if (isResearch) {
            // Milestone-based doctoral/research structure
            String[] researchLabels = {
                    "Coursework & Foundations",
                    "Advanced Coursework & Comprehensive Exam",
                    "Research Proposal & Topic Defense",
                    "Progress Review I (Methodology)",
                    "Progress Review II (Experimental Findings)",
                    "Progress Review III (Publication Colloquium)",
                    "Pre-Submission Seminar",
                    "Dissertation / Thesis Submission",
                    "External Peer Review Period",
                    "Final Viva-Voce Defense & Degree Award"
            };
            String[] termTypes = {
                    "COURSEWORK", "COURSEWORK", "RESEARCH_TERM", "RESEARCH_TERM",
                    "RESEARCH_TERM", "RESEARCH_TERM", "RESEARCH_TERM", "THESIS",
                    "THESIS", "THESIS"
            };

            for (int i = 1; i <= totalSemesters; i++) {
                String label = (i <= researchLabels.length) ? researchLabels[i - 1] : "Research Term " + i;
                String type = (i <= termTypes.length) ? termTypes[i - 1] : "RESEARCH_TERM";
                int credits = "THESIS".equals(type) ? 24 : ("COURSEWORK".equals(type) ? 16 : 20);
                list.add(new ProgrammeSemester(0, i, label, type, credits));
            }
        } else {
            // Standard undergraduate / postgraduate semester structure
            for (int i = 1; i <= totalSemesters; i++) {
                list.add(new ProgrammeSemester(0, i, "Semester " + i, "SEMESTER", 20));
            }
        }
        return list;
    }

    /**
     * Creates a new Academic Programme with default semester definitions.
     */
    public AcademicProgram createProgram(User currentUser, AcademicProgram program) {
        return createProgram(currentUser, program, null);
    }

    /**
     * Creates a new Academic Programme with associated semester definitions.
     */
    public AcademicProgram createProgram(User currentUser, AcademicProgram program, List<ProgrammeSemester> customSemesters) {
        AuthService.requireRole(currentUser, Role.ADMIN);

        if (program == null) {
            throw new ValidationException("Program details cannot be null.");
        }
        ValidationUtil.requireNonEmpty(program.getName(), "Programme name");
        ValidationUtil.requireNonEmpty(program.getCode(), "Programme code");
        ValidationUtil.requireNonEmpty(program.getDegree(), "Degree / Qualification");

        String code = program.getCode().trim().toUpperCase();
        program.setCode(code);
        program.setName(program.getName().trim());
        program.setDegree(program.getDegree().trim());

        Department dept = departmentDAO.findById(program.getDepartmentId());
        if (dept == null) {
            throw new ValidationException("Associated department does not exist (ID: " + program.getDepartmentId() + ").");
        }

        if (program.getDurationYears() <= 0 || program.getDurationYears() > 10) {
            throw new ValidationException("Programme duration must be between 1 and 10 years.");
        }

        if (program.getTotalSemesters() <= 0 || program.getTotalSemesters() > 20) {
            program.setTotalSemesters(calculateDefaultSemesterCount(program.getDurationYears()));
        }

        AcademicProgram existing = programDAO.findByCode(code);
        if (existing != null) {
            throw new ValidationException("An academic programme with code '" + code + "' already exists (ID: " + existing.getId() + ").");
        }

        AcademicProgram saved = programDAO.insert(program);

        // Generate or persist semesters
        List<ProgrammeSemester> semestersToPersist = customSemesters;
        if (semestersToPersist == null || semestersToPersist.isEmpty()) {
            semestersToPersist = generateDefaultSemesters(saved.getDurationYears(), saved.isResearch());
        }

        semesterDAO.batchInsert(saved.getId(), semestersToPersist);

        auditService.log(currentUser.getId(), "CREATE_PROGRAMME", "ACADEMIC_PROGRAM", saved.getId(),
                null, "Created programme: " + saved.getCode() + " - " + saved.getName() + " (" + saved.getDegree() + ")");

        return saved;
    }

    /**
     * Updates an existing Academic Programme.
     */
    public AcademicProgram updateProgram(User currentUser, AcademicProgram program) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        if (program == null || program.getId() <= 0) {
            throw new ValidationException("Valid programme ID required.");
        }
        ValidationUtil.requireNonEmpty(program.getName(), "Programme name");
        ValidationUtil.requireNonEmpty(program.getCode(), "Programme code");
        ValidationUtil.requireNonEmpty(program.getDegree(), "Degree / Qualification");

        AcademicProgram current = programDAO.findById(program.getId());
        if (current == null) {
            throw new ValidationException("Programme not found (ID: " + program.getId() + ")");
        }

        String code = program.getCode().trim().toUpperCase();
        AcademicProgram other = programDAO.findByCode(code);
        if (other != null && other.getId() != program.getId()) {
            throw new ValidationException("Programme code '" + code + "' is already in use by another programme (ID: " + other.getId() + ").");
        }

        program.setCode(code);
        program.setName(program.getName().trim());
        programDAO.update(program);

        auditService.log(currentUser.getId(), "UPDATE_PROGRAMME", "ACADEMIC_PROGRAM", program.getId(),
                current.getCode(), program.getCode());

        return program;
    }

    /**
     * Regenerates semester structure for an Academic Programme (with optional custom semester definitions).
     */
    public List<ProgrammeSemester> regenerateSemesters(User currentUser, int programId, List<ProgrammeSemester> customSemesters) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        AcademicProgram program = programDAO.findById(programId);
        if (program == null) {
            throw new ValidationException("Programme not found (ID: " + programId + ")");
        }

        List<ProgrammeSemester> sems = customSemesters;
        if (sems == null || sems.isEmpty()) {
            sems = generateDefaultSemesters(program.getDurationYears(), program.isResearch());
        }

        semesterDAO.batchInsert(programId, sems);
        auditService.log(currentUser.getId(), "REGENERATE_PROGRAMME_SEMESTERS", "ACADEMIC_PROGRAM", programId,
                null, "Regenerated " + sems.size() + " semesters for " + program.getCode());

        return semesterDAO.findByProgramId(programId);
    }

    /**
     * Toggles the active status of an Academic Programme.
     * Prevents deactivation if active enrolled students are currently mapped to the programme.
     */
    public void toggleActive(User currentUser, int programId, boolean active) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        AcademicProgram program = programDAO.findById(programId);
        if (program == null) {
            throw new ValidationException("Programme not found (ID: " + programId + ")");
        }

        if (!active) {
            int enrolled = programDAO.countEnrolledStudents(programId);
            if (enrolled > 0) {
                throw new ValidationException(String.format(
                        "Cannot deactivate programme '%s': %d active students are enrolled in this programme. " +
                        "Reassign or graduate enrolled students before deactivating.",
                        program.getCode(), enrolled));
            }
        }

        programDAO.setActive(programId, active);
        auditService.log(currentUser.getId(), active ? "ACTIVATE_PROGRAMME" : "DEACTIVATE_PROGRAMME",
                "ACADEMIC_PROGRAM", programId, String.valueOf(program.isActive()), String.valueOf(active));
    }

    public AcademicProgram getProgramById(User currentUser, int id) {
        if (currentUser == null) throw new ValidationException("Authentication required.");
        return programDAO.findById(id);
    }

    public AcademicProgram getProgramByCode(User currentUser, String code) {
        if (currentUser == null) throw new ValidationException("Authentication required.");
        ValidationUtil.requireNonEmpty(code, "Programme code");
        return programDAO.findByCode(code.trim().toUpperCase());
    }

    public List<AcademicProgram> getAllPrograms(User currentUser) {
        if (currentUser == null) throw new ValidationException("Authentication required.");
        return programDAO.findAll();
    }

    public List<AcademicProgram> getAllActivePrograms(User currentUser) {
        if (currentUser == null) throw new ValidationException("Authentication required.");
        return programDAO.findAllActive();
    }

    public List<AcademicProgram> getProgramsByDepartment(User currentUser, int departmentId) {
        if (currentUser == null) throw new ValidationException("Authentication required.");
        return programDAO.findByDepartment(departmentId);
    }

    public List<ProgrammeSemester> getSemestersByProgram(User currentUser, int programId) {
        if (currentUser == null) throw new ValidationException("Authentication required.");
        return semesterDAO.findByProgramId(programId);
    }
}
