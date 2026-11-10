package com.college.service;

import com.college.dao.CourseAssignmentDAO;
import com.college.dao.CourseDAO;
import com.college.dao.ExaminationDAO;
import com.college.dao.FacultyDAO;
import com.college.exception.AuthenticationException;
import com.college.exception.ValidationException;
import com.college.model.Course;
import com.college.model.CourseAssignment;
import com.college.model.Examination;
import com.college.model.Faculty;
import com.college.model.Role;
import com.college.model.User;
import com.college.util.ValidationUtil;

import java.util.List;

/**
 * Service managing academic assessments, exam schedules, and assessment authorizations.
 */
public class ExaminationService {

    private final ExaminationDAO examinationDAO;
    private final CourseDAO courseDAO;
    private final CourseAssignmentDAO courseAssignmentDAO;
    private final FacultyDAO facultyDAO;
    private final AuditService auditService;

    public ExaminationService() {
        this.examinationDAO = new ExaminationDAO();
        this.courseDAO = new CourseDAO();
        this.courseAssignmentDAO = new CourseAssignmentDAO();
        this.facultyDAO = new FacultyDAO();
        this.auditService = new AuditService();
    }

    public ExaminationService(ExaminationDAO examinationDAO, CourseDAO courseDAO,
                              CourseAssignmentDAO courseAssignmentDAO, FacultyDAO facultyDAO,
                              AuditService auditService) {
        this.examinationDAO = examinationDAO;
        this.courseDAO = courseDAO;
        this.courseAssignmentDAO = courseAssignmentDAO;
        this.facultyDAO = facultyDAO;
        this.auditService = auditService;
    }

    /**
     * Schedules a new examination (ADMIN or assigned FACULTY).
     *
     * @param currentUser authenticated user
     * @param exam        examination entity
     * @return persisted Examination with generated ID
     */
    public Examination createExamination(User currentUser, Examination exam) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        validateExamInput(exam);

        Course course = courseDAO.findById(exam.getCourseId());
        if (course == null || !course.isActive()) {
            throw new ValidationException("Course ID " + exam.getCourseId() + " does not exist or is inactive.");
        }

        // Authorization check
        if (currentUser.getRole() == Role.FACULTY) {
            Faculty faculty = facultyDAO.findByUserId(currentUser.getId());
            if (faculty == null || !faculty.isActive()) {
                throw new AuthenticationException("Active faculty profile not found.");
            }
            CourseAssignment ca = courseAssignmentDAO.findActiveByCourseAndFaculty(
                    course.getId(), faculty.getId(), exam.getAcademicYear().trim());
            if (ca == null) {
                throw new AuthenticationException(String.format(
                        "Faculty %s is not assigned to course %s for academic year %s.",
                        faculty.getFacultyId(), course.getCourseCode(), exam.getAcademicYear()));
            }
        } else if (currentUser.getRole() != Role.ADMIN) {
            throw new AuthenticationException("Students cannot schedule examinations.");
        }

        exam.setActive(true);
        Examination saved = examinationDAO.insert(exam);

        auditService.log(currentUser.getId(), "CREATE_EXAMINATION", "EXAMINATION", saved.getId(),
                null, String.format("Scheduled exam '%s' (%s, Total Marks: %d) for course %s (%s)",
                        saved.getExamName(), saved.getExamType(), saved.getTotalMarks(),
                        course.getCourseCode(), saved.getAcademicYear()));
        return saved;
    }

    /**
     * Updates an existing examination definition (ADMIN or assigned FACULTY).
     */
    public Examination updateExamination(User currentUser, Examination exam) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        if (exam == null || exam.getId() <= 0) {
            throw new ValidationException("Valid examination ID required.");
        }
        validateExamInput(exam);

        Examination current = examinationDAO.findById(exam.getId());
        if (current == null) {
            throw new ValidationException("Examination " + exam.getId() + " not found.");
        }

        Course course = courseDAO.findById(exam.getCourseId());
        if (course == null || !course.isActive()) {
            throw new ValidationException("Course ID " + exam.getCourseId() + " is inactive or not found.");
        }

        if (currentUser.getRole() == Role.FACULTY) {
            Faculty faculty = facultyDAO.findByUserId(currentUser.getId());
            if (faculty == null || !faculty.isActive()) {
                throw new AuthenticationException("Active faculty profile not found.");
            }
            CourseAssignment ca = courseAssignmentDAO.findActiveByCourseAndFaculty(
                    course.getId(), faculty.getId(), exam.getAcademicYear().trim());
            if (ca == null) {
                throw new AuthenticationException("Faculty is not assigned to this course.");
            }
        } else if (currentUser.getRole() != Role.ADMIN) {
            throw new AuthenticationException("Unauthorized to modify examinations.");
        }

        examinationDAO.update(exam);

        auditService.log(currentUser.getId(), "UPDATE_EXAMINATION", "EXAMINATION", exam.getId(),
                current.getExamName(), exam.getExamName());
        return exam;
    }

    /**
     * Soft-deactivates an examination (ADMIN only).
     */
    public void deleteExamination(User currentUser, int examId) {
        AuthService.requireRole(currentUser, Role.ADMIN);
        Examination exam = examinationDAO.findById(examId);
        if (exam == null) {
            throw new ValidationException("Examination " + examId + " not found.");
        }
        if (!exam.isActive()) {
            throw new ValidationException("Examination " + examId + " is already inactive.");
        }

        examinationDAO.setActive(examId, false);
        auditService.log(currentUser.getId(), "DEACTIVATE_EXAMINATION", "EXAMINATION", examId,
                "active=1", "active=0 (Exam: " + exam.getExamName() + ")");
    }

    public Examination getExaminationById(User currentUser, int examId) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        return examinationDAO.findById(examId);
    }

    public List<Examination> getExaminationsByCourse(User currentUser, int courseId) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        return examinationDAO.findByCourse(courseId);
    }

    public List<Examination> getExaminationsByCourseAndYear(User currentUser, int courseId, String academicYear) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        ValidationUtil.requireNonEmpty(academicYear, "Academic year");
        return examinationDAO.findByCourseAndYear(courseId, academicYear.trim());
    }

    public List<Examination> getAllActiveExaminations(User currentUser) {
        if (currentUser == null) {
            throw new AuthenticationException("Authentication required.");
        }
        return examinationDAO.findAllActive();
    }

    private void validateExamInput(Examination exam) {
        if (exam == null) {
            throw new ValidationException("Examination entity cannot be null.");
        }
        ValidationUtil.requireNonEmpty(exam.getExamName(), "Exam name");
        ValidationUtil.requireNonEmpty(exam.getExamType(), "Exam type");
        ValidationUtil.requireNonEmpty(exam.getAcademicYear(), "Academic year");
        ValidationUtil.requireValidIsoDate(exam.getExamDate(), "Exam date");
        if (exam.getCourseId() <= 0) {
            throw new ValidationException("Course ID must be a positive integer.");
        }
        if (exam.getTotalMarks() <= 0) {
            throw new ValidationException("Total marks must be strictly positive.");
        }
    }
}

