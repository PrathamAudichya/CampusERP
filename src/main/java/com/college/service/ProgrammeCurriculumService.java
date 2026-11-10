package com.college.service;

import com.college.dao.AcademicProgramDAO;
import com.college.dao.CourseDAO;
import com.college.dao.ProgrammeCurriculumDAO;
import com.college.exception.ValidationException;
import com.college.model.AcademicProgram;
import com.college.model.Course;
import com.college.model.ProgrammeCurriculum;
import com.college.model.Role;
import com.college.model.User;

import java.util.List;

/**
 * Service managing Programme Curriculum, course mappings across semesters,
 * credit allocations, prerequisites, and circularity checks.
 */
public class ProgrammeCurriculumService {

    private final ProgrammeCurriculumDAO curriculumDAO;
    private final AcademicProgramDAO programDAO;
    private final CourseDAO courseDAO;
    private final AuditService auditService;

    public ProgrammeCurriculumService() {
        this(new ProgrammeCurriculumDAO(), new AcademicProgramDAO(), new CourseDAO(), new AuditService());
    }

    public ProgrammeCurriculumService(ProgrammeCurriculumDAO curriculumDAO, AcademicProgramDAO programDAO,
                                      CourseDAO courseDAO, AuditService auditService) {
        this.curriculumDAO = curriculumDAO;
        this.programDAO = programDAO;
        this.courseDAO = courseDAO;
        this.auditService = auditService;
    }

    public ProgrammeCurriculum mapCourseToSemester(User currentUser, ProgrammeCurriculum curr) {
        return mapCourseToCurriculum(currentUser, curr);
    }

    /**
     * Maps a course to an Academic Programme semester curriculum.
     */
    public ProgrammeCurriculum mapCourseToCurriculum(User currentUser, ProgrammeCurriculum curr) {
        AuthService.requireRole(currentUser, Role.ADMIN);

        if (curr == null) {
            throw new ValidationException("Curriculum mapping cannot be null.");
        }

        AcademicProgram program = programDAO.findById(curr.getProgramId());
        if (program == null || !program.isActive()) {
            throw new ValidationException("Academic programme does not exist or is inactive (ID: " + curr.getProgramId() + ")");
        }

        Course course = courseDAO.findById(curr.getCourseId());
        if (course == null || !course.isActive()) {
            throw new ValidationException("Course does not exist or is inactive (ID: " + curr.getCourseId() + ")");
        }

        if (curr.getSemesterNumber() < 1 || curr.getSemesterNumber() > program.getTotalSemesters()) {
            throw new ValidationException(String.format(
                    "Invalid semester %d for programme '%s' (allowed range: 1 to %d).",
                    curr.getSemesterNumber(), program.getCode(), program.getTotalSemesters()));
        }

        String reg = (curr.getAcademicRegulation() != null && !curr.getAcademicRegulation().trim().isEmpty())
                ? curr.getAcademicRegulation().trim() : "R2026";
        curr.setAcademicRegulation(reg);

        if (curriculumDAO.existsMapping(curr.getProgramId(), curr.getSemesterNumber(), curr.getCourseId(), reg)) {
            throw new ValidationException(String.format(
                    "Course %s is already mapped to %s (Semester %d, Scheme %s).",
                    course.getCourseCode(), program.getCode(), curr.getSemesterNumber(), reg));
        }

        // Circular prerequisite check
        if (curr.getPrerequisiteCourseId() != null && curr.getPrerequisiteCourseId() > 0) {
            Course prereq = courseDAO.findById(curr.getPrerequisiteCourseId());
            if (prereq == null) {
                throw new ValidationException("Prerequisite course not found (ID: " + curr.getPrerequisiteCourseId() + ")");
            }
            if (curr.getPrerequisiteCourseId() == curr.getCourseId()) {
                throw new ValidationException("Course cannot be its own prerequisite.");
            }
            if (curriculumDAO.hasCircularPrerequisite(curr.getCourseId(), curr.getPrerequisiteCourseId())) {
                throw new ValidationException(String.format(
                        "Circular prerequisite detected: Course %s cannot require %s as a prerequisite.",
                        course.getCourseCode(), prereq.getCourseCode()));
            }
        }

        ProgrammeCurriculum saved = curriculumDAO.insert(curr);

        auditService.log(currentUser.getId(), "MAP_COURSE_TO_CURRICULUM", "PROGRAMME_CURRICULUM", saved.getId(),
                null, String.format("Mapped course %s to %s Sem %d (%s)",
                        course.getCourseCode(), program.getCode(), curr.getSemesterNumber(), curr.getCourseCategory()));

        return saved;
    }

    /**
     * Updates an existing curriculum mapping.
     */
    public ProgrammeCurriculum updateCurriculumMapping(User currentUser, ProgrammeCurriculum curr) {
        AuthService.requireRole(currentUser, Role.ADMIN);

        if (curr == null || curr.getId() <= 0) {
            throw new ValidationException("Valid curriculum mapping ID required.");
        }

        ProgrammeCurriculum existing = curriculumDAO.findById(curr.getId());
        if (existing == null) {
            throw new ValidationException("Curriculum mapping not found (ID: " + curr.getId() + ")");
        }

        AcademicProgram program = programDAO.findById(curr.getProgramId());
        if (program != null && (curr.getSemesterNumber() < 1 || curr.getSemesterNumber() > program.getTotalSemesters())) {
            throw new ValidationException(String.format(
                    "Invalid semester %d for programme '%s' (allowed range: 1 to %d).",
                    curr.getSemesterNumber(), program.getCode(), program.getTotalSemesters()));
        }

        if (curr.getPrerequisiteCourseId() != null && curr.getPrerequisiteCourseId() > 0) {
            if (curr.getPrerequisiteCourseId() == existing.getCourseId()) {
                throw new ValidationException("Course cannot be its own prerequisite.");
            }
            if (curriculumDAO.hasCircularPrerequisite(existing.getCourseId(), curr.getPrerequisiteCourseId())) {
                throw new ValidationException("Circular prerequisite dependency detected.");
            }
        }

        curriculumDAO.update(curr);

        auditService.log(currentUser.getId(), "UPDATE_CURRICULUM_MAPPING", "PROGRAMME_CURRICULUM", curr.getId(),
                existing.getCourseCategory(), curr.getCourseCategory());

        return curr;
    }

    /**
     * Removes a curriculum mapping.
     * Note: Does not touch or delete historical enrollments or marks for this course.
     */
    public void removeCurriculumMapping(User currentUser, int mappingId) {
        AuthService.requireRole(currentUser, Role.ADMIN);

        ProgrammeCurriculum existing = curriculumDAO.findById(mappingId);
        if (existing == null) {
            throw new ValidationException("Curriculum mapping not found (ID: " + mappingId + ")");
        }

        curriculumDAO.delete(mappingId);

        auditService.log(currentUser.getId(), "REMOVE_CURRICULUM_MAPPING", "PROGRAMME_CURRICULUM", mappingId,
                existing.getCourseCode(), "Removed from Sem " + existing.getSemesterNumber());
    }

    public List<ProgrammeCurriculum> getCurriculumByProgramAndSemester(User currentUser, int programId, int semesterNumber) {
        if (currentUser == null) throw new ValidationException("Authentication required.");
        return curriculumDAO.findByProgramAndSemester(programId, semesterNumber);
    }

    public List<ProgrammeCurriculum> getFullProgramCurriculum(User currentUser, int programId) {
        if (currentUser == null) throw new ValidationException("Authentication required.");
        return curriculumDAO.findByProgramId(programId);
    }

    /**
     * Exports the curriculum for a programme into RFC 4180 compliant CSV format.
     */
    public String exportCurriculumCsv(User currentUser, int programId) {
        if (currentUser == null) throw new ValidationException("Authentication required.");

        AcademicProgram program = programDAO.findById(programId);
        if (program == null) {
            throw new ValidationException("Programme not found (ID: " + programId + ")");
        }

        List<ProgrammeCurriculum> list = curriculumDAO.findByProgramId(programId);

        StringBuilder sb = new StringBuilder();
        sb.append("Semester,Course Code,Course Title,Credits,Category,Mandatory/Optional,Prerequisite,Regulation\n");

        for (ProgrammeCurriculum pc : list) {
            sb.append(pc.getSemesterNumber()).append(",");
            sb.append(escapeCsv(pc.getCourseCode())).append(",");
            sb.append(escapeCsv(pc.getCourseName())).append(",");
            sb.append(pc.getCredits()).append(",");
            sb.append(escapeCsv(pc.getCourseCategory())).append(",");
            sb.append(pc.isMandatory() ? "MANDATORY" : "ELECTIVE").append(",");
            sb.append(escapeCsv(pc.getPrerequisiteCode() != null ? pc.getPrerequisiteCode() : "None")).append(",");
            sb.append(escapeCsv(pc.getAcademicRegulation())).append("\n");
        }
        return sb.toString();
    }

    private String escapeCsv(String val) {
        if (val == null) return "\"\"";
        return "\"" + val.replace("\"", "\"\"") + "\"";
    }
}
